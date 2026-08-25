package com.mvpitsolutions.rooftopcamera;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class CameraSearchPlannerTest
{
    @Test
    public void requiresBaselineBeforeExploration()
    {
        SearchHistory history = new SearchHistory();
        CameraTarget current = new CameraTarget(0, 1288, 512);
        CameraTarget target = new CameraSearchPlanner().nextTarget(history, null, current);
        assertEquals(current.key(), target.key());
    }

    @Test
    public void beginsWithCoarseYawNeighborAfterBaseline()
    {
        SearchHistory history = new SearchHistory();
        CameraCandidateStats baseline = history.getOrCreate(0, 1288, 512);
        baseline.samples = 2;
        baseline.overlapTotal = 2;
        baseline.gapTotal = 2000;
        baseline.centerTotal = 4000;
        baseline.mouseTotal = 5000;

        CameraTarget target = new CameraSearchPlanner().nextTarget(history, history.best(),
            new CameraTarget(0, 1288, 512));
        assertEquals(new CameraTarget(1792, 1288, 512).key(), target.key());
    }

    @Test
    public void finishesOnlyAfterAxisAndFineDiagonalNeighborsAreTested()
    {
        SearchHistory history = new SearchHistory();
        CameraCandidateStats baseline = eligible(history, 0, 1288, 512);
        int[][] steps = {{256,128,128},{128,64,64},{64,32,32},{32,16,16},{16,8,16}};
        for (int level = 0; level < steps.length; level++)
        {
            for (int y = -1; y <= 1; y++) for (int p = -1; p <= 1; p++) for (int z = -1; z <= 1; z++)
            {
                int changed = Math.abs(y) + Math.abs(p) + Math.abs(z);
                if (changed == 0 || (level < steps.length - 1 && changed != 1)) continue;
                eligible(history, CameraSearchPlanner.normalizeYaw(y * steps[level][0]),
                    1288 + p * steps[level][1], 512 + z * steps[level][2]);
            }
        }
        assertNull(new CameraSearchPlanner().nextTarget(history, baseline, new CameraTarget(0, 1288, 512)));
    }

    private static CameraCandidateStats eligible(SearchHistory history, int yaw, int pitch, int zoom)
    {
        CameraCandidateStats candidate = history.getOrCreate(yaw, pitch, zoom);
        candidate.samples = 2;
        candidate.overlapTotal = 2;
        candidate.gapTotal = 2000;
        candidate.centerTotal = 4000;
        candidate.mouseTotal = 5000;
        return candidate;
    }
}
