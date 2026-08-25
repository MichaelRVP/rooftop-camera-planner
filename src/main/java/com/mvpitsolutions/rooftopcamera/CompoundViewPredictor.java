package com.mvpitsolutions.rooftopcamera;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Predicts useful multi-axis experiments from layouts that were actually measured one axis at a time. */
final class CompoundViewPredictor
{
    private static final double MINIMUM_TRAVEL_IMPROVEMENT = 0.01;

    static final class Prediction
    {
        final CameraTarget target;
        final LapOptimizer.MarkerRouteScore score;

        Prediction(CameraTarget target, LapOptimizer.MarkerRouteScore score)
        {
            this.target = target;
            this.score = score;
        }
    }

    Prediction bestPrediction(SearchHistory history, CameraBounds bounds)
    {
        Prediction best = null;
        Collection<CameraCandidateStats> candidates = history.all();
        for (CameraCandidateStats base : candidates)
        {
            if (!usable(base)) continue;
            List<CameraCandidateStats> yawDonors = donors(candidates, base, Axis.YAW);
            List<CameraCandidateStats> pitchDonors = donors(candidates, base, Axis.PITCH);
            List<CameraCandidateStats> zoomDonors = donors(candidates, base, Axis.ZOOM);

            best = better(best, combinations(history, bounds, base, yawDonors, pitchDonors, null));
            best = better(best, combinations(history, bounds, base, yawDonors, null, zoomDonors));
            best = better(best, combinations(history, bounds, base, null, pitchDonors, zoomDonors));
            best = better(best, combinations(history, bounds, base, yawDonors, pitchDonors, zoomDonors));
        }
        CameraCandidateStats measuredBest = history.best();
        if (best == null || measuredBest == null || measuredBest.representativeLayout == null)
        {
            return null;
        }
        return meaningfullyBetter(best.score, score(measuredBest.representativeLayout)) ? best : null;
    }

    private Prediction combinations(SearchHistory history, CameraBounds bounds, CameraCandidateStats base,
        List<CameraCandidateStats> yawDonors, List<CameraCandidateStats> pitchDonors,
        List<CameraCandidateStats> zoomDonors)
    {
        List<CameraCandidateStats> yawChoices = choices(yawDonors);
        List<CameraCandidateStats> pitchChoices = choices(pitchDonors);
        List<CameraCandidateStats> zoomChoices = choices(zoomDonors);
        Prediction best = null;
        for (CameraCandidateStats yaw : yawChoices)
        {
            for (CameraCandidateStats pitch : pitchChoices)
            {
                for (CameraCandidateStats zoom : zoomChoices)
                {
                    int changed = count(yaw, pitch, zoom);
                    if (changed < 2) continue;
                    CameraTarget target = new CameraTarget(
                        yaw == null ? base.yaw : yaw.yaw,
                        bounds.clampPitch(pitch == null ? base.pitch : pitch.pitch),
                        bounds.clampZoom(zoom == null ? base.zoom : zoom.zoom));
                    CameraCandidateStats prior = history.get(target);
                    if (prior != null) continue;
                    ScreenMarkerLayout predicted = combine(base, yaw, pitch, zoom);
                    if (predicted == null) continue;
                    LapOptimizer.MarkerRouteScore predictedScore = score(predicted);
                    LapOptimizer.MarkerRouteScore baseScore = score(base.representativeLayout);
                    if (!meaningfullyBetter(predictedScore, baseScore)) continue;
                    best = better(best, new Prediction(target, predictedScore));
                }
            }
        }
        return best;
    }

    private static ScreenMarkerLayout combine(CameraCandidateStats base, CameraCandidateStats... donors)
    {
        ScreenMarkerLayout baseLayout = base.representativeLayout;
        int width = baseLayout.canvasWidth;
        int height = baseLayout.canvasHeight;
        List<List<Rectangle>> scaledDonors = new ArrayList<>();
        for (CameraCandidateStats donor : donors)
        {
            if (donor == null) continue;
            if (!usable(donor) || donor.representativeLayout.markers.size() != baseLayout.markers.size()) return null;
            scaledDonors.add(donor.representativeLayout.scaledTo(width, height));
        }

        List<Rectangle> markers = new ArrayList<>(baseLayout.markers.size());
        for (int i = 0; i < baseLayout.markers.size(); i++)
        {
            Rectangle original = baseLayout.markers.get(i);
            if (original == null) return null;
            int x = original.x;
            int y = original.y;
            int markerWidth = original.width;
            int markerHeight = original.height;
            for (List<Rectangle> donorLayout : scaledDonors)
            {
                Rectangle donor = donorLayout.get(i);
                if (donor == null) return null;
                x += donor.x - original.x;
                y += donor.y - original.y;
                markerWidth += donor.width - original.width;
                markerHeight += donor.height - original.height;
            }
            markerWidth = Math.max(1, Math.min(width, markerWidth));
            markerHeight = Math.max(1, Math.min(height, markerHeight));
            x = Math.max(0, Math.min(width - markerWidth, x));
            y = Math.max(0, Math.min(height - markerHeight, y));
            markers.add(new Rectangle(x, y, markerWidth, markerHeight));
        }
        return new ScreenMarkerLayout(width, height, markers);
    }

    private static List<CameraCandidateStats> donors(Collection<CameraCandidateStats> candidates,
        CameraCandidateStats base, Axis axis)
    {
        List<CameraCandidateStats> result = new ArrayList<>();
        for (CameraCandidateStats candidate : candidates)
        {
            if (!usable(candidate) || candidate == base) continue;
            boolean matches = axis == Axis.YAW
                ? candidate.pitch == base.pitch && candidate.zoom == base.zoom && candidate.yaw != base.yaw
                : axis == Axis.PITCH
                    ? candidate.yaw == base.yaw && candidate.zoom == base.zoom && candidate.pitch != base.pitch
                    : candidate.yaw == base.yaw && candidate.pitch == base.pitch && candidate.zoom != base.zoom;
            if (matches) result.add(candidate);
        }
        return result;
    }

    private static List<CameraCandidateStats> choices(List<CameraCandidateStats> donors)
    {
        List<CameraCandidateStats> result = new ArrayList<>();
        if (donors == null)
        {
            result.add(null);
            return result;
        }
        result.addAll(donors);
        return result;
    }

    private static boolean usable(CameraCandidateStats candidate)
    {
        return candidate != null && candidate.isEligible() && candidate.representativeLayout != null
            && candidate.representativeLayout.verifiedInnerRectangles
            && !candidate.representativeLayout.markers.isEmpty();
    }

    private static LapOptimizer.MarkerRouteScore score(ScreenMarkerLayout layout)
    {
        return LapOptimizer.scoreCyclicMarkers(layout.markers.toArray(new Rectangle[0]));
    }

    private static boolean meaningfullyBetter(LapOptimizer.MarkerRouteScore candidate,
        LapOptimizer.MarkerRouteScore baseline)
    {
        if (Double.isNaN(candidate.attainableTravel) || Double.isNaN(baseline.attainableTravel)) return false;
        return candidate.attainableTravel <= baseline.attainableTravel * (1 - MINIMUM_TRAVEL_IMPROVEMENT);
    }

    private static Prediction better(Prediction left, Prediction right)
    {
        if (right == null) return left;
        if (left == null) return right;
        return compare(right.score, left.score) < 0 ? right : left;
    }

    private static int compare(LapOptimizer.MarkerRouteScore left, LapOptimizer.MarkerRouteScore right)
    {
        int attainable = Double.compare(left.attainableTravel, right.attainableTravel);
        if (attainable != 0) return attainable;
        int overlap = Integer.compare(right.overlappingTransitions, left.overlappingTransitions);
        if (overlap != 0) return overlap;
        int area = Double.compare(right.overlapArea, left.overlapArea);
        if (area != 0) return area;
        return Double.compare(left.centerTravel, right.centerTravel);
    }

    private static int count(Object... values)
    {
        int count = 0;
        for (Object value : values) if (value != null) count++;
        return count;
    }

    private enum Axis { YAW, PITCH, ZOOM }
}
