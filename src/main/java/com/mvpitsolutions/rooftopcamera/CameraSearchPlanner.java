package com.mvpitsolutions.rooftopcamera;

import java.util.ArrayList;
import java.util.List;

final class CameraSearchPlanner
{
    static final int INITIAL_VALID_LAPS = 6;
    static final int MAX_VALID_LAPS = INITIAL_VALID_LAPS;
    private static final int WIDE_EXPLORATION_SAMPLES = 6;
    // Start with small, route-safe probes. A mechanically valid camera extreme can
    // make every obstacle disappear, which is useless for the low-mouse-travel goal.
    private static final int PITCH_PROBE_DELTA = 128;
    private static final int ZOOM_PROBE_DELTA = 64;
    private final CompoundViewPredictor compoundPredictor = new CompoundViewPredictor();

    CameraTarget nextTarget(SearchHistory history, CameraCandidateStats ignoredBest,
        CameraTarget current, CameraBounds bounds)
    {
        return nextTarget(history, ignoredBest, current, bounds, INITIAL_VALID_LAPS);
    }

    CameraTarget nextTarget(SearchHistory history, CameraCandidateStats ignoredBest,
        CameraTarget current, CameraBounds bounds, int targetSamples)
    {
        if (history.totalSamples() >= targetSamples)
        {
            return null;
        }
        CameraCandidateStats anchor = history.first();
        if (anchor == null)
        {
            return canonicalize(current);
        }

        int explorationSamples = Math.min(WIDE_EXPLORATION_SAMPLES, targetSamples);
        if (history.totalSamples() < explorationSamples)
        {
            for (CameraTarget target : explorationTargets(anchor, bounds))
            {
                if (needsLap(history, target)) return target;
            }
        }

        CompoundViewPredictor.Prediction prediction = compoundPredictor.bestPrediction(history, bounds);
        if (prediction != null && needsLap(history, prediction.target))
        {
            return prediction.target;
        }

        CameraCandidateStats measuredBest = history.best();
        if (measuredBest != null)
        {
            CameraTarget measuredCenter = new CameraTarget(
                measuredBest.yaw, measuredBest.pitch, measuredBest.zoom);
            for (CameraTarget target : precisionTargets(measuredCenter, bounds))
            {
                if (needsLap(history, target)) return target;
            }
        }

        if (history.totalSamples() == targetSamples - 1)
        {
            CameraCandidateStats winner = history.best();
            return winner == null ? null : new CameraTarget(winner.yaw, winner.pitch, winner.zoom);
        }

        if (history.totalSamples() < targetSamples)
        {
            CameraCandidateStats winner = history.best();
            return winner == null ? null : new CameraTarget(winner.yaw, winner.pitch, winner.zoom);
        }
        return null;
    }

    boolean isComplete(SearchHistory history)
    {
        return isComplete(history, INITIAL_VALID_LAPS);
    }

    boolean isComplete(SearchHistory history, int targetSamples)
    {
        return history.totalSamples() >= targetSamples;
    }

    private static boolean needsLap(SearchHistory history, CameraTarget target)
    {
        CameraCandidateStats candidate = history.get(target);
        return candidate == null || (candidate.samples == 0 && !candidate.isRejected());
    }

    private static List<CameraTarget> explorationTargets(CameraCandidateStats anchor, CameraBounds bounds)
    {
        List<CameraTarget> targets = new ArrayList<>();
        int lowPitch = ceilToStep(bounds.clampPitch(anchor.pitch - PITCH_PROBE_DELTA), 8);
        int highPitch = floorToStep(bounds.clampPitch(anchor.pitch + PITCH_PROBE_DELTA), 8);
        int lowZoom = ceilToStep(bounds.clampZoom(anchor.zoom - ZOOM_PROBE_DELTA), 16);
        int highZoom = floorToStep(bounds.clampZoom(anchor.zoom + ZOOM_PROBE_DELTA), 16);
        addUnique(targets, new CameraTarget(anchor.yaw, anchor.pitch, anchor.zoom));
        // Establish the usable tilt/zoom envelope before spending experiments on yaw.
        addUnique(targets, new CameraTarget(anchor.yaw, lowPitch, anchor.zoom));
        addUnique(targets, new CameraTarget(anchor.yaw, highPitch, anchor.zoom));
        addUnique(targets, new CameraTarget(anchor.yaw, anchor.pitch, lowZoom));
        addUnique(targets, new CameraTarget(anchor.yaw, anchor.pitch, highZoom));
        addUnique(targets, new CameraTarget(normalizeYaw(anchor.yaw + 683), anchor.pitch, anchor.zoom));
        addUnique(targets, new CameraTarget(normalizeYaw(anchor.yaw + 1365), anchor.pitch, anchor.zoom));
        return targets;
    }

    private static List<CameraTarget> precisionTargets(CameraTarget center, CameraBounds bounds)
    {
        List<CameraTarget> targets = new ArrayList<>();
        addUnique(targets, new CameraTarget(normalizeYaw(center.yaw - 128), center.pitch, center.zoom));
        addUnique(targets, new CameraTarget(normalizeYaw(center.yaw + 128), center.pitch, center.zoom));
        addUnique(targets, new CameraTarget(normalizeYaw(center.yaw - 64), center.pitch, center.zoom));
        addUnique(targets, new CameraTarget(normalizeYaw(center.yaw + 64), center.pitch, center.zoom));
        addUnique(targets, new CameraTarget(center.yaw, bounds.clampPitch(center.pitch - 64), center.zoom));
        addUnique(targets, new CameraTarget(center.yaw, bounds.clampPitch(center.pitch + 64), center.zoom));
        addUnique(targets, new CameraTarget(center.yaw, center.pitch, bounds.clampZoom(center.zoom - 64)));
        addUnique(targets, new CameraTarget(center.yaw, center.pitch, bounds.clampZoom(center.zoom + 64)));
        targets.removeIf(target -> target.key().equals(center.key()));
        return targets;
    }

    private static CameraCandidateStats bestOf(SearchHistory history, List<CameraTarget> targets)
    {
        CameraCandidateStats best = null;
        for (CameraTarget target : targets)
        {
            CameraCandidateStats candidate = history.get(target);
            if (candidate != null && candidate.isEligible() && candidate.isBetterThan(best)) best = candidate;
        }
        return best;
    }

    private static void addUnique(List<CameraTarget> targets, CameraTarget target)
    {
        CameraTarget canonical = canonicalize(target);
        if (targets.stream().noneMatch(existing -> existing.key().equals(canonical.key())))
        {
            targets.add(canonical);
        }
    }

    private static CameraTarget canonicalize(CameraTarget target)
    {
        return new CameraTarget(normalizeYaw(quantize(target.yaw, 16)),
            quantize(target.pitch, 8), quantize(target.zoom, 16));
    }

    private static int quantize(int value, int step)
    {
        return Math.round((float) value / step) * step;
    }

    private static int floorToStep(int value, int step)
    {
        return Math.floorDiv(value, step) * step;
    }

    private static int ceilToStep(int value, int step)
    {
        return -Math.floorDiv(-value, step) * step;
    }

    static int normalizeYaw(int yaw) { return ((yaw % 2048) + 2048) % 2048; }
}
