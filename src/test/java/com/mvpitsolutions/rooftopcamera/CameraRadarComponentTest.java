package com.mvpitsolutions.rooftopcamera;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.image.BufferedImage;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CameraRadarComponentTest
{
    @Test
    public void rendersAStableCompactGuidanceSurface()
    {
        CameraRadarComponent radar = new CameraRadarComponent();
        radar.setPreferredLocation(new Point(5, 7));
        radar.setPreferredSize(new Dimension(269, 154));
        radar.setState(new CameraGuidanceState(300, -80, 120, true, 4));
        BufferedImage image = new BufferedImage(300, 180, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();

        Dimension rendered = radar.render(graphics);
        graphics.dispose();

        assertEquals(new Dimension(269, 154), rendered);
        assertEquals(5, radar.getBounds().x);
        assertEquals(7, radar.getBounds().y);
        assertTrue(image.getRGB(10, 12) != 0);
    }
}
