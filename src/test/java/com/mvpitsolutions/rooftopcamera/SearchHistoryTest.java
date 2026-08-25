package com.mvpitsolutions.rooftopcamera;

import java.awt.Rectangle;
import java.util.Arrays;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class SearchHistoryTest
{
    @Test
    public void persistsCandidateEvidenceAndMarkerLayout()
    {
        SearchHistory history = new SearchHistory();
        CameraCandidateStats candidate = history.getOrCreate(32, 1200, 512);
        candidate.samples = 3;
        candidate.overlapTotal = 9;
        candidate.gapTotal = 300;
        candidate.centerTotal = 600;
        candidate.mouseTotal = 900;
        candidate.representativeLayout = new ScreenMarkerLayout(800, 600,
            Arrays.asList(new Rectangle(1, 2, 3, 4), new Rectangle(5, 6, 7, 8)));

        SearchHistory parsed = SearchHistory.parse(history.serialize());
        CameraCandidateStats restored = parsed.get(new CameraTarget(32, 1200, 512));
        assertNotNull(restored);
        assertEquals(3, restored.samples);
        assertEquals(3.0, restored.averageOverlap(), 0.001);
        assertEquals(new Rectangle(1, 2, 3, 4), restored.representativeLayout.markers.get(0));
    }

    @Test
    public void reranksWhenIncumbentAverageDegrades()
    {
        SearchHistory history = new SearchHistory();
        CameraCandidateStats first = history.getOrCreate(0, 1000, 500);
        first.samples = 2; first.overlapTotal = 6; first.gapTotal = 200; first.centerTotal = 500; first.mouseTotal = 700;
        CameraCandidateStats second = history.getOrCreate(16, 1000, 500);
        second.samples = 2; second.overlapTotal = 4; second.gapTotal = 100; second.centerTotal = 400; second.mouseTotal = 600;
        assertEquals(first, history.best());
        first.samples = 4; first.overlapTotal = 6;
        assertEquals(second, history.best());
    }
}
