package com.mvpitsolutions.rooftopcamera;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import net.runelite.api.TileObject;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

final class RooftopSceneOverlay extends Overlay
{
    private final RooftopCameraPlugin plugin;
    private final RooftopCameraConfig config;

    @Inject
    RooftopSceneOverlay(RooftopCameraPlugin plugin, RooftopCameraConfig config)
    {
        this.plugin = plugin;
        this.config = config;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_SCENE);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        int nextStep = plugin.getNextObstacleNumber() - 1;
        for (Map.Entry<TileObject, Integer> entry : plugin.getTracked().entrySet())
        {
            TileObject object = entry.getKey();
            boolean isNext = entry.getValue() == nextStep;
            if (!isNext && !config.showAllObstacles())
            {
                continue;
            }
            Shape clickbox = object.getClickbox();
            if (clickbox == null)
            {
                continue;
            }
            Color color = isNext ? config.nextColor() : config.obstacleColor();
            graphics.setStroke(new BasicStroke(isNext ? 3f : 1.5f));
            OverlayUtil.renderPolygon(graphics, clickbox, color);
            java.awt.Rectangle bounds = clickbox.getBounds();
            OverlayUtil.renderTextLocation(graphics,
                new net.runelite.api.Point((int) bounds.getCenterX(), (int) bounds.getCenterY()),
                Integer.toString(entry.getValue() + 1), color);
        }
        drawScreenMarkers(graphics, plugin.getScaledBestMarkers(), plugin.getNextObstacleNumber() - 1);
        return null;
    }

    private static void drawScreenMarkers(Graphics2D graphics, List<Rectangle> markers, int next)
    {
        for (int i = 0; i < markers.size(); i++)
        {
            Rectangle marker = markers.get(i);
            if (marker == null) continue;

            boolean isNext = i == next;
            Color edge = isNext ? new Color(42, 255, 220) : new Color(255, 196, 64);
            Color fill = isNext ? new Color(42, 255, 220, 115) : new Color(255, 196, 64, 90);

            graphics.setColor(new Color(0, 0, 0, 220));
            graphics.setStroke(new BasicStroke(isNext ? 6f : 5f));
            graphics.draw(marker);
            graphics.setColor(fill);
            graphics.fill(marker);
            graphics.setColor(edge);
            graphics.setStroke(new BasicStroke(isNext ? 3f : 2f));
            graphics.draw(marker);

            String label = "M" + (i + 1);
            int labelX = marker.x + Math.max(3, marker.width / 2 - graphics.getFontMetrics().stringWidth(label) / 2);
            int labelY = marker.y + Math.max(13, marker.height / 2 + 5);
            graphics.setColor(new Color(0, 0, 0, 235));
            graphics.drawString(label, labelX + 1, labelY + 1);
            graphics.setColor(edge);
            graphics.drawString(label, labelX, labelY);
        }
    }
}
