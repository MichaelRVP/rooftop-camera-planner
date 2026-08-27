package com.mvpitsolutions.rooftopcamera;

import java.awt.Rectangle;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class ScreenMarkerLayoutTest
{
    @Test
    public void calibratedLayoutScalingIsStableAcrossRepeatedFrames()
    {
        ScreenMarkerLayout layout = new ScreenMarkerLayout(100, 100, Arrays.asList(
            new Rectangle(10, 20, 30, 40),
            new Rectangle(55, 60, 20, 15)));

        List<Rectangle> firstFrame = layout.scaledTo(200, 200);
        List<Rectangle> laterFrame = layout.scaledTo(200, 200);

        assertEquals(firstFrame, laterFrame);
        assertEquals(new Rectangle(20, 40, 60, 80), laterFrame.get(0));
        assertEquals(new Rectangle(110, 120, 40, 30), laterFrame.get(1));
    }

    @Test
    public void roundTripsAndScalesMarkers()
    {
        ScreenMarkerLayout original = new ScreenMarkerLayout(800, 600,
            Arrays.asList(new Rectangle(100, 120, 40, 20), null, new Rectangle(600, 300, 80, 40)));
        ScreenMarkerLayout parsed = ScreenMarkerLayout.parse(original.serialize());
        org.junit.Assert.assertTrue(parsed.verifiedInnerRectangles);
        org.junit.Assert.assertTrue(parsed.releaseValidated);
        List<Rectangle> scaled = parsed.scaledTo(1600, 1200);
        assertEquals(new Rectangle(200, 240, 80, 40), scaled.get(0));
        assertNull(scaled.get(1));
        assertEquals(new Rectangle(1200, 600, 160, 80), scaled.get(2));
    }

    @Test
    public void legacyBoundingBoxesRemainReadableButUnverified()
    {
        ScreenMarkerLayout parsed = ScreenMarkerLayout.parse("800,600|1:2:3:4");
        org.junit.Assert.assertFalse(parsed.verifiedInnerRectangles);
        assertEquals(new Rectangle(1, 2, 3, 4), parsed.markers.get(0));
    }

    @Test
    public void v2LayoutsRemainReadableButCannotBeUsedAsReleaseMarkers()
    {
        ScreenMarkerLayout parsed = ScreenMarkerLayout.parse("v2|800,600|1:2:30:40");

        org.junit.Assert.assertTrue(parsed.verifiedInnerRectangles);
        org.junit.Assert.assertFalse(parsed.releaseValidated);
        assertEquals(new Rectangle(1, 2, 30, 40), parsed.markers.get(0));
    }

    @Test
    public void liveObstacleClickboxCorrectsAStaleCalibratedMarker()
    {
        List<Rectangle> saved = Arrays.asList(
            new Rectangle(10, 10, 20, 20), new Rectangle(40, 40, 20, 20));
        Rectangle live = new Rectangle(14, 18, 28, 24);

        List<Rectangle> merged = RooftopCameraPlugin.mergeLiveMarkers(saved,
            Collections.singletonMap(0, live));

        assertEquals(live, merged.get(0));
        assertEquals(saved.get(1), merged.get(1));
        assertEquals(new Rectangle(10, 10, 20, 20), saved.get(0));
    }

    @Test
    public void liveClickboxFillsOnlyAMissingCalibratedMarker()
    {
        Rectangle live = new Rectangle(14, 18, 28, 24);
        List<Rectangle> merged = RooftopCameraPlugin.mergeLiveMarkers(
            Arrays.asList(null, new Rectangle(40, 40, 20, 20)), Collections.singletonMap(0, live));

        assertEquals(live, merged.get(0));
        assertEquals(new Rectangle(40, 40, 20, 20), merged.get(1));
    }
}
