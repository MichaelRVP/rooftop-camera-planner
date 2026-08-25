package com.mvpitsolutions.rooftopcamera;

import java.awt.Color;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(RooftopCameraConfig.GROUP)
public interface RooftopCameraConfig extends Config
{
    String GROUP = "rooftopCameraPlanner";

    @ConfigItem(keyName = "autoLearn", name = "Learn better layouts", description = "Learn obstacle screen markers from complete laps and retain the camera with the most overlap and least travel")
    default boolean autoLearn() { return true; }

    @ConfigItem(keyName = "showAll", name = "Show all obstacles", description = "Highlight every visible rooftop obstacle instead of only the next one")
    default boolean showAllObstacles() { return true; }

    @ConfigItem(keyName = "showDiagnostics", name = "Show optimizer diagnostics", description = "Show detailed camera-search evidence and raw route measurements")
    default boolean showDiagnostics() { return false; }

    @Alpha
    @ConfigItem(keyName = "obstacleColor", name = "Obstacle color", description = "Color used for normal obstacle clickboxes")
    default Color obstacleColor() { return new Color(65, 214, 196, 90); }

    @Alpha
    @ConfigItem(keyName = "nextColor", name = "Next obstacle color", description = "Color used for the expected next obstacle")
    default Color nextColor() { return new Color(255, 197, 61, 170); }
}
