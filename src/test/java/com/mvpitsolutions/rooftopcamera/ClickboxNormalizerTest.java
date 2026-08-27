package com.mvpitsolutions.rooftopcamera;

import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.geom.Rectangle2D;
import java.util.Arrays;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

public class ClickboxNormalizerTest
{
    @Test
    public void neverReturnsTransparentBoundingBoxCorners()
    {
        Polygon triangle = new Polygon(new int[] {0, 100, 0}, new int[] {0, 100, 100}, 3);
        Rectangle safe = ClickboxNormalizer.largestSafeRectangle(triangle, 200, 200);
        assertNotNull(safe);
        assertTrue(safe.width * safe.height < triangle.getBounds().width * triangle.getBounds().height);
        assertTrue(triangle.contains(safe.getCenterX(), safe.getCenterY()));
        assertTrue(triangle.contains(safe.x + 1, safe.y + 1));
        assertTrue(triangle.contains(safe.x + safe.width - 1, safe.y + safe.height - 1));
        assertTrue(triangle.contains(new Rectangle2D.Double(
            safe.x + 1, safe.y + 1, safe.width - 2, safe.height - 2)));
    }

    @Test
    public void choosesNearestSafeMarkerWhenClickEventArrivesAfterGeometryMoves()
    {
        Rectangle nearest = new Rectangle(100, 100, 30, 20);
        Rectangle selected = ClickboxNormalizer.nearest(new Rectangle(135, 110, 1, 1),
            Arrays.asList(new Rectangle(400, 400, 20, 20), nearest));
        assertEquals(nearest, selected);
    }

    @Test
    public void cursorFallbackMarkerPreservesAValidClickWithoutAClientClickbox()
    {
        assertEquals(new Rectangle(95, 45, 10, 10),
            RooftopCameraPlugin.cursorFallbackMarker(100, 50, 800, 600));
        assertEquals(new Rectangle(0, 0, 10, 10),
            RooftopCameraPlugin.cursorFallbackMarker(1, 2, 800, 600));
        assertEquals(new Rectangle(790, 590, 10, 10),
            RooftopCameraPlugin.cursorFallbackMarker(799, 599, 800, 600));
        assertNull(RooftopCameraPlugin.cursorFallbackMarker(-1, 50, 800, 600));
        assertNull(RooftopCameraPlugin.cursorFallbackMarker(100, 50, 0, 600));
    }
}
