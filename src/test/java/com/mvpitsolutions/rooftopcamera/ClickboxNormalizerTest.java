package com.mvpitsolutions.rooftopcamera;

import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.geom.Rectangle2D;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

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
}
