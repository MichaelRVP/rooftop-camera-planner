package com.mvpitsolutions.rooftopcamera;

import com.google.inject.Provides;
import java.awt.AWTEvent;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntPredicate;
import java.util.function.IntUnaryOperator;
import java.util.function.ToIntFunction;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Tile;
import net.runelite.api.TileObject;
import net.runelite.api.VarClientInt;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@PluginDescriptor(name = "Rooftop Camera Planner", description = "Learns low-movement camera layouts for rooftop Agility", tags = {"agility", "rooftop", "camera", "optimizer"})
public class RooftopCameraPlugin extends Plugin
{
    private static final Logger log = LoggerFactory.getLogger(RooftopCameraPlugin.class);
    private static final String CALIBRATION_VERSION = "camera-varc-10-v5-usable-envelope";
    @Inject private Client client;
    @Inject private OverlayManager overlayManager;
    @Inject private ConfigManager configManager;
    @Inject private RooftopCameraConfig config;
    @Inject private RooftopCameraOverlay cameraOverlay;
    @Inject private RooftopSceneOverlay sceneOverlay;

    private final Map<TileObject, Integer> tracked = new ConcurrentHashMap<>();
    private final LapOptimizer lapOptimizer = new LapOptimizer();
    private final CameraSearchPlanner searchPlanner = new CameraSearchPlanner();
    private final CameraReachabilityTracker reachabilityTracker = new CameraReachabilityTracker();
    private final AutomaticCameraShiftDetector automaticCameraShiftDetector =
        new AutomaticCameraShiftDetector();
    private final CameraSettleTracker cameraSettleTracker = new CameraSettleTracker();
    private RooftopCourse course;
    private TravelProfile bestTravelProfile;
    private ScreenMarkerLayout bestMarkerLayout;
    private SearchHistory searchHistory = new SearchHistory();
    private CameraBounds cameraBounds = new CameraBounds();
    private CameraTarget activeSearchTarget;
    private double currentScore;
    private int lastClickedObstacle = -1;
    private int visibleObstacleCount;
    private int unusableViewTicks;
    private int ticksSinceScan;
    private String calibrationNote;
    /** A roof transition changed the camera during this lap; it is valid course evidence. */
    private boolean forcedCameraShiftThisLap;
    private String lastMarkerRenderState;
    private volatile CameraGuidanceState guidanceSnapshot;
    private int calibrationTargetSamples = CameraSearchPlanner.INITIAL_VALID_LAPS;
    private List<Rectangle> adjustedScreenMarkers = Collections.emptyList();
    private final AtomicBoolean wheelInputPending = new AtomicBoolean();
    private final AtomicBoolean cameraDragPending = new AtomicBoolean();
    private final AWTEventListener cameraInputObserver = event ->
    {
        if (event instanceof MouseWheelEvent && course != null)
        {
            MouseWheelEvent wheel = (MouseWheelEvent) event;
            reachabilityTracker.zoomInput(wheel.getWheelRotation());
            wheelInputPending.set(true);
        }
        else if (event instanceof MouseEvent && event.getID() == MouseEvent.MOUSE_DRAGGED && course != null)
        {
            MouseEvent mouse = (MouseEvent) event;
            if ((mouse.getModifiersEx() & (MouseEvent.BUTTON2_DOWN_MASK | MouseEvent.BUTTON3_DOWN_MASK)) != 0)
            {
                cameraDragPending.set(true);
            }
        }
    };

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
        Toolkit.getDefaultToolkit().addAWTEventListener(cameraInputObserver,
            AWTEvent.MOUSE_WHEEL_EVENT_MASK | AWTEvent.MOUSE_MOTION_EVENT_MASK);
    }

    @Override
    protected void shutDown()
    {
        overlayManager.remove(cameraOverlay);
        overlayManager.remove(sceneOverlay);
        Toolkit.getDefaultToolkit().removeAWTEventListener(cameraInputObserver);
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
            unusableViewTicks = 0;
            lapOptimizer.reset(course == null ? 0 : course.routeSize());
            bestTravelProfile = course == null ? null : TravelProfile.parse(
                configManager.getConfiguration(RooftopCameraConfig.GROUP, travelProfileKey(course)));
            bestMarkerLayout = course == null ? null : ScreenMarkerLayout.parse(
                configManager.getConfiguration(RooftopCameraConfig.GROUP, markerLayoutKey(course)));
            searchHistory = course == null ? new SearchHistory() : SearchHistory.parse(
                configManager.getConfiguration(RooftopCameraConfig.GROUP, searchHistoryKey(course)));
            activeSearchTarget = null;
            calibrationTargetSamples = CameraSearchPlanner.INITIAL_VALID_LAPS;
            calibrationNote = null;
            if (course != null && !CALIBRATION_VERSION.equals(configManager.getConfiguration(
                RooftopCameraConfig.GROUP, calibrationVersionKey(course))))
            {
                bestTravelProfile = null;
                bestMarkerLayout = null;
                searchHistory = new SearchHistory();
                configManager.setConfiguration(RooftopCameraConfig.GROUP, travelProfileKey(course), "");
                configManager.setConfiguration(RooftopCameraConfig.GROUP, markerLayoutKey(course), "");
                configManager.setConfiguration(RooftopCameraConfig.GROUP, searchHistoryKey(course), "");
                configManager.setConfiguration(RooftopCameraConfig.GROUP,
                    calibrationVersionKey(course), CALIBRATION_VERSION);
            }
            cameraBounds = CameraBounds.parse(configManager.getConfiguration(
                RooftopCameraConfig.GROUP, cameraBoundsKey()));
            reachabilityTracker.reset();
            automaticCameraShiftDetector.reset();
            cameraSettleTracker.reset();
            adjustedScreenMarkers = Collections.emptyList();
            bootstrapLegacyProfile();
            applyBestCandidate();
            if (bestMarkerLayout != null && !bestMarkerLayout.releaseValidated)
            {
                calibrationNote = "SAVED MARKERS PAUSED - VERIFY ONE FULL LAP";
            }
            scanScene();
        }
        if (course == null)
        {
            currentScore = 0;
            visibleObstacleCount = 0;
            lastMarkerRenderState = null;
            return;
        }

        if (config.refineCamera())
        {
            calibrationTargetSamples = searchHistory.totalSamples() + 2;
            activeSearchTarget = null;
            calibrationNote = "REFINE MODE: TWO NEW LAPS";
            configManager.setConfiguration(RooftopCameraConfig.GROUP, "refineCamera", false);
        }

        logMarkerRenderState();

        if (tracked.isEmpty() && ++ticksSinceScan >= 3)
        {
            ticksSinceScan = 0;
            scanScene();
        }

        List<Rectangle> boxes = orderedVisibleClickboxes();
        visibleObstacleCount = boxes.size();
        currentScore = LayoutScorer.score(boxes, client.getViewportWidth(), client.getViewportHeight());
        rejectVisuallyUselessSearchView();
        if (cameraSettleTracker.observe(client.getCameraYawTarget(), client.getCameraPitchTarget(),
            currentCameraZoom()))
        {
            List<Rectangle> saved = bestMarkerLayout == null ? Collections.emptyList()
                : bestMarkerLayout.scaledTo(client.getCanvasWidth(), client.getCanvasHeight());
            adjustedScreenMarkers = ScreenMarkerAligner.align(saved, liveSafeMarkers(),
                client.getCanvasWidth(), client.getCanvasHeight());
            calibrationNote = null;
        }
    }

    @Subscribe
    public void onClientTick(ClientTick event)
    {
        guidanceSnapshot = computeCameraGuidanceState();
        boolean wheelAdjusted = wheelInputPending.getAndSet(false);
        boolean dragAdjusted = cameraDragPending.getAndSet(false);
        if (wheelAdjusted || dragAdjusted)
        {
            lapOptimizer.cameraAdjusted();
        }
        if (dragAdjusted)
        {
            reachabilityTracker.cameraDrag(client.getCameraYawTarget());
        }
        CameraTarget observedTarget = course == null ? null : getSearchTarget();
        if (course != null && observedTarget != null && automaticCameraShiftDetector.observe(
            client.getCameraYawTarget(), client.getCameraPitchTarget(), currentCameraZoom(),
            wheelAdjusted || dragAdjusted, lapOptimizer.isActive()))
        {
            // Rooftop transitions can move the camera without player input. Preserve
            // the experiment and score the real course path instead of making the
            // player fight the game back to a static full-lap view.
            forcedCameraShiftThisLap = true;
            lapOptimizer.cameraAdjusted();
            calibrationNote = "GAME SHIFT: LAP ACCEPTED";
        }
        boolean boundsChanged = course != null && reachabilityTracker.observe(observedTarget,
            client.getCameraYawTarget(), client.getCameraPitchTarget(), currentCameraZoom(), cameraBounds);
        boolean targetUnreachable = course != null && reachabilityTracker.consumeTargetUnreachable();
        if (boundsChanged)
        {
            persistCameraBounds();
            if (!lapOptimizer.isActive())
            {
                activeSearchTarget = null;
            }
        }
        if (targetUnreachable && observedTarget != null)
        {
            rejectCameraTarget(observedTarget);
        }
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
            client.getCameraPitchTarget(), currentCameraZoom());
    }

    @Subscribe
    public void onMenuOptionClicked(MenuOptionClicked event)
    {
        if (course != null && course.contains(event.getId()))
        {
            int obstacleIndex = course.indexOf(event.getId());
            lastClickedObstacle = event.getId();
            if (obstacleIndex == 0 && !lapOptimizer.isActive())
            {
                forcedCameraShiftThisLap = false;
            }
            if (obstacleIndex == 0 && bestMarkerLayout != null)
            {
                cameraSettleTracker.begin(client.getCameraYawTarget(), client.getCameraPitchTarget(),
                    currentCameraZoom());
                adjustedScreenMarkers = Collections.emptyList();
                calibrationNote = "CAMERA SETTLING - MARKERS PAUSED";
            }
            net.runelite.api.Point point = client.getMouseCanvasPosition();
            if (point != null)
            {
                CameraTarget target = alignmentTarget();
                LapOptimizer.CompletedLap lap = lapOptimizer.obstacleClicked(obstacleIndex,
                    point.getX(), point.getY(), client.getCameraYawTarget(),
                    client.getCameraPitchTarget(), currentCameraZoom(),
                    clickboxFor(event.getId(), point.getX(), point.getY()), calibrationCameraAccepted(
                        target, client.getCameraYawTarget(), client.getCameraPitchTarget(), currentCameraZoom(),
                        forcedCameraShiftThisLap));
                if (lap != null && config.autoLearn())
                {
                    learnFrom(lap);
                }
            }
        }
        else if (course != null)
        {
            // Ground items such as Marks of Grace are useful actions, but not route transitions.
            lapOptimizer.pauseMouseSampling();
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
    String getCalibrationNote() { return calibrationNote; }
    List<Rectangle> getScaledBestMarkers()
    {
        if (cameraSettleTracker.isPending()) return Collections.emptyList();
        if (!markersAvailable(searchPlanner.isComplete(searchHistory, calibrationTargetSamples),
            bestMarkerLayout, getCameraGuidanceState()))
        {
            return Collections.emptyList();
        }
        // Released markers are predictive destinations: they must remain fixed
        // for the whole lap so the player can pre-position the mouse before the
        // next obstacle becomes clickable. Live projections are calibration
        // evidence only and must never move the rendered route each game tick.
        return bestMarkerLayout.scaledTo(client.getCanvasWidth(), client.getCanvasHeight());
    }

    private void logMarkerRenderState()
    {
        boolean complete = searchPlanner.isComplete(searchHistory, calibrationTargetSamples);
        CameraGuidanceState guidance = getCameraGuidanceState();
        String state;
        if (!complete)
        {
            state = "blocked:calibration " + searchHistory.totalSamples() + "/" + calibrationTargetSamples;
        }
        else if (bestMarkerLayout == null)
        {
            state = "blocked:no-layout";
        }
        else if (!bestMarkerLayout.verifiedInnerRectangles)
        {
            state = "blocked:unverified-layout count=" + bestMarkerLayout.markers.size();
        }
        else if (bestTravelProfile == null || guidance == null)
        {
            state = "blocked:no-profile layout=" + bestMarkerLayout.markers.size();
        }
        else if (guidance.calibration || !guidance.isAligned())
        {
            state = "blocked:camera delta=" + guidance.yawDelta + "/" + guidance.pitchDelta + "/"
                + guidance.zoomDelta + " target=" + bestTravelProfile.yaw + "/" + bestTravelProfile.pitch
                + "/" + bestTravelProfile.zoom;
        }
        else
        {
            state = "active:layout=" + bestMarkerLayout.markers.size() + " delta=" + guidance.yawDelta + "/"
                + guidance.pitchDelta + "/" + guidance.zoomDelta;
        }
        if (!state.equals(lastMarkerRenderState))
        {
            log.info("Rooftop screen markers {}", state);
            lastMarkerRenderState = state;
        }
    }

    static List<Rectangle> mergeLiveMarkers(List<Rectangle> saved, Map<Integer, Rectangle> live)
    {
        List<Rectangle> merged = new ArrayList<>(saved);
        for (Map.Entry<Integer, Rectangle> entry : live.entrySet())
        {
            int index = entry.getKey();
            // A live projection is authoritative after rooftop camera shifts.
            // Saved coordinates are only a fallback for an obstacle that is not
            // currently projectable by the client.
            if (index >= 0 && index < merged.size() && entry.getValue() != null)
            {
                merged.set(index, new Rectangle(entry.getValue()));
            }
        }
        return merged;
    }

    static boolean markersAvailable(boolean calibrationComplete, ScreenMarkerLayout layout,
        TravelProfile profile, int yaw, int pitch, int zoom)
    {
        CameraGuidanceState guidance = profile == null ? null : new CameraGuidanceState(
            signedYawDelta(yaw, profile.yaw), profile.pitch - pitch, profile.zoom - zoom, false, 0);
        return markersAvailable(calibrationComplete, layout, guidance);
    }

    static boolean markersAvailable(boolean calibrationComplete, ScreenMarkerLayout layout,
        CameraGuidanceState guidance)
    {
        return calibrationComplete && layout != null && layout.verifiedInnerRectangles
            && layout.releaseValidated && guidance != null && !guidance.calibration && guidance.isAligned();
    }

    static boolean cameraAligned(int yaw, int pitch, int zoom, TravelProfile profile)
    {
        return profile != null
            && Math.abs(signedYawDelta(yaw, profile.yaw)) <= 8
            && Math.abs(pitch - profile.pitch) <= 4
            && Math.abs(zoom - profile.zoom) <= 8;
    }

    static boolean cameraAligned(int yaw, int pitch, int zoom, CameraTarget target)
    {
        return target != null
            && Math.abs(signedYawDelta(yaw, target.yaw)) <= 8
            && Math.abs(pitch - target.pitch) <= 4
            && Math.abs(zoom - target.zoom) <= 8;
    }
    /** A first clean lap establishes the baseline instead of requiring a target that does not exist yet. */
    static boolean calibrationCameraAccepted(CameraTarget target, int yaw, int pitch, int zoom)
    {
        return calibrationCameraAccepted(target, yaw, pitch, zoom, false);
    }

    static boolean calibrationCameraAccepted(CameraTarget target, int yaw, int pitch, int zoom,
        boolean forcedCourseShift)
    {
        return forcedCourseShift || target == null || cameraAligned(yaw, pitch, zoom, target);
    }

    int getTestedCameraCount() { return searchHistory.testedCount(); }
    int getValidCalibrationLaps() { return Math.min(searchHistory.totalSamples(), calibrationTargetSamples); }
    CameraTarget getSearchTarget()
    {
        if (course == null) return null;
        // The game can still settle or reposition the camera while a course loads.
        // Do not freeze a target from that transient frame; the first click locks
        // the player's actual usable starting view as the baseline experiment.
        if (shouldFollowLiveCameraForBaseline(searchHistory.totalSamples(), lapOptimizer.isActive()))
        {
            activeSearchTarget = currentCameraTarget();
            return activeSearchTarget;
        }
        if (requiresLiveMarkerVerification())
        {
            return bestTravelProfile == null ? null
                : new CameraTarget(bestTravelProfile.yaw, bestTravelProfile.pitch, bestTravelProfile.zoom);
        }
        if (activeSearchTarget == null)
        {
            activeSearchTarget = searchPlanner.nextTarget(
                searchHistory, searchHistory.bestOperational(), currentCameraTarget(), cameraBounds,
                calibrationTargetSamples);
        }
        return activeSearchTarget;
    }

    static boolean shouldFollowLiveCameraForBaseline(int completedSamples, boolean lapActive)
    {
        return completedSamples == 0 && !lapActive;
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
            if (bestTravelProfile == null) return "Complete a lap to begin calibration";
            CameraTarget best = new CameraTarget(bestTravelProfile.yaw, bestTravelProfile.pitch, bestTravelProfile.zoom);
            return cameraAligned(client.getCameraYawTarget(), client.getCameraPitchTarget(), currentCameraZoom(),
                bestTravelProfile) ? "Optimized camera locked - markers active" : directionsTo(best);
        }
        if (alignedTo(target))
        {
            return "Calibration lap " + (getValidCalibrationLaps() + 1) + " / "
                + calibrationTargetSamples + " - hold camera";
        }
        return directionsTo(target);
    }

    CameraGuidanceState getCameraGuidanceState()
    {
        CameraGuidanceState snapshot = guidanceSnapshot;
        return snapshot == null ? computeCameraGuidanceState() : snapshot;
    }

    private CameraGuidanceState computeCameraGuidanceState()
    {
        CameraTarget target = getSearchTarget();
        boolean calibration = target != null;
        if (target == null && bestTravelProfile != null)
        {
            target = new CameraTarget(bestTravelProfile.yaw, bestTravelProfile.pitch, bestTravelProfile.zoom);
        }
        if (target == null) return null;
        return new CameraGuidanceState(
            signedYawDelta(client.getCameraYawTarget(), target.yaw),
            target.pitch - client.getCameraPitchTarget(),
            target.zoom - currentCameraZoom(), calibration, getValidCalibrationLaps(), calibrationTargetSamples);
    }

    private boolean alignedTo(CameraTarget target)
    {
        if (target == null)
        {
            return false;
        }
        int yawDelta = signedYawDelta(client.getCameraYawTarget(), target.yaw);
        int pitchDelta = target.pitch - client.getCameraPitchTarget();
        int zoomDelta = target.zoom - currentCameraZoom();
        return Math.abs(yawDelta) <= 8 && Math.abs(pitchDelta) <= 4 && Math.abs(zoomDelta) <= 8;
    }

    private CameraTarget alignmentTarget()
    {
        return effectiveAlignmentTarget(getSearchTarget(), bestTravelProfile);
    }

    static CameraTarget effectiveAlignmentTarget(CameraTarget searchTarget, TravelProfile bestProfile)
    {
        if (searchTarget != null || bestProfile == null)
        {
            return searchTarget;
        }
        return new CameraTarget(bestProfile.yaw, bestProfile.pitch, bestProfile.zoom);
    }

    private String directionsTo(CameraTarget target)
    {
        int yawDelta = signedYawDelta(client.getCameraYawTarget(), target.yaw);
        int pitchDelta = target.pitch - client.getCameraPitchTarget();
        int zoomDelta = target.zoom - currentCameraZoom();
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

    private Map<Integer, Rectangle> liveSafeMarkers()
    {
        Map<Integer, Rectangle> live = new ConcurrentHashMap<>();
        for (Map.Entry<TileObject, Integer> entry : tracked.entrySet())
        {
            Shape clickbox = entry.getKey().getClickbox();
            if (clickbox == null || clickbox.getBounds().isEmpty())
            {
                continue;
            }
            Rectangle safe = ClickboxNormalizer.largestSafeRectangle(clickbox,
                client.getCanvasWidth(), client.getCanvasHeight());
            if (safe != null)
            {
                live.merge(entry.getValue(), safe,
                    (left, right) -> left.width * left.height >= right.width * right.height ? left : right);
            }
        }
        return live;
    }

    private Rectangle clickboxFor(int objectId, int mouseX, int mouseY)
    {
        List<Rectangle> candidates = new ArrayList<>();
        for (TileObject object : tracked.keySet())
        {
            if (object.getId() != objectId) continue;
            Shape shape = object.getClickbox();
            if (shape == null || shape.getBounds().isEmpty()) continue;
            Rectangle safe = ClickboxNormalizer.largestSafeRectangle(shape,
                client.getCanvasWidth(), client.getCanvasHeight());
            if (safe != null)
            {
                candidates.add(safe);
            }
        }
        Rectangle nearest = ClickboxNormalizer.nearest(new Rectangle(mouseX, mouseY, 1, 1), candidates);
        return nearest != null ? nearest : cursorFallbackMarker(
            mouseX, mouseY, client.getCanvasWidth(), client.getCanvasHeight());
    }

    static Rectangle cursorFallbackMarker(int mouseX, int mouseY, int canvasWidth, int canvasHeight)
    {
        if (canvasWidth <= 0 || canvasHeight <= 0
            || mouseX < 0 || mouseY < 0 || mouseX >= canvasWidth || mouseY >= canvasHeight)
        {
            return null;
        }

        int width = Math.min(10, canvasWidth);
        int height = Math.min(10, canvasHeight);
        int x = Math.max(0, Math.min(mouseX - width / 2, canvasWidth - width));
        int y = Math.max(0, Math.min(mouseY - height / 2, canvasHeight - height));
        return new Rectangle(x, y, width, height);
    }

    private void learnFrom(LapOptimizer.CompletedLap lap)
    {
        boolean searchComplete = searchPlanner.isComplete(searchHistory, calibrationTargetSamples);
        CameraTarget requested = searchComplete ? alignmentTarget() : getSearchTarget();
        if (!lap.stableCamera || Double.isNaN(lap.markerTravel)
            || !calibrationCameraAccepted(requested, lap.yaw, lap.pitch, lap.zoom, forcedCameraShiftThisLap))
        {
            calibrationNote = !lap.stableCamera ? "LAST LAP SKIPPED: CAMERA SHIFTED"
                : Double.isNaN(lap.markerTravel) ? "LAST LAP SKIPPED: MARKER DATA"
                : "LAST LAP SKIPPED: CAMERA MISALIGNED";
            log.info("Calibration lap skipped for {}: stable={}, markerTravel={}, requested={}, actual={}/{}/{}",
                course, lap.stableCamera, lap.markerTravel,
                requested == null ? "none" : requested.key(), lap.yaw, lap.pitch, lap.zoom);
            if (!searchComplete && requested != null && !lap.stableCamera)
            {
                searchHistory.getOrCreate(requested.yaw, requested.pitch, requested.zoom).reject();
                configManager.setConfiguration(RooftopCameraConfig.GROUP, searchHistoryKey(course),
                    searchHistory.serialize());
                activeSearchTarget = null;
            }
            return;
        }
        calibrationNote = null;
        int yaw = CameraSearchPlanner.normalizeYaw(quantize(lap.yaw, 16));
        int pitch = quantize(lap.pitch, 8);
        int zoom = quantize(lap.zoom, 16);
        ScreenMarkerLayout layout = new ScreenMarkerLayout(client.getCanvasWidth(), client.getCanvasHeight(), lap.markers);
        if (searchComplete)
        {
            if (requiresLiveMarkerVerification())
            {
                searchHistory.getOrCreate(yaw, pitch, zoom).add(lap, layout);
                configManager.setConfiguration(RooftopCameraConfig.GROUP, searchHistoryKey(course),
                    searchHistory.serialize());
                applyBestCandidate();
                calibrationNote = "LIVE MARKERS VERIFIED";
            }
            return;
        }
        searchHistory.getOrCreate(yaw, pitch, zoom).add(lap, layout);
        configManager.setConfiguration(RooftopCameraConfig.GROUP, searchHistoryKey(course), searchHistory.serialize());
        applyBestCandidate();
        activeSearchTarget = null;
        forcedCameraShiftThisLap = false;
    }

    private static int quantize(int value, int step)
    {
        return Math.round((float) value / step) * step;
    }

    private void rejectCameraTarget(CameraTarget target)
    {
        searchHistory.getOrCreate(target.yaw, target.pitch, target.zoom).rejectAsUnreachable();
        configManager.setConfiguration(RooftopCameraConfig.GROUP, searchHistoryKey(course),
            searchHistory.serialize());
        lapOptimizer.cameraTargetRejected();
        activeSearchTarget = null;
        reachabilityTracker.reset();
        automaticCameraShiftDetector.reset();
    }

    /** Reject a calibration view before a lap if it has hidden the route entirely. */
    private void rejectVisuallyUselessSearchView()
    {
        CameraTarget target = getSearchTarget();
        if (target == null || !shouldRejectVisuallyUselessView(
            lapOptimizer.isActive(), alignedTo(target), tracked.size(), visibleObstacleCount))
        {
            unusableViewTicks = 0;
            return;
        }
        if (++unusableViewTicks >= 3)
        {
            rejectCameraTarget(target);
            calibrationNote = "VIEW REJECTED: COURSE HIDDEN";
            unusableViewTicks = 0;
        }
    }

    static boolean shouldRejectVisuallyUselessView(boolean lapActive, boolean targetAligned,
        int trackedObstacles, int visibleObstacles)
    {
        return !lapActive && targetAligned && trackedObstacles >= 3 && visibleObstacles == 0;
    }

    private CameraTarget currentCameraTarget()
    {
        return canonicalCameraTarget(client.getCameraYawTarget(), client.getCameraPitchTarget(), currentCameraZoom());
    }

    static CameraTarget canonicalCameraTarget(int yaw, int pitch, int zoom)
    {
        return new CameraTarget(CameraSearchPlanner.normalizeYaw(quantize(yaw, 16)),
            quantize(pitch, 8), quantize(zoom, 16));
    }

    private int currentCameraZoom()
    {
        return client.getVarcIntValue(VarClientInt.CAMERA_ZOOM_RESIZABLE_VIEWPORT);
    }

    private void bootstrapLegacyProfile()
    {
        if (bestTravelProfile == null || searchHistory.best() != null) return;
        CameraCandidateStats candidate = searchHistory.getOrCreate(bestTravelProfile.yaw,
            bestTravelProfile.pitch, bestTravelProfile.zoom);
        candidate.samples = Math.max(2, bestTravelProfile.samples);
        candidate.overlapTotal = bestTravelProfile.overlappingTransitions * candidate.samples;
        candidate.overlapAreaTotal = bestTravelProfile.overlapArea * candidate.samples;
        candidate.gapTotal = bestTravelProfile.markerGap * candidate.samples;
        candidate.centerTotal = bestTravelProfile.markerTravel * candidate.samples;
        candidate.mouseTotal = bestTravelProfile.observedMouseTravel * candidate.samples;
        candidate.representativeLayout = bestMarkerLayout;
        configManager.setConfiguration(RooftopCameraConfig.GROUP, searchHistoryKey(course), searchHistory.serialize());
    }

    private void applyBestCandidate()
    {
        CameraCandidateStats best = searchHistory.bestOperational();
        if (best == null) return;
        bestTravelProfile = new TravelProfile(best.yaw, best.pitch, best.zoom, best.averageCenter(),
            best.averageGap(), best.averageOverlap(), best.averageOverlapArea(), best.averageMouse(), best.samples);
        bestMarkerLayout = best.representativeLayout;
        configManager.setConfiguration(RooftopCameraConfig.GROUP, travelProfileKey(course), bestTravelProfile.serialize());
        if (bestMarkerLayout != null)
        {
            configManager.setConfiguration(RooftopCameraConfig.GROUP, markerLayoutKey(course), bestMarkerLayout.serialize());
        }
    }

    private boolean requiresLiveMarkerVerification()
    {
        return bestTravelProfile != null && (bestMarkerLayout == null || !bestMarkerLayout.releaseValidated);
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

    private static String calibrationVersionKey(RooftopCourse course)
    {
        return "calibrationVersion." + course.name().toLowerCase();
    }

    private static String cameraBoundsKey()
    {
        return "cameraBounds";
    }

    private void persistCameraBounds()
    {
        configManager.setConfiguration(RooftopCameraConfig.GROUP, cameraBoundsKey(), cameraBounds.serialize());
    }

    private void reset()
    {
        tracked.clear();
        course = null;
        bestTravelProfile = null;
        bestMarkerLayout = null;
        lapOptimizer.reset(0);
        searchHistory = new SearchHistory();
        activeSearchTarget = null;
        cameraBounds = new CameraBounds();
        cameraSettleTracker.reset();
        adjustedScreenMarkers = Collections.emptyList();
        currentScore = 0;
        lastClickedObstacle = -1;
        visibleObstacleCount = 0;
        ticksSinceScan = 0;
        calibrationNote = null;
        wheelInputPending.set(false);
        cameraDragPending.set(false);
        reachabilityTracker.reset();
        automaticCameraShiftDetector.reset();
    }
}
