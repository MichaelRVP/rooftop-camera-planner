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
        candidate.overlapAreaTotal = 270;
        candidate.gapTotal = 300;
        candidate.centerTotal = 600;
        candidate.mouseTotal = 900;
        candidate.rejections = 1;
        candidate.representativeLayout = new ScreenMarkerLayout(800, 600,
            Arrays.asList(new Rectangle(1, 2, 3, 4), new Rectangle(5, 6, 7, 8)));

        SearchHistory parsed = SearchHistory.parse(history.serialize());
        CameraCandidateStats restored = parsed.get(new CameraTarget(32, 1200, 512));
        assertNotNull(restored);
        assertEquals(3, restored.samples);
        assertEquals(3.0, restored.averageOverlap(), 0.001);
        assertEquals(90.0, restored.averageOverlapArea(), 0.001);
        assertEquals(1, restored.rejections);
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

    @Test
    public void overlapAreaBreaksTiesBeforeTravel()
    {
        SearchHistory history = new SearchHistory();
        CameraCandidateStats narrow = history.getOrCreate(0, 1000, 500);
        narrow.samples = 2; narrow.overlapTotal = 4; narrow.overlapAreaTotal = 100;
        narrow.gapTotal = 0; narrow.centerTotal = 100; narrow.mouseTotal = 100;
        CameraCandidateStats broad = history.getOrCreate(16, 1000, 500);
        broad.samples = 2; broad.overlapTotal = 4; broad.overlapAreaTotal = 200;
        broad.gapTotal = 0; broad.centerTotal = 1000; broad.mouseTotal = 1000;
        assertEquals(broad, history.best());
    }
}
