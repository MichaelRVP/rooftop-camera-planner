package com.mvpitsolutions.rooftopcamera;

import java.awt.Dimension;
import java.awt.Graphics2D;
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
        panelComponent.getChildren().add(LineComponent.builder().left("Layout score").right(String.format("%.0f", plugin.getCurrentScore())).build());
        panelComponent.getChildren().add(LineComponent.builder().left("Camera").right(plugin.cameraGuidance()).build());
        CameraProfile profile = plugin.getBestProfile();
        if (profile != null)
        {
            panelComponent.getChildren().add(LineComponent.builder().left("Target").right(
                "Y " + profile.yaw + "  P " + profile.pitch + "  Z " + profile.scale).build());
        }
        return super.render(graphics);
    }
}
