package com.mvpitsolutions.rooftopcamera;

final class CameraCandidateStats
{
    final int yaw;
    final int pitch;
    final int zoom;
    int samples;
    int rejections;
    double overlapTotal;
    double overlapAreaTotal;
    double gapTotal;
    double centerTotal;
    double mouseTotal;
    ScreenMarkerLayout representativeLayout;
    private double representativeCost = Double.POSITIVE_INFINITY;
    private ScreenMarkerLayout cachedAttainableLayout;
    private double cachedAttainableTravel = Double.POSITIVE_INFINITY;

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
        double cost = lap.attainableTravel * 1_000_000d
            - lap.overlappingTransitions * 1_000d - lap.overlapArea;
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
    boolean isRejected() { return samples == 0 && rejections >= 2; }
    void reject() { rejections++; }
    void rejectAsUnreachable() { rejections = Math.max(rejections, 2); }

    boolean isBetterThan(CameraCandidateStats other)
    {
        if (other == null) return true;
        int overlap = Double.compare(averageOverlap(), other.averageOverlap());
        if (overlap != 0) return overlap > 0;
        int overlapArea = Double.compare(averageOverlapArea(), other.averageOverlapArea());
        if (overlapArea != 0) return overlapArea > 0;
        int gap = Double.compare(averageGap(), other.averageGap());
        if (gap != 0) return gap < 0;
        double thisAttainable = representativeAttainableTravel();
        double otherAttainable = other.representativeAttainableTravel();
        int attainable = Double.compare(thisAttainable, otherAttainable);
        if (attainable != 0 && (Double.isFinite(thisAttainable) || Double.isFinite(otherAttainable)))
        {
            return attainable < 0;
        }
        int center = Double.compare(averageCenter(), other.averageCenter());
        if (center != 0) return center < 0;
        return false;
    }

    double representativeAttainableTravel()
    {
        if (representativeLayout == null) return Double.POSITIVE_INFINITY;
        if (representativeLayout != cachedAttainableLayout)
        {
            cachedAttainableLayout = representativeLayout;
            cachedAttainableTravel = AttainableRouteOptimizer.solve(
                representativeLayout.markers.toArray(new java.awt.Rectangle[0])).travel;
        }
        return cachedAttainableTravel;
    }

    boolean isOperational()
    {
        if (representativeLayout == null || !representativeLayout.verifiedInnerRectangles
            || representativeLayout.markers.isEmpty()) return false;
        for (java.awt.Rectangle marker : representativeLayout.markers)
        {
            if (marker == null || marker.width < 8 || marker.height < 8) return false;
        }
        return true;
    }
}
