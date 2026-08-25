package com.mvpitsolutions.rooftopcamera;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Shape;
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
        int next = plugin.getNextObstacleId();
        for (Map.Entry<TileObject, Integer> entry : plugin.getTracked().entrySet())
        {
            TileObject object = entry.getKey();
            boolean isNext = object.getId() == next;
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
        }
        return null;
    }
}
