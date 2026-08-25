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
    private final RooftopCameraConfig config;
    private final CameraRadarComponent radar = new CameraRadarComponent();

    @Inject
    RooftopCameraOverlay(RooftopCameraPlugin plugin, RooftopCameraConfig config)
    {
        this.plugin = plugin;
        this.config = config;
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
        if (config.showDiagnostics())
        {
            panelComponent.getChildren().add(LineComponent.builder().left("Obstacles found").right(
                plugin.getTrackedObstacleCount() + " / " + course.obstacles.length).build());
            panelComponent.getChildren().add(LineComponent.builder().left("Visible now").right(
                Integer.toString(plugin.getVisibleObstacleCount())).build());
        }
        int next = plugin.getNextObstacleNumber();
        panelComponent.getChildren().add(LineComponent.builder().left("Next target").right(
            next < 0 ? "Acquiring" : "Obstacle " + next).build());
        if (config.showDiagnostics())
        {
            panelComponent.getChildren().add(LineComponent.builder().left("Live visibility").right(
                String.format("%.0f", plugin.getCurrentScore())).build());
        }
        CameraGuidanceState guidance = plugin.getCameraGuidanceState();
        if (guidance != null)
        {
            radar.setState(guidance);
            panelComponent.getChildren().add(radar);
        }
        else
        {
            panelComponent.getChildren().add(LineComponent.builder().left(plugin.cameraGuidance())
                .leftColor(new Color(74, 220, 200)).build());
        }
        LapOptimizer optimizer = plugin.getLapOptimizer();
        CameraTarget target = plugin.getSearchTarget();
        if (target != null)
        {
            if (config.showDiagnostics())
            {
                panelComponent.getChildren().add(LineComponent.builder().left("Search target").right(
                    plugin.nextExperiment()).build());
                panelComponent.getChildren().add(LineComponent.builder().left("Target evidence").right(
                    plugin.getSearchTargetSamples() + " valid laps").build());
            }
        }
        panelComponent.getChildren().add(LineComponent.builder().left("Lap progress").right(
            optimizer.getProgress() + " / " + course.obstacles.length).build());
        if (!optimizer.isCurrentLapStableSoFar())
        {
            panelComponent.getChildren().add(LineComponent.builder()
                .left("Camera changed: next lap will count")
                .leftColor(new Color(255, 190, 70)).build());
        }
        if (config.showDiagnostics())
        {
            panelComponent.getChildren().add(LineComponent.builder().left("Mouse travel").right(
                String.format("%.0f px", optimizer.getCurrentTravel())).build());
            if (!Double.isNaN(optimizer.getLastTravel()))
            {
                panelComponent.getChildren().add(LineComponent.builder().left("Last lap").right(
                    String.format("%.0f px%s", optimizer.getLastTravel(),
                        optimizer.wasLastLapStable() ? "" : " (camera moved)")).build());
                if (!Double.isNaN(optimizer.getLastMarkerTravel()))
                {
                    panelComponent.getChildren().add(LineComponent.builder().left("Marker overlap").right(
                        optimizer.getLastOverlappingTransitions() + " / " + course.obstacles.length).build());
                    panelComponent.getChildren().add(LineComponent.builder().left("Marker gaps").right(
                        String.format("%.0f px", optimizer.getLastMarkerGap())).build());
                }
            }
        }
        TravelProfile profile = plugin.getBestTravelProfile();
        if (profile != null)
        {
            panelComponent.getChildren().add(LineComponent.builder().left("Best overlap").right(
                String.format("%.1f / %d", profile.overlappingTransitions, course.obstacles.length)).build());
            panelComponent.getChildren().add(LineComponent.builder().left("Best gaps").right(
                String.format("%.0f px (%d laps)", profile.markerGap, profile.samples)).build());
            if (config.showDiagnostics())
            {
                panelComponent.getChildren().add(LineComponent.builder().left("Shared overlap").right(
                    String.format("%.0f px^2", profile.overlapArea)).build());
                panelComponent.getChildren().add(LineComponent.builder().left("Observed mouse").right(
                    String.format("%.0f px", profile.observedMouseTravel)).build());
                panelComponent.getChildren().add(LineComponent.builder().left("Best camera").right(
                    "Y " + profile.yaw + "  P " + profile.pitch + "  Z " + profile.zoom).build());
            }
            if (target == null)
            {
                panelComponent.getChildren().add(LineComponent.builder().left("Search status").right(
                    "Complete - markers ready").leftColor(new Color(74, 220, 200)).build());
            }
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
