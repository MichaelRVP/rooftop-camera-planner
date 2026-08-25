package com.mvpitsolutions.rooftopcamera;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class CameraSearchPlannerTest
{
    private final CameraSearchPlanner planner = new CameraSearchPlanner();
    private final CameraBounds bounds = new CameraBounds();

    @Test
    public void startsAtCurrentCameraThenUsesThreeWideViews()
    {
        SearchHistory history = new SearchHistory();
        CameraTarget current = new CameraTarget(100, 1288, 512);
        assertEquals(new CameraTarget(96, 1288, 512).key(),
            planner.nextTarget(history, null, current, bounds).key());

        sample(history, 96, 1288, 512, 1);
        assertEquals(new CameraTarget(784, 1288, 512).key(),
            planner.nextTarget(history, history.best(), current, bounds).key());
        sample(history, 784, 1288, 512, 2);
        assertEquals(new CameraTarget(1456, 1288, 512).key(),
            planner.nextTarget(history, history.best(), current, bounds).key());
    }

    @Test
    public void advancesAfterRecordedCameraRoundsRequestedYaw()
    {
        SearchHistory history = new SearchHistory();
        CameraTarget current = new CameraTarget(112, 1792, 512);
        sample(history, 112, 1792, 512, 1);

        assertEquals(new CameraTarget(800, 1792, 512).key(),
            planner.nextTarget(history, history.best(), current, bounds).key());

        sample(history, 800, 1792, 512, 2);
        assertEquals(new CameraTarget(1472, 1792, 512).key(),
            planner.nextTarget(history, history.best(), current, bounds).key());
    }

    @Test
    public void refinesAroundBestGlobalViewOnAllThreeAxes()
    {
        SearchHistory history = seededGlobals();
        CameraTarget target = planner.nextTarget(history, history.best(),
            new CameraTarget(0, 1288, 512), bounds);
        assertEquals(new CameraTarget(432, 1288, 512).key(), target.key());

        sample(history, 432, 1288, 512, 1);
        assertEquals(new CameraTarget(944, 1288, 512).key(),
            planner.nextTarget(history, history.best(), target, bounds).key());
    }

    @Test
    public void tenthValidLapConfirmsWinnerAndCompletesCampaign()
    {
        SearchHistory history = seededGlobals();
        int[][] local = {
            {432,1288,512}, {944,1288,512}, {688,1160,512},
            {688,1416,512}, {688,1288,384}, {688,1288,640}
        };
        for (int[] camera : local) sample(history, camera[0], camera[1], camera[2], 1);

        assertEquals(9, history.totalSamples());
        CameraTarget confirmation = planner.nextTarget(history, history.best(),
            new CameraTarget(0, 1288, 512), bounds);
        assertEquals(new CameraTarget(688, 1288, 512).key(), confirmation.key());
        sample(history, confirmation.yaw, confirmation.pitch, confirmation.zoom, 3);
        assertEquals(10, history.totalSamples());
        assertNull(planner.nextTarget(history, history.best(), confirmation, bounds));
        assertTrue(planner.isComplete(history));
    }

    @Test
    public void mouseTravelNeverChangesTheWinner()
    {
        SearchHistory history = new SearchHistory();
        CameraCandidateStats first = sample(history, 0, 1288, 512, 2);
        CameraCandidateStats second = sample(history, 100, 1288, 512, 2);
        first.mouseTotal = 1_000_000;
        second.mouseTotal = 1;
        assertFalse(second.isBetterThan(first));
        assertFalse(first.isBetterThan(second));
    }

    private static SearchHistory seededGlobals()
    {
        SearchHistory history = new SearchHistory();
        sample(history, 0, 1288, 512, 1);
        sample(history, 688, 1288, 512, 3);
        sample(history, 1360, 1288, 512, 2);
        return history;
    }

    private static CameraCandidateStats sample(SearchHistory history, int yaw, int pitch, int zoom,
        double overlap)
    {
        CameraCandidateStats candidate = history.getOrCreate(yaw, pitch, zoom);
        candidate.samples++;
        candidate.overlapTotal += overlap;
        candidate.overlapAreaTotal += overlap * 100;
        candidate.gapTotal += 1000;
        candidate.centerTotal += 2000;
        candidate.mouseTotal += 3000;
        return candidate;
    }
}
