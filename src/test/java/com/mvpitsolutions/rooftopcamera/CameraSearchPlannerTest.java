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
        CameraTarget target = new CameraSearchPlanner().nextTarget(history, null, current, new CameraBounds());
        assertEquals(current.key(), target.key());
    }

    @Test
    public void beginsWithWideYawScoutAfterBaseline()
    {
        SearchHistory history = new SearchHistory();
        CameraCandidateStats baseline = history.getOrCreate(0, 1288, 512);
        baseline.samples = 2;
        baseline.overlapTotal = 2;
        baseline.gapTotal = 2000;
        baseline.centerTotal = 4000;
        baseline.mouseTotal = 5000;

        CameraTarget target = new CameraSearchPlanner().nextTarget(history, history.best(),
            new CameraTarget(0, 1288, 512), new CameraBounds());
        assertEquals(new CameraTarget(512, 1288, 512).key(), target.key());
    }

    @Test
    public void finishesOnlyAfterAxisAndFineDiagonalNeighborsAreTested()
    {
        SearchHistory history = new SearchHistory();
        CameraCandidateStats baseline = eligible(history, 0, 1288, 512);
        seedWideScouts(history, 0, 1288, 512);
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
        assertNull(new CameraSearchPlanner().nextTarget(history, baseline,
            new CameraTarget(0, 1288, 512), new CameraBounds()));
    }

    @Test
    public void skipsCameraTargetsOutsideLearnedBounds()
    {
        SearchHistory history = new SearchHistory();
        CameraCandidateStats baseline = eligible(history, 0, 1288, 512);
        CameraBounds bounds = new CameraBounds(1288, 2040, 512, 2048);
        CameraTarget target = new CameraSearchPlanner().nextTarget(history, baseline,
            new CameraTarget(0, 1288, 512), bounds);
        assertEquals(new CameraTarget(512, 1288, 512).key(), target.key());
    }

    @Test
    public void wideScoutsStayAnchoredToFirstBaselineWhenWinnerChanges()
    {
        SearchHistory history = new SearchHistory();
        eligible(history, 0, 1288, 512);
        CameraCandidateStats winner = eligible(history, 512, 1288, 512);
        winner.overlapTotal = 20;
        CameraTarget target = new CameraSearchPlanner().nextTarget(history, history.best(),
            new CameraTarget(512, 1288, 512), new CameraBounds());
        assertEquals(new CameraTarget(768, 1288, 512).key(), target.key());
    }

    private static void seedWideScouts(SearchHistory history, int yaw, int pitch, int zoom)
    {
        for (int offset : new int[] {512, 768, 1024, 1280, 1536})
        {
            eligible(history, CameraSearchPlanner.normalizeYaw(yaw + offset), pitch, zoom);
        }
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
