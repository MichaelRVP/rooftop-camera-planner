package com.mvpitsolutions.rooftopcamera;

import com.google.inject.Provides;
import java.awt.Rectangle;
import java.awt.Shape;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
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
    private final Map<String, List<LapOptimizer.CompletedLap>> travelSamples = new HashMap<>();
    private final LapOptimizer lapOptimizer = new LapOptimizer();
    private RooftopCourse course;
    private TravelProfile bestTravelProfile;
    private ScreenMarkerLayout bestMarkerLayout;
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
            travelSamples.clear();
            bestTravelProfile = course == null ? null : TravelProfile.parse(
                configManager.getConfiguration(RooftopCameraConfig.GROUP, travelProfileKey(course)));
            bestMarkerLayout = course == null ? null : ScreenMarkerLayout.parse(
                configManager.getConfiguration(RooftopCameraConfig.GROUP, markerLayoutKey(course)));
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
    int getNextObstacleId() { return course == null ? -1 : course.nextAfter(lastClickedObstacle); }
    int getNextObstacleNumber()
    {
        int next = getNextObstacleId();
        return course == null || next < 0 ? -1 : course.indexOf(next) + 1;
    }

    String cameraGuidance()
    {
        if (bestTravelProfile == null)
        {
            return lapOptimizer.isActive()
                ? "Learning this lap - keep the camera fixed"
                : "Complete two laps at one camera position";
        }
        int yawDelta = signedYawDelta(client.getCameraYawTarget(), bestTravelProfile.yaw);
        int pitchDelta = bestTravelProfile.pitch - client.getCameraPitchTarget();
        int zoomDelta = bestTravelProfile.zoom - client.get3dZoom();
        if (Math.abs(yawDelta) <= 12 && Math.abs(pitchDelta) <= 8 && Math.abs(zoomDelta) <= 16)
        {
            return "Best measured full-lap camera";
        }
        String turn = Math.abs(yawDelta) <= 12 ? "hold yaw" : yawDelta > 0 ? "rotate right" : "rotate left";
        String tilt = Math.abs(pitchDelta) <= 8 ? "hold pitch" : pitchDelta > 0 ? "tilt up" : "tilt down";
        String zoom = Math.abs(zoomDelta) <= 16 ? "hold zoom" : zoomDelta > 0 ? "zoom in" : "zoom out";
        return turn + " | " + tilt + " | " + zoom;
    }

    String nextExperiment()
    {
        if (bestTravelProfile == null)
        {
            return "Establishing a two-lap baseline";
        }
        int phase = (lapOptimizer.getCompletedLaps() / 2) % 6;
        int round = Math.min(3, lapOptimizer.getCompletedLaps() / 12);
        int yawStep = Math.max(16, 128 >> round);
        int pitchStep = Math.max(8, 32 >> round);
        int zoomStep = Math.max(16, 64 >> round);
        switch (phase)
        {
            case 0: return "Test yaw +" + yawStep;
            case 1: return "Test yaw -" + yawStep;
            case 2: return "Test pitch +" + pitchStep;
            case 3: return "Test pitch -" + pitchStep;
            case 4: return "Test zoom +" + zoomStep;
            default: return "Test zoom -" + zoomStep;
        }
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
        int yaw = quantize(lap.yaw, 16);
        int pitch = quantize(lap.pitch, 8);
        int zoom = quantize(lap.zoom, 16);
        String key = yaw + ":" + pitch + ":" + zoom;
        List<LapOptimizer.CompletedLap> samples = travelSamples.computeIfAbsent(key, ignored -> new ArrayList<>());
        samples.add(lap);
        if (samples.size() < 2)
        {
            return;
        }
        List<Double> centerValues = new ArrayList<>();
        List<Double> gapValues = new ArrayList<>();
        List<Double> mouseValues = new ArrayList<>();
        List<Integer> overlapValues = new ArrayList<>();
        for (LapOptimizer.CompletedLap sample : samples)
        {
            centerValues.add(sample.markerTravel);
            gapValues.add(sample.markerGap);
            mouseValues.add(sample.mouseTravel);
            overlapValues.add(sample.overlappingTransitions);
        }
        double medianCenter = median(centerValues);
        double medianGap = median(gapValues);
        double medianMouse = median(mouseValues);
        Collections.sort(overlapValues);
        int medianOverlaps = overlapValues.get((overlapValues.size() - 1) / 2);
        boolean better = bestTravelProfile == null
            || medianOverlaps > bestTravelProfile.overlappingTransitions
            || (medianOverlaps == bestTravelProfile.overlappingTransitions && medianGap < bestTravelProfile.markerGap)
            || (medianOverlaps == bestTravelProfile.overlappingTransitions
                && Double.compare(medianGap, bestTravelProfile.markerGap) == 0
                && medianCenter < bestTravelProfile.markerTravel);
        if (better)
        {
            bestTravelProfile = new TravelProfile(yaw, pitch, zoom, medianCenter, medianGap,
                medianOverlaps, medianMouse, samples.size());
            bestMarkerLayout = new ScreenMarkerLayout(client.getCanvasWidth(), client.getCanvasHeight(), lap.markers);
            configManager.setConfiguration(RooftopCameraConfig.GROUP,
                travelProfileKey(course), bestTravelProfile.serialize());
            configManager.setConfiguration(RooftopCameraConfig.GROUP,
                markerLayoutKey(course), bestMarkerLayout.serialize());
        }
    }

    private static int quantize(int value, int step)
    {
        return Math.round((float) value / step) * step;
    }

    private static double median(List<Double> values)
    {
        Collections.sort(values);
        int middle = values.size() / 2;
        return values.size() % 2 == 1 ? values.get(middle) : (values.get(middle - 1) + values.get(middle)) / 2.0;
    }

    private static String travelProfileKey(RooftopCourse course)
    {
        return "travelProfile." + course.name().toLowerCase();
    }

    private static String markerLayoutKey(RooftopCourse course)
    {
        return "markerLayout." + course.name().toLowerCase();
    }

    private void reset()
    {
        tracked.clear();
        course = null;
        bestTravelProfile = null;
        bestMarkerLayout = null;
        lapOptimizer.reset(0);
        travelSamples.clear();
        currentScore = 0;
        lastClickedObstacle = -1;
        visibleObstacleCount = 0;
        ticksSinceScan = 0;
    }
}
