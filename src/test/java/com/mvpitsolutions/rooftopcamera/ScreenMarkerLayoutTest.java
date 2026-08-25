package com.mvpitsolutions.rooftopcamera;

import java.awt.Rectangle;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class ScreenMarkerLayoutTest
{
    @Test
    public void roundTripsAndScalesMarkers()
    {
        ScreenMarkerLayout original = new ScreenMarkerLayout(800, 600,
            Arrays.asList(new Rectangle(100, 120, 40, 20), null, new Rectangle(600, 300, 80, 40)));
        ScreenMarkerLayout parsed = ScreenMarkerLayout.parse(original.serialize());
        org.junit.Assert.assertTrue(parsed.verifiedInnerRectangles);
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
}
