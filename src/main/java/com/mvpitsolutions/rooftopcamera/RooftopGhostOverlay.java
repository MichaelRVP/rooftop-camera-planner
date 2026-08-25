package com.mvpitsolutions.rooftopcamera;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.List;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

final class RooftopGhostOverlay extends Overlay
{
    private final RooftopCameraPlugin plugin;

    @Inject
    RooftopGhostOverlay(RooftopCameraPlugin plugin)
    {
        this.plugin = plugin;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        List<Rectangle> markers = plugin.getScaledBestMarkers();
        int next = plugin.getNextObstacleNumber() - 1;
        for (int i = 0; i < markers.size(); i++)
        {
            Rectangle marker = markers.get(i);
            if (marker == null) continue;
            boolean isNext = i == next;
            graphics.setColor(isNext ? new Color(67, 232, 201, 65) : new Color(255, 196, 64, 35));
            graphics.fill(marker);
            graphics.setColor(isNext ? new Color(67, 232, 201, 225) : new Color(255, 196, 64, 175));
            graphics.setStroke(new BasicStroke(isNext ? 3f : 1.5f));
            graphics.draw(marker);
            graphics.drawString(Integer.toString(i + 1), marker.x + 4, marker.y + 14);
        }
        return null;
    }
}
