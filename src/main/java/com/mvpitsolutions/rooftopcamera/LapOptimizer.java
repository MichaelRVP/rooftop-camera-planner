package com.mvpitsolutions.rooftopcamera;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class LapOptimizer
{
    static final class CompletedLap
    {
        final double mouseTravel;
        final double markerTravel;
        final double markerGap;
        final double overlapArea;
        final int overlappingTransitions;
        final int yaw;
        final int pitch;
        final int zoom;
        final boolean stableCamera;
        final List<Rectangle> markers;

        CompletedLap(double mouseTravel, MarkerRouteScore markerScore, int yaw, int pitch, int zoom,
            boolean stableCamera, List<Rectangle> markers)
        {
            this.mouseTravel = mouseTravel;
            this.markerTravel = markerScore.centerTravel;
            this.markerGap = markerScore.gapTravel;
            this.overlapArea = markerScore.overlapArea;
            this.overlappingTransitions = markerScore.overlappingTransitions;
            this.yaw = yaw;
            this.pitch = pitch;
            this.zoom = zoom;
            this.stableCamera = stableCamera;
            this.markers = markers;
        }
    }

    private int routeSize;
    private boolean active;
    private int expectedIndex;
    private int lastObstacleIndex = -1;
    private boolean hasMouseSample;
    private int lastMouseX;
    private int lastMouseY;
    private double currentTravel;
    private double lastTravel = Double.NaN;
    private double lastMarkerTravel = Double.NaN;
    private double lastMarkerGap = Double.NaN;
    private int lastOverlappingTransitions;
    private boolean lastLapStable;
    private int startYaw;
    private int startPitch;
    private int startZoom;
    private boolean cameraAlignedAtCheckpoints;
    private int completedLaps;
    private Rectangle[] markers = new Rectangle[0];

    void reset(int routeSize)
    {
        this.routeSize = routeSize;
        active = false;
        expectedIndex = 0;
        lastObstacleIndex = -1;
        hasMouseSample = false;
        currentTravel = 0;
        lastTravel = Double.NaN;
        lastMarkerTravel = Double.NaN;
        lastMarkerGap = Double.NaN;
        lastOverlappingTransitions = 0;
        lastLapStable = false;
        completedLaps = 0;
        markers = new Rectangle[Math.max(0, routeSize)];
    }

    void sampleMouse(int x, int y, int yaw, int pitch, int zoom)
    {
        if (!active)
        {
            return;
        }
        if (hasMouseSample)
        {
            currentTravel += Math.hypot(x - lastMouseX, y - lastMouseY);
        }
        lastMouseX = x;
        lastMouseY = y;
        hasMouseSample = true;
    }

    void pauseMouseSampling() { hasMouseSample = false; }
    void cameraAdjusted() { if (active) pauseMouseSampling(); }
    void cameraTargetRejected()
    {
        if (active)
        {
            cameraAlignedAtCheckpoints = false;
            active = false;
            hasMouseSample = false;
        }
    }

    CompletedLap obstacleClicked(int index, int mouseX, int mouseY, int yaw, int pitch, int zoom,
        Rectangle clickbox)
    {
        return obstacleClicked(index, mouseX, mouseY, yaw, pitch, zoom, clickbox, true);
    }

    CompletedLap obstacleClicked(int index, int mouseX, int mouseY, int yaw, int pitch, int zoom,
        Rectangle clickbox, boolean cameraAligned)
    {
        if (index == 0)
        {
            if (active && routeSize == 1)
            {
                sampleMouse(mouseX, mouseY, yaw, pitch, zoom);
                CompletedLap completed = finishLap();
                beginLap(mouseX, mouseY, yaw, pitch, zoom, clickbox, cameraAligned);
                return completed;
            }
            beginLap(mouseX, mouseY, yaw, pitch, zoom, clickbox, cameraAligned);
            return null;
        }
        if (!active || index == lastObstacleIndex)
        {
            return null;
        }
        if (index != expectedIndex)
        {
            active = false;
            hasMouseSample = false;
            return null;
        }
        sampleMouse(mouseX, mouseY, yaw, pitch, zoom);
        cameraAlignedAtCheckpoints &= cameraAligned;
        markers[index] = copy(clickbox);
        lastObstacleIndex = index;
        expectedIndex++;
        return expectedIndex == routeSize ? finishLap() : null;
    }

    boolean isActive() { return active; }
    int getProgress() { return active ? Math.min(expectedIndex, routeSize) : 0; }
    double getCurrentTravel() { return currentTravel; }
    double getLastTravel() { return lastTravel; }
    double getLastMarkerTravel() { return lastMarkerTravel; }
    double getLastMarkerGap() { return lastMarkerGap; }
    int getLastOverlappingTransitions() { return lastOverlappingTransitions; }
    boolean wasLastLapStable() { return lastLapStable; }
    boolean isCurrentLapStableSoFar()
    {
        return !active || cameraAlignedAtCheckpoints;
    }
    int getCompletedLaps() { return completedLaps; }

    private void beginLap(int mouseX, int mouseY, int yaw, int pitch, int zoom, Rectangle clickbox,
        boolean cameraAligned)
    {
        active = routeSize > 0;
        expectedIndex = routeSize == 1 ? routeSize : 1;
        lastObstacleIndex = 0;
        currentTravel = 0;
        hasMouseSample = true;
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        startYaw = yaw;
        startPitch = pitch;
        startZoom = zoom;
        cameraAlignedAtCheckpoints = cameraAligned;
        markers = new Rectangle[Math.max(0, routeSize)];
        if (routeSize > 0)
        {
            markers[0] = copy(clickbox);
        }
    }

    private CompletedLap finishLap()
    {
        active = false;
        hasMouseSample = false;
        lastTravel = currentTravel;
        MarkerRouteScore markerScore = scoreCyclicMarkers(markers);
        lastMarkerTravel = markerScore.centerTravel;
        lastMarkerGap = markerScore.gapTravel;
        lastOverlappingTransitions = markerScore.overlappingTransitions;
        lastLapStable = cameraAlignedAtCheckpoints;
        completedLaps++;
        List<Rectangle> snapshot = new ArrayList<>(markers.length);
        for (Rectangle marker : markers)
        {
            snapshot.add(copy(marker));
        }
        return new CompletedLap(lastTravel, markerScore, startYaw, startPitch, startZoom,
            lastLapStable, Collections.unmodifiableList(snapshot));
    }

    static final class MarkerRouteScore
    {
        final double centerTravel;
        final double gapTravel;
        final double overlapArea;
        final int overlappingTransitions;

        MarkerRouteScore(double centerTravel, double gapTravel, double overlapArea,
            int overlappingTransitions)
        {
            this.centerTravel = centerTravel;
            this.gapTravel = gapTravel;
            this.overlapArea = overlapArea;
            this.overlappingTransitions = overlappingTransitions;
        }
    }

    static MarkerRouteScore scoreCyclicMarkers(Rectangle[] markers)
    {
        if (markers == null || markers.length == 0)
        {
            return new MarkerRouteScore(Double.NaN, Double.NaN, Double.NaN, 0);
        }
        double centerTotal = 0;
        double gapTotal = 0;
        double overlapArea = 0;
        int overlaps = 0;
        for (int i = 0; i < markers.length; i++)
        {
            Rectangle from = markers[i];
            Rectangle to = markers[(i + 1) % markers.length];
            if (from == null || to == null)
            {
                return new MarkerRouteScore(Double.NaN, Double.NaN, Double.NaN, 0);
            }
            centerTotal += Math.hypot(to.getCenterX() - from.getCenterX(), to.getCenterY() - from.getCenterY());
            double dx = Math.max(0, Math.max(from.x - (to.x + to.width), to.x - (from.x + from.width)));
            double dy = Math.max(0, Math.max(from.y - (to.y + to.height), to.y - (from.y + from.height)));
            gapTotal += Math.hypot(dx, dy);
            Rectangle intersection = from.intersection(to);
            if (!intersection.isEmpty())
            {
                overlaps++;
                overlapArea += (double) intersection.width * intersection.height;
            }
        }
        return new MarkerRouteScore(centerTotal, gapTotal, overlapArea, overlaps);
    }

    private static Rectangle copy(Rectangle rectangle)
    {
        return rectangle == null ? null : new Rectangle(rectangle);
    }
}
