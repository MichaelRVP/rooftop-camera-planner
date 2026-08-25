package com.mvpitsolutions.rooftopcamera;

import java.awt.Rectangle;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class ScreenMarkerAlignerTest
{
    @Test
    public void appliesMedianCorrectionFromMultipleLiveObstacles()
    {
        List<Rectangle> saved = Arrays.asList(new Rectangle(10, 10, 20, 20),
            new Rectangle(50, 50, 20, 20), new Rectangle(90, 90, 20, 20));
        Map<Integer, Rectangle> live = new HashMap<>();
        live.put(0, new Rectangle(16, 6, 20, 20));
        live.put(1, new Rectangle(54, 44, 20, 20));
        live.put(2, new Rectangle(96, 86, 20, 20));

        List<Rectangle> adjusted = ScreenMarkerAligner.align(saved, live, 200, 200);

        assertEquals(new Rectangle(16, 6, 20, 20), adjusted.get(0));
        assertEquals(new Rectangle(56, 46, 20, 20), adjusted.get(1));
        assertEquals(new Rectangle(96, 86, 20, 20), adjusted.get(2));
    }

    @Test
    public void ignoresSingleMatchAndClampsToCanvas()
    {
        List<Rectangle> saved = Arrays.asList(new Rectangle(95, 95, 10, 10),
            new Rectangle(10, 10, 10, 10));
        Map<Integer, Rectangle> live = new HashMap<>();
        live.put(0, new Rectangle(500, 500, 10, 10));

        List<Rectangle> adjusted = ScreenMarkerAligner.align(saved, live, 100, 100);

        assertEquals(new Rectangle(90, 90, 10, 10), adjusted.get(0));
        assertEquals(new Rectangle(10, 10, 10, 10), adjusted.get(1));
    }
}
