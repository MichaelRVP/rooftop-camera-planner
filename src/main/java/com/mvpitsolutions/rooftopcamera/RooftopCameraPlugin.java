package com.mvpitsolutions.rooftopcamera;

import com.google.inject.Provides;
import java.awt.Rectangle;
import java.awt.Shape;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Tile;
import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.DecorativeObjectDespawned;
import net.runelite.api.events.DecorativeObjectSpawned;
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

    private final Map<TileObject, Integer> tracked = new ConcurrentHashMap<>();
    private RooftopCourse course;
    private CameraProfile bestProfile;
    private double currentScore;
    private int lastClickedObstacle = -1;

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
    }

    @Override
    protected void shutDown()
    {
        overlayManager.remove(cameraOverlay);
        overlayManager.remove(sceneOverlay);
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
            bestProfile = course == null ? null : CameraProfile.parse(
                configManager.getConfiguration(RooftopCameraConfig.GROUP, profileKey(course)));
            scanScene();
        }
        if (course == null)
        {
            currentScore = 0;
            return;
        }

        List<Rectangle> boxes = orderedVisibleClickboxes();
        currentScore = LayoutScorer.score(boxes, client.getViewportWidth(), client.getViewportHeight());
        if (config.autoLearn() && boxes.size() >= 2 && (bestProfile == null || currentScore > bestProfile.score + 0.5))
        {
            bestProfile = new CameraProfile(client.getCameraYawTarget(), client.getCameraPitchTarget(), client.getScale(), currentScore);
            configManager.setConfiguration(RooftopCameraConfig.GROUP, profileKey(course), bestProfile.serialize());
        }
    }

    @Subscribe
    public void onMenuOptionClicked(MenuOptionClicked event)
    {
        if (course != null && course.contains(event.getId()))
        {
            lastClickedObstacle = event.getId();
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
    CameraProfile getBestProfile() { return bestProfile; }
    double getCurrentScore() { return currentScore; }
    Map<TileObject, Integer> getTracked() { return tracked; }
    int getNextObstacleId() { return course == null ? -1 : course.nextAfter(lastClickedObstacle); }

    String cameraGuidance()
    {
        if (bestProfile == null)
        {
            return "Adjust the camera; a better layout is saved automatically";
        }
        int yawDelta = signedYawDelta(client.getCameraYawTarget(), bestProfile.yaw);
        int pitchDelta = bestProfile.pitch - client.getCameraPitchTarget();
        int scaleDelta = bestProfile.scale - client.getScale();
        if (Math.abs(yawDelta) <= 12 && Math.abs(pitchDelta) <= 8 && Math.abs(scaleDelta) <= 12)
        {
            return "Best learned camera position";
        }
        String turn = Math.abs(yawDelta) <= 12 ? "hold yaw" : yawDelta > 0 ? "rotate right" : "rotate left";
        String tilt = Math.abs(pitchDelta) <= 8 ? "hold pitch" : pitchDelta > 0 ? "tilt up" : "tilt down";
        String zoom = Math.abs(scaleDelta) <= 12 ? "hold zoom" : scaleDelta > 0 ? "zoom in" : "zoom out";
        return turn + " | " + tilt + " | " + zoom;
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
        tracked.remove(oldObject);
        if (newObject != null && course != null && course.contains(newObject.getId()))
        {
            tracked.put(newObject, course.indexOf(newObject.getId()));
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

    private static String profileKey(RooftopCourse course)
    {
        return "profile." + course.name().toLowerCase();
    }

    private void reset()
    {
        tracked.clear();
        course = null;
        bestProfile = null;
        currentScore = 0;
        lastClickedObstacle = -1;
    }
}
