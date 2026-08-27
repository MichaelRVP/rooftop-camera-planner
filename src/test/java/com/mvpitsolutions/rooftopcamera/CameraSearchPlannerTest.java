package com.mvpitsolutions.rooftopcamera;

import org.junit.Test;

import java.awt.Rectangle;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class CameraSearchPlannerTest
{
    private final CameraSearchPlanner planner = new CameraSearchPlanner();
    private final CameraBounds bounds = new CameraBounds();

    @Test
    public void startsAtCurrentCameraThenLearnsPitchZoomEnvelopeBeforeYaw()
    {
        SearchHistory history = new SearchHistory();
        CameraTarget current = new CameraTarget(100, 1288, 512);
        assertEquals(new CameraTarget(96, 1288, 512).key(),
            planner.nextTarget(history, null, current, bounds).key());

        sample(history, 96, 1288, 512, 1);
        assertEquals(new CameraTarget(96, 1160, 512).key(),
            planner.nextTarget(history, history.best(), current, bounds).key());
        sample(history, 96, 1160, 512, 2);
        assertEquals(new CameraTarget(96, 1416, 512).key(),
            planner.nextTarget(history, history.best(), current, bounds).key());

        sample(history, 96, 1416, 512, 2);
        assertEquals(new CameraTarget(96, 1288, 448).key(),
            planner.nextTarget(history, history.best(), current, bounds).key());
        sample(history, 96, 1288, 448, 2);
        assertEquals(new CameraTarget(96, 1288, 576).key(),
            planner.nextTarget(history, history.best(), current, bounds).key());
        sample(history, 96, 1288, 576, 2);
        assertEquals(new CameraTarget(784, 1288, 512).key(),
            planner.nextTarget(history, history.best(), current, bounds).key());
        sample(history, 784, 1288, 512, 2);
        assertNull(planner.nextTarget(history, history.best(), current, bounds));
    }

    @Test
    public void advancesAfterRecordedCameraStartsWithPitchEnvelope()
    {
        SearchHistory history = new SearchHistory();
        CameraTarget current = new CameraTarget(112, 1792, 512);
        sample(history, 112, 1792, 512, 1);

        assertEquals(new CameraTarget(112, 1664, 512).key(),
            planner.nextTarget(history, history.best(), current, bounds).key());

        sample(history, 112, 1664, 512, 2);
        assertEquals(new CameraTarget(112, 1920, 512).key(),
            planner.nextTarget(history, history.best(), current, bounds).key());
    }

    @Test
    public void advancesPastCameraThatCourseRejectsTwice()
    {
        SearchHistory history = new SearchHistory();
        CameraTarget current = new CameraTarget(100, 1288, 512);
        CameraCandidateStats rejected = history.getOrCreate(96, 1288, 512);
        rejected.reject();
        assertEquals(new CameraTarget(96, 1288, 512).key(),
            planner.nextTarget(history, null, current, bounds).key());

        rejected.reject();
        assertEquals(new CameraTarget(96, 1160, 512).key(),
            planner.nextTarget(history, null, current, bounds).key());
    }

    @Test
    public void refinesAroundBestMeasuredViewAfterWideExploration()
    {
        SearchHistory history = seededExploration();
        CameraTarget target = planner.nextTarget(history, history.best(),
            new CameraTarget(0, 1288, 512), bounds, 18);
        assertEquals(new CameraTarget(560, 1288, 512).key(), target.key());

        sample(history, 560, 1288, 512, 1);
        assertFalse(planner.isComplete(history, 18));
        assertTrue(planner.nextTarget(history, history.best(), target, bounds, 18) != null);
    }

    @Test
    public void precisionStageContinuesPastTenLapsAndThenCompletesCampaign()
    {
        SearchHistory history = seededExploration();
        int[][] local = {
            {560,1288,512}, {816,1288,512}, {688,1160,512},
            {688,1416,512}, {688,1288,384}, {688,1288,640}
        };
        for (int[] camera : local) sample(history, camera[0], camera[1], camera[2], 1);

        assertEquals(13, history.totalSamples());
        CameraTarget precision = planner.nextTarget(history, history.best(),
            new CameraTarget(0, 1288, 512), bounds, 18);
        assertTrue(precision != null);
        assertNull(history.get(precision));
        sample(history, precision.yaw, precision.pitch, precision.zoom, 3);
        assertEquals(14, history.totalSamples());
        assertFalse(planner.isComplete(history, 18));

        CameraTarget current = precision;
        for (int guard = 0; guard < 30; guard++)
        {
            CameraTarget next = planner.nextTarget(history, history.best(), current, bounds, 18);
            if (next == null) break;
            sample(history, next.yaw, next.pitch, next.zoom, 3);
            current = next;
        }

        assertTrue(history.totalSamples() >= 18);
        assertNull(planner.nextTarget(history, history.best(), current, bounds, 18));
        assertTrue(planner.isComplete(history, 18));
    }

    @Test
    public void learnedZoomBoundaryRemovesImpossibleExperiment()
    {
        SearchHistory history = seededExploration();
        CameraBounds restricted = new CameraBounds();
        assertTrue(restricted.learnZoomLimit(384, 512));

        CameraTarget next = planner.nextTarget(history, history.best(),
            new CameraTarget(688, 1288, 512), restricted, 18);

        assertEquals(new CameraTarget(560, 1288, 512).key(), next.key());
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

    @Test
    public void measuredAxisEffectsCanSelectAnUntestedCompoundView()
    {
        SearchHistory history = new SearchHistory();
        addLayout(history, 0, 1000, 500, 14, layout(100, 100));
        addLayout(history, 128, 1000, 500, 1, layout(50, 100));
        addLayout(history, 0, 1100, 500, 1, layout(100, 50));

        CameraTarget predicted = planner.nextTarget(history, history.best(),
            new CameraTarget(0, 1000, 500), bounds, 18);

        assertEquals(new CameraTarget(128, 1100, 500).key(), predicted.key());
        assertNull(history.get(predicted));
    }

    @Test
    public void completedCampaignStopsBeforeMorePredictionWork()
    {
        SearchHistory history = new SearchHistory();
        addLayout(history, 0, 1000, 500, CameraSearchPlanner.MAX_VALID_LAPS, layout(100, 100));

        assertNull(planner.nextTarget(history, history.best(), new CameraTarget(0, 1000, 500), bounds));
    }

    private static SearchHistory seededExploration()
    {
        SearchHistory history = new SearchHistory();
        sample(history, 0, 1288, 512, 1);
        sample(history, 688, 1288, 512, 3);
        sample(history, 1360, 1288, 512, 2);
        sample(history, 0, 128, 512, 1);
        sample(history, 0, 2040, 512, 1);
        sample(history, 0, 1288, -400, 1);
        sample(history, 0, 1288, 992, 1);
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

    private static void addLayout(SearchHistory history, int yaw, int pitch, int zoom, int samples,
        ScreenMarkerLayout layout)
    {
        CameraCandidateStats candidate = history.getOrCreate(yaw, pitch, zoom);
        candidate.samples = samples;
        candidate.representativeLayout = layout;
        LapOptimizer.MarkerRouteScore score = LapOptimizer.scoreCyclicMarkers(
            layout.markers.toArray(new Rectangle[0]));
        candidate.overlapTotal = score.overlappingTransitions * samples;
        candidate.overlapAreaTotal = score.overlapArea * samples;
        candidate.gapTotal = score.gapTravel * samples;
        candidate.centerTotal = score.centerTravel * samples;
    }

    private static ScreenMarkerLayout layout(int width, int height)
    {
        return new ScreenMarkerLayout(400, 400, Arrays.asList(
            new Rectangle(0, 0, 10, 10), new Rectangle(width, 0, 10, 10),
            new Rectangle(width, height, 10, 10), new Rectangle(0, height, 10, 10)));
    }
}
