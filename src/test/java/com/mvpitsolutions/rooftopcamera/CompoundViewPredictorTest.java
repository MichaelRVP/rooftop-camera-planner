package com.mvpitsolutions.rooftopcamera;

import java.awt.Rectangle;
import java.util.Arrays;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class CompoundViewPredictorTest
{
    @Test
    public void combinesMeasuredAxisEffectsIntoABetterUntestedCamera()
    {
        SearchHistory history = new SearchHistory();
        add(history, 0, 1000, 500, layout(100, 100));
        add(history, 128, 1000, 500, layout(50, 100));
        add(history, 0, 1100, 500, layout(100, 50));

        CompoundViewPredictor.Prediction prediction =
            new CompoundViewPredictor().bestPrediction(history, new CameraBounds());

        assertNotNull(prediction);
        assertEquals(new CameraTarget(128, 1100, 500).key(), prediction.target.key());
        assertTrue(prediction.score.attainableTravel
            < history.get(new CameraTarget(128, 1000, 500)).representativeAttainableTravel());
    }

    @Test
    public void neverSuggestsAnAlreadyMeasuredPrediction()
    {
        SearchHistory history = new SearchHistory();
        add(history, 0, 1000, 500, layout(100, 100));
        add(history, 128, 1000, 500, layout(50, 100));
        add(history, 0, 1100, 500, layout(100, 50));
        add(history, 128, 1100, 500, layout(50, 50));

        assertNull(new CompoundViewPredictor().bestPrediction(history, new CameraBounds()));
    }

    private static void add(SearchHistory history, int yaw, int pitch, int zoom, ScreenMarkerLayout layout)
    {
        CameraCandidateStats candidate = history.getOrCreate(yaw, pitch, zoom);
        candidate.samples = 1;
        candidate.representativeLayout = layout;
        LapOptimizer.MarkerRouteScore score = LapOptimizer.scoreCyclicMarkers(
            layout.markers.toArray(new Rectangle[0]));
        candidate.overlapTotal = score.overlappingTransitions;
        candidate.overlapAreaTotal = score.overlapArea;
        candidate.gapTotal = score.gapTravel;
        candidate.centerTotal = score.centerTravel;
    }

    private static ScreenMarkerLayout layout(int width, int height)
    {
        return new ScreenMarkerLayout(400, 400, Arrays.asList(
            new Rectangle(0, 0, 10, 10), new Rectangle(width, 0, 10, 10),
            new Rectangle(width, height, 10, 10), new Rectangle(0, height, 10, 10)));
    }
}
