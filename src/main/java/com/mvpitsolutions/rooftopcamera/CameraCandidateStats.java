package com.mvpitsolutions.rooftopcamera;

final class CameraCandidateStats
{
    final int yaw;
    final int pitch;
    final int zoom;
    int samples;
    double overlapTotal;
    double overlapAreaTotal;
    double gapTotal;
    double centerTotal;
    double mouseTotal;
    ScreenMarkerLayout representativeLayout;
    private double representativeCost = Double.POSITIVE_INFINITY;

    CameraCandidateStats(int yaw, int pitch, int zoom)
    {
        this.yaw = yaw;
        this.pitch = pitch;
        this.zoom = zoom;
    }

    void add(LapOptimizer.CompletedLap lap, ScreenMarkerLayout layout)
    {
        samples++;
        overlapTotal += lap.overlappingTransitions;
        overlapAreaTotal += lap.overlapArea;
        gapTotal += lap.markerGap;
        centerTotal += lap.markerTravel;
        mouseTotal += lap.mouseTravel;
        double cost = -lap.overlappingTransitions * 1_000_000_000d - lap.overlapArea * 1_000d
            + lap.markerGap * 10d + lap.markerTravel;
        if (layout != null && cost < representativeCost)
        {
            representativeCost = cost;
            representativeLayout = layout;
        }
    }

    double averageOverlap() { return samples == 0 ? 0 : overlapTotal / samples; }
    double averageOverlapArea() { return samples == 0 ? 0 : overlapAreaTotal / samples; }
    double averageGap() { return samples == 0 ? Double.POSITIVE_INFINITY : gapTotal / samples; }
    double averageCenter() { return samples == 0 ? Double.POSITIVE_INFINITY : centerTotal / samples; }
    double averageMouse() { return samples == 0 ? Double.POSITIVE_INFINITY : mouseTotal / samples; }
    boolean isEligible() { return samples >= 1; }

    boolean isBetterThan(CameraCandidateStats other)
    {
        if (other == null) return true;
        int overlap = Double.compare(averageOverlap(), other.averageOverlap());
        if (overlap != 0) return overlap > 0;
        int overlapArea = Double.compare(averageOverlapArea(), other.averageOverlapArea());
        if (overlapArea != 0) return overlapArea > 0;
        int gap = Double.compare(averageGap(), other.averageGap());
        if (gap != 0) return gap < 0;
        int center = Double.compare(averageCenter(), other.averageCenter());
        if (center != 0) return center < 0;
        return false;
    }
}
