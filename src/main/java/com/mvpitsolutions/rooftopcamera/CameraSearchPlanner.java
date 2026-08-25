package com.mvpitsolutions.rooftopcamera;

import java.util.ArrayList;
import java.util.List;

final class CameraSearchPlanner
{
    // Deliberately distant probes prevent the fine search from settling around a poor starting yaw.
    private static final int[] WIDE_YAW_OFFSETS = {512, 768, 1024, 1280, 1536};
    private static final int[][] STEPS = {
        {256, 128, 128}, {128, 64, 64}, {64, 32, 32}, {32, 16, 16}, {16, 8, 16}
    };

    CameraTarget nextTarget(SearchHistory history, CameraCandidateStats best, CameraTarget current,
        CameraBounds bounds)
    {
        CameraTarget center = best == null ? current : new CameraTarget(best.yaw, best.pitch, best.zoom);
        if (best == null)
        {
            CameraCandidateStats baseline = history.get(center);
            return baseline == null || baseline.samples < 2 ? center : null;
        }

        CameraCandidateStats anchor = history.firstEligible();
        if (anchor != null)
        {
            for (int offset : WIDE_YAW_OFFSETS)
            {
                CameraTarget target = new CameraTarget(normalizeYaw(anchor.yaw + offset),
                    bounds.clampPitch(anchor.pitch), bounds.clampZoom(anchor.zoom));
                CameraCandidateStats candidate = history.get(target);
                if (candidate == null || candidate.samples < 2) return target;
            }
        }

        for (int level = 0; level < STEPS.length; level++)
        {
            for (CameraTarget target : neighbors(center, STEPS[level], level == STEPS.length - 1, bounds))
            {
                if (target.key().equals(center.key())) continue;
                CameraCandidateStats candidate = history.get(target);
                if (candidate == null || candidate.samples < 2) return target;
            }
        }
        return null;
    }

    private static List<CameraTarget> neighbors(CameraTarget center, int[] step, boolean includeDiagonals,
        CameraBounds bounds)
    {
        List<CameraTarget> targets = new ArrayList<>();
        for (int y = -1; y <= 1; y++)
        {
            for (int p = -1; p <= 1; p++)
            {
                for (int z = -1; z <= 1; z++)
                {
                    int changed = Math.abs(y) + Math.abs(p) + Math.abs(z);
                    if (changed == 0 || (!includeDiagonals && changed != 1)) continue;
                    CameraTarget target = new CameraTarget(normalizeYaw(center.yaw + y * step[0]),
                        bounds.clampPitch(center.pitch + p * step[1]),
                        bounds.clampZoom(center.zoom + z * step[2]));
                    if (targets.stream().noneMatch(existing -> existing.key().equals(target.key())))
                    {
                        targets.add(target);
                    }
                }
            }
        }
        return targets;
    }

    static int normalizeYaw(int yaw) { return ((yaw % 2048) + 2048) % 2048; }
}
