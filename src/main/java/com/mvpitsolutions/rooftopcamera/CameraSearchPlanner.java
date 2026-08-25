package com.mvpitsolutions.rooftopcamera;

import java.util.ArrayList;
import java.util.List;

final class CameraSearchPlanner
{
    static final int INITIAL_VALID_LAPS = 6;
    static final int MAX_VALID_LAPS = INITIAL_VALID_LAPS;
    private static final int[] GLOBAL_YAW_OFFSETS = {0, 683, 1365};
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

        if (history.totalSamples() >= 16)
        {
            CompoundViewPredictor.Prediction prediction = compoundPredictor.bestPrediction(history, bounds);
            if (prediction != null && needsLap(history, prediction.target)) return prediction.target;
        }

        List<CameraTarget> globalTargets = globalTargets(anchor, bounds);
        for (CameraTarget target : globalTargets)
        {
            if (needsLap(history, target)) return target;
        }

        CameraCandidateStats globalBest = bestOf(history, globalTargets);
        CameraTarget center = globalBest == null
            ? new CameraTarget(anchor.yaw, anchor.pitch, anchor.zoom)
            : new CameraTarget(globalBest.yaw, globalBest.pitch, globalBest.zoom);
        for (CameraTarget target : localTargets(center, bounds))
        {
            if (needsLap(history, target)) return target;
        }

        if (history.totalSamples() == targetSamples - 1)
        {
            CameraCandidateStats winner = history.best();
            return winner == null ? null : new CameraTarget(winner.yaw, winner.pitch, winner.zoom);
        }

        if (history.totalSamples() >= 12)
        {
            CompoundViewPredictor.Prediction prediction = compoundPredictor.bestPrediction(history, bounds);
            if (prediction != null && needsLap(history, prediction.target)) return prediction.target;
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

    private static List<CameraTarget> globalTargets(CameraCandidateStats anchor, CameraBounds bounds)
    {
        List<CameraTarget> targets = new ArrayList<>();
        for (int offset : GLOBAL_YAW_OFFSETS)
        {
            addUnique(targets, new CameraTarget(normalizeYaw(anchor.yaw + offset),
                offset == 0 ? anchor.pitch : bounds.clampPitch(anchor.pitch),
                offset == 0 ? anchor.zoom : bounds.clampZoom(anchor.zoom)));
        }
        return targets;
    }

    private static List<CameraTarget> localTargets(CameraTarget center, CameraBounds bounds)
    {
        List<CameraTarget> targets = new ArrayList<>();
        addUnique(targets, new CameraTarget(normalizeYaw(center.yaw - 256), center.pitch, center.zoom));
        addUnique(targets, new CameraTarget(normalizeYaw(center.yaw + 256), center.pitch, center.zoom));
        addUnique(targets, new CameraTarget(center.yaw, bounds.clampPitch(center.pitch - 128), center.zoom));
        addUnique(targets, new CameraTarget(center.yaw, bounds.clampPitch(center.pitch + 128), center.zoom));
        addUnique(targets, new CameraTarget(center.yaw, center.pitch, bounds.clampZoom(center.zoom - 128)));
        addUnique(targets, new CameraTarget(center.yaw, center.pitch, bounds.clampZoom(center.zoom + 128)));
        targets.removeIf(target -> target.key().equals(center.key()));
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

    static int normalizeYaw(int yaw) { return ((yaw % 2048) + 2048) % 2048; }
}
