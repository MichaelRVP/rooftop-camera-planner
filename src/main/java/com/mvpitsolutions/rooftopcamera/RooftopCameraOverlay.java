package com.mvpitsolutions.rooftopcamera;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Color;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

final class RooftopCameraOverlay extends OverlayPanel
{
    private final RooftopCameraPlugin plugin;

    @Inject
    RooftopCameraOverlay(RooftopCameraPlugin plugin)
    {
        this.plugin = plugin;
        panelComponent.setPreferredSize(new Dimension(285, 0));
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        RooftopCourse course = plugin.getCourse();
        if (course == null)
        {
            return null;
        }
        panelComponent.getChildren().add(TitleComponent.builder().text("Rooftop Camera Planner").build());
        panelComponent.getChildren().add(LineComponent.builder().left("Course").right(course.displayName).build());
        panelComponent.getChildren().add(LineComponent.builder().left("Obstacles found").right(
            plugin.getTrackedObstacleCount() + " / " + course.obstacles.length).build());
        panelComponent.getChildren().add(LineComponent.builder().left("Visible now").right(
            Integer.toString(plugin.getVisibleObstacleCount())).build());
        int next = plugin.getNextObstacleNumber();
        panelComponent.getChildren().add(LineComponent.builder().left("Next target").right(
            next < 0 ? "Acquiring" : "Obstacle " + next).build());
        panelComponent.getChildren().add(LineComponent.builder().left("Layout score").right(
            String.format("%.0f", plugin.getCurrentScore())).build());
        panelComponent.getChildren().add(LineComponent.builder().left("Camera guidance").build());
        panelComponent.getChildren().add(LineComponent.builder().left(plugin.cameraGuidance())
            .leftColor(new Color(74, 220, 200)).build());
        CameraProfile profile = plugin.getBestProfile();
        if (profile != null)
        {
            panelComponent.getChildren().add(LineComponent.builder().left("Target").right(
                "Y " + profile.yaw + "  P " + profile.pitch + "  Z " + profile.scale).build());
        }
        else if (plugin.getTrackedObstacleCount() == 0)
        {
            panelComponent.getChildren().add(LineComponent.builder()
                .left("Scanning the active course...")
                .leftColor(new Color(255, 190, 70)).build());
        }
        return super.render(graphics);
    }
}
