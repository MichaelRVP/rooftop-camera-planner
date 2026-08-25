package com.mvpitsolutions.rooftopcamera;

import com.google.inject.Provides;
import java.awt.Rectangle;
import java.awt.Shape;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntPredicate;
import java.util.function.IntUnaryOperator;
import java.util.function.ToIntFunction;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Tile;
import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.DecorativeObjectDespawned;
import net.runelite.api.events.DecorativeObjectSpawned;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.GroundObjectDespawned;
import net.runelite.api.events.GroundObjectSpawned;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.WallObjectDespawned;
import net.runelite.api.events.WallObjectSpawned;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

@PluginDescriptor(name = "Rooftop Camera Planner", description = "Learns low-movement camera layouts for rooftop Agility", tags = {"agility", "rooftop", "camera", "optimizer"})
public class RooftopCameraPlugin extends Plugin
{
    @Inject private Client client;
    @Inject private OverlayManager overlayManager;
    @Inject private ConfigManager configManager;
    @Inject private RooftopCameraConfig config;
    @Inject private RooftopCameraOverlay cameraOverlay;
    @Inject private RooftopSceneOverlay sceneOverlay;
    @Inject private RooftopGhostOverlay ghostOverlay;

    private final Map<TileObject, Integer> tracked = new ConcurrentHashMap<>();
    private final LapOptimizer lapOptimizer = new LapOptimizer();
    private final CameraSearchPlanner searchPlanner = new CameraSearchPlanner();
    private RooftopCourse course;
    private TravelProfile bestTravelProfile;
    private ScreenMarkerLayout bestMarkerLayout;
    private SearchHistory searchHistory = new SearchHistory();
    private double currentScore;
    private int lastClickedObstacle = -1;
    private int visibleObstacleCount;
    private int ticksSinceScan;

    @Provides
    RooftopCameraConfig provideConfig(ConfigManager manager)
    {
        return manager.getConfig(RooftopCameraConfig.class);
    }

    @Override
    protected void startUp()
    {
        overlayManager.add(cameraOverlay);
        overlayManager.add(sceneOverlay);
        overlayManager.add(ghostOverlay);
    }

    @Override
    protected void shutDown()
    {
        overlayManager.remove(cameraOverlay);
        overlayManager.remove(sceneOverlay);
        overlayManager.remove(ghostOverlay);
        reset();
    }

    @Subscribe
    public void onGameTick(GameTick event)
    {
        RooftopCourse detected = detectCourse();
        if (detected != course)
        {
            course = detected;
            tracked.clear();
            lastClickedObstacle = -1;
            lapOptimizer.reset(course == null ? 0 : course.obstacles.length);
            bestTravelProfile = course == null ? null : TravelProfile.parse(
                configManager.getConfiguration(RooftopCameraConfig.GROUP, travelProfileKey(course)));
            bestMarkerLayout = course == null ? null : ScreenMarkerLayout.parse(
                configManager.getConfiguration(RooftopCameraConfig.GROUP, markerLayoutKey(course)));
            searchHistory = course == null ? new SearchHistory() : SearchHistory.parse(
                configManager.getConfiguration(RooftopCameraConfig.GROUP, searchHistoryKey(course)));
            bootstrapLegacyProfile();
            applyBestCandidate();
            scanScene();
        }
        if (course == null)
        {
            currentScore = 0;
            visibleObstacleCount = 0;
            return;
        }

        if (tracked.isEmpty() && ++ticksSinceScan >= 3)
        {
            ticksSinceScan = 0;
            scanScene();
        }

        List<Rectangle> boxes = orderedVisibleClickboxes();
        visibleObstacleCount = boxes.size();
        currentScore = LayoutScorer.score(boxes, client.getViewportWidth(), client.getViewportHeight());
    }

    @Subscribe
    public void onClientTick(ClientTick event)
    {
        if (course == null || !lapOptimizer.isActive())
        {
            return;
        }
        net.runelite.api.Point point = client.getMouseCanvasPosition();
        if (point == null || point.getX() < 0 || point.getY() < 0
            || point.getX() >= client.getCanvasWidth() || point.getY() >= client.getCanvasHeight())
        {
            lapOptimizer.pauseMouseSampling();
            return;
        }
        lapOptimizer.sampleMouse(point.getX(), point.getY(), client.getCameraYawTarget(),
            client.getCameraPitchTarget(), client.get3dZoom());
    }

    @Subscribe
    public void onMenuOptionClicked(MenuOptionClicked event)
    {
        if (course != null && course.contains(event.getId()))
        {
            lastClickedObstacle = event.getId();
            net.runelite.api.Point point = client.getMouseCanvasPosition();
            if (point != null)
            {
                LapOptimizer.CompletedLap lap = lapOptimizer.obstacleClicked(course.indexOf(event.getId()),
                    point.getX(), point.getY(), client.getCameraYawTarget(),
                    client.getCameraPitchTarget(), client.get3dZoom(),
                    clickboxFor(event.getId(), point.getX(), point.getY()));
                if (lap != null && config.autoLearn())
                {
                    learnFrom(lap);
                }
            }
        }
    }

    @Subscribe public void onGameObjectSpawned(GameObjectSpawned event) { onTileObject(null, event.getGameObject()); }
    @Subscribe public void onGameObjectDespawned(GameObjectDespawned event) { onTileObject(event.getGameObject(), null); }
    @Subscribe public void onWallObjectSpawned(WallObjectSpawned event) { onTileObject(null, event.getWallObject()); }
    @Subscribe public void onWallObjectDespawned(WallObjectDespawned event) { onTileObject(event.getWallObject(), null); }
    @Subscribe public void onDecorativeObjectSpawned(DecorativeObjectSpawned event) { onTileObject(null, event.getDecorativeObject()); }
    @Subscribe public void onDecorativeObjectDespawned(DecorativeObjectDespawned event) { onTileObject(event.getDecorativeObject(), null); }
    @Subscribe public void onGroundObjectSpawned(GroundObjectSpawned event) { onTileObject(null, event.getGroundObject()); }
    @Subscribe public void onGroundObjectDespawned(GroundObjectDespawned event) { onTileObject(event.getGroundObject(), null); }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        if (event.getGameState() == GameState.LOADING || event.getGameState() == GameState.LOGIN_SCREEN)
        {
            tracked.clear();
        }
    }

    RooftopCourse getCourse() { return course; }
    TravelProfile getBestTravelProfile() { return bestTravelProfile; }
    LapOptimizer getLapOptimizer() { return lapOptimizer; }
    double getCurrentScore() { return currentScore; }
    Map<TileObject, Integer> getTracked() { return tracked; }
    int getVisibleObstacleCount() { return visibleObstacleCount; }
    int getTrackedObstacleCount() { return tracked.size(); }
    List<Rectangle> getScaledBestMarkers()
    {
        return bestMarkerLayout == null ? Collections.emptyList()
            : bestMarkerLayout.scaledTo(client.getCanvasWidth(), client.getCanvasHeight());
    }
    int getTestedCameraCount() { return searchHistory.testedCount(); }
    CameraTarget getSearchTarget()
    {
        if (course == null) return null;
        return searchPlanner.nextTarget(searchHistory, searchHistory.best(), currentCameraTarget());
    }
    int getSearchTargetSamples()
    {
        CameraTarget target = getSearchTarget();
        CameraCandidateStats candidate = target == null ? null : searchHistory.get(target);
        return candidate == null ? 0 : candidate.samples;
    }
    int getNextObstacleId() { return course == null ? -1 : course.nextAfter(lastClickedObstacle); }
    int getNextObstacleNumber()
    {
        int next = getNextObstacleId();
        return course == null || next < 0 ? -1 : course.indexOf(next) + 1;
    }

    String cameraGuidance()
    {
        CameraTarget target = getSearchTarget();
        if (target == null)
        {
            return "Local camera neighborhood verified";
        }
        int yawDelta = signedYawDelta(client.getCameraYawTarget(), target.yaw);
        int pitchDelta = target.pitch - client.getCameraPitchTarget();
        int zoomDelta = target.zoom - client.get3dZoom();
        if (Math.abs(yawDelta) <= 8 && Math.abs(pitchDelta) <= 4 && Math.abs(zoomDelta) <= 8)
        {
            return "Target locked - hold camera for the full lap";
        }
        String turn = Math.abs(yawDelta) <= 8 ? "hold yaw" : yawDelta > 0 ? "rotate right" : "rotate left";
        String tilt = Math.abs(pitchDelta) <= 4 ? "hold pitch" : pitchDelta > 0 ? "tilt up" : "tilt down";
        String zoom = Math.abs(zoomDelta) <= 8 ? "hold zoom" : zoomDelta > 0 ? "zoom in" : "zoom out";
        return turn + " | " + tilt + " | " + zoom;
    }

    String nextExperiment()
    {
        CameraTarget target = getSearchTarget();
        return target == null ? "Search complete"
            : "Y " + target.yaw + "  P " + target.pitch + "  Z " + target.zoom;
    }

    static int signedYawDelta(int current, int target)
    {
        return ((target - current + 1024) & 2047) - 1024;
    }

    private RooftopCourse detectCourse()
    {
        if (client.getLocalPlayer() == null)
        {
            return null;
        }
        WorldPoint point = WorldPoint.fromLocalInstance(client, client.getLocalPlayer().getLocalLocation());
        return RooftopCourse.forRegion(point.getRegionID());
    }

    private void onTileObject(TileObject oldObject, TileObject newObject)
    {
        updateTracked(tracked, oldObject, newObject, TileObject::getId,
            course == null ? id -> false : course::contains,
            course == null ? id -> -1 : course::indexOf);
    }

    static <T> void updateTracked(Map<T, Integer> objects, T oldObject, T newObject,
        ToIntFunction<T> idReader, IntPredicate acceptedId, IntUnaryOperator routeIndex)
    {
        if (oldObject != null)
        {
            objects.remove(oldObject);
        }
        if (newObject != null)
        {
            int id = idReader.applyAsInt(newObject);
            if (acceptedId.test(id))
            {
                objects.put(newObject, routeIndex.applyAsInt(id));
            }
        }
    }

    private void scanScene()
    {
        if (course == null || client.getTopLevelWorldView() == null)
        {
            return;
        }
        Tile[][][] tiles = client.getTopLevelWorldView().getScene().getTiles();
        for (Tile[][] plane : tiles)
        {
            for (Tile[] column : plane)
            {
                for (Tile tile : column)
                {
                    if (tile == null)
                    {
                        continue;
                    }
                    onTileObject(null, tile.getWallObject());
                    onTileObject(null, tile.getDecorativeObject());
                    onTileObject(null, tile.getGroundObject());
                    for (TileObject object : tile.getGameObjects())
                    {
                        onTileObject(null, object);
                    }
                }
            }
        }
    }

    private List<Rectangle> orderedVisibleClickboxes()
    {
        List<Map.Entry<TileObject, Integer>> entries = new ArrayList<>(tracked.entrySet());
        entries.sort(Comparator.comparingInt(Map.Entry::getValue));
        List<Rectangle> boxes = new ArrayList<>();
        for (Map.Entry<TileObject, Integer> entry : entries)
        {
            Shape clickbox = entry.getKey().getClickbox();
            if (clickbox != null && !clickbox.getBounds().isEmpty())
            {
                boxes.add(clickbox.getBounds());
            }
        }
        return boxes;
    }

    private Rectangle clickboxFor(int objectId, int mouseX, int mouseY)
    {
        Rectangle nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (TileObject object : tracked.keySet())
        {
            if (object.getId() != objectId) continue;
            Shape shape = object.getClickbox();
            if (shape == null || shape.getBounds().isEmpty()) continue;
            Rectangle bounds = shape.getBounds();
            if (shape.contains(mouseX, mouseY)) return new Rectangle(bounds);
            double distance = Math.hypot(bounds.getCenterX() - mouseX, bounds.getCenterY() - mouseY);
            if (distance < nearestDistance)
            {
                nearestDistance = distance;
                nearest = new Rectangle(bounds);
            }
        }
        return nearest;
    }

    private void learnFrom(LapOptimizer.CompletedLap lap)
    {
        if (!lap.stableCamera || Double.isNaN(lap.markerTravel))
        {
            return;
        }
        int yaw = CameraSearchPlanner.normalizeYaw(quantize(lap.yaw, 16));
        int pitch = quantize(lap.pitch, 8);
        int zoom = quantize(lap.zoom, 16);
        ScreenMarkerLayout layout = new ScreenMarkerLayout(client.getCanvasWidth(), client.getCanvasHeight(), lap.markers);
        searchHistory.getOrCreate(yaw, pitch, zoom).add(lap, layout);
        configManager.setConfiguration(RooftopCameraConfig.GROUP, searchHistoryKey(course), searchHistory.serialize());
        applyBestCandidate();
    }

    private static int quantize(int value, int step)
    {
        return Math.round((float) value / step) * step;
    }

    private CameraTarget currentCameraTarget()
    {
        return new CameraTarget(CameraSearchPlanner.normalizeYaw(quantize(client.getCameraYawTarget(), 16)),
            quantize(client.getCameraPitchTarget(), 8), quantize(client.get3dZoom(), 16));
    }

    private void bootstrapLegacyProfile()
    {
        if (bestTravelProfile == null || searchHistory.best() != null) return;
        CameraCandidateStats candidate = searchHistory.getOrCreate(bestTravelProfile.yaw,
            bestTravelProfile.pitch, bestTravelProfile.zoom);
        candidate.samples = Math.max(2, bestTravelProfile.samples);
        candidate.overlapTotal = bestTravelProfile.overlappingTransitions * candidate.samples;
        candidate.gapTotal = bestTravelProfile.markerGap * candidate.samples;
        candidate.centerTotal = bestTravelProfile.markerTravel * candidate.samples;
        candidate.mouseTotal = bestTravelProfile.observedMouseTravel * candidate.samples;
        candidate.representativeLayout = bestMarkerLayout;
        configManager.setConfiguration(RooftopCameraConfig.GROUP, searchHistoryKey(course), searchHistory.serialize());
    }

    private void applyBestCandidate()
    {
        CameraCandidateStats best = searchHistory.best();
        if (best == null) return;
        bestTravelProfile = new TravelProfile(best.yaw, best.pitch, best.zoom, best.averageCenter(),
            best.averageGap(), best.averageOverlap(), best.averageMouse(), best.samples);
        bestMarkerLayout = best.representativeLayout;
        configManager.setConfiguration(RooftopCameraConfig.GROUP, travelProfileKey(course), bestTravelProfile.serialize());
        if (bestMarkerLayout != null)
        {
            configManager.setConfiguration(RooftopCameraConfig.GROUP, markerLayoutKey(course), bestMarkerLayout.serialize());
        }
    }

    private static String travelProfileKey(RooftopCourse course)
    {
        return "travelProfile." + course.name().toLowerCase();
    }

    private static String markerLayoutKey(RooftopCourse course)
    {
        return "markerLayout." + course.name().toLowerCase();
    }

    private static String searchHistoryKey(RooftopCourse course)
    {
        return "searchHistory." + course.name().toLowerCase();
    }

    private void reset()
    {
        tracked.clear();
        course = null;
        bestTravelProfile = null;
        bestMarkerLayout = null;
        lapOptimizer.reset(0);
        searchHistory = new SearchHistory();
        currentScore = 0;
        lastClickedObstacle = -1;
        visibleObstacleCount = 0;
        ticksSinceScan = 0;
    }
}
