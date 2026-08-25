package com.mvpitsolutions.rooftopcamera;

import java.util.ArrayList;
import java.util.List;

final class CameraSearchPlanner
{
    private static final int[][] STEPS = {
        {256, 128, 128}, {128, 64, 64}, {64, 32, 32}, {32, 16, 16}, {16, 8, 16}
    };

    CameraTarget nextTarget(SearchHistory history, CameraCandidateStats best, CameraTarget current)
    {
        CameraTarget center = best == null ? current : new CameraTarget(best.yaw, best.pitch, best.zoom);
        if (best == null)
        {
            CameraCandidateStats baseline = history.get(center);
            return baseline == null || baseline.samples < 2 ? center : null;
        }
        for (int level = 0; level < STEPS.length; level++)
        {
            for (CameraTarget target : neighbors(center, STEPS[level], level == STEPS.length - 1))
            {
                CameraCandidateStats candidate = history.get(target);
                if (candidate == null || candidate.samples < 2) return target;
            }
        }
        return null;
    }

    private static List<CameraTarget> neighbors(CameraTarget center, int[] step, boolean includeDiagonals)
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
                    targets.add(new CameraTarget(normalizeYaw(center.yaw + y * step[0]),
                        clamp(center.pitch + p * step[1], 128, 2040),
                        clamp(center.zoom + z * step[2], 128, 2048)));
                }
            }
        }
        return targets;
    }

    static int normalizeYaw(int yaw) { return ((yaw % 2048) + 2048) % 2048; }
    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
}
