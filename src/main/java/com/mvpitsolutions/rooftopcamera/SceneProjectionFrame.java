package com.mvpitsolutions.rooftopcamera;

import java.awt.Rectangle;
import net.runelite.api.Client;

/** Immutable scene projection coordinates; canvas resizing is not image scaling. */
final class SceneProjectionFrame
{
    final int canvasWidth, canvasHeight, x, y, width, height, scale;

    SceneProjectionFrame(int canvasWidth, int canvasHeight, int x, int y,
        int width, int height, int scale)
    {
        this.canvasWidth = canvasWidth;
        this.canvasHeight = canvasHeight;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.scale = scale;
    }

    static SceneProjectionFrame capture(Client client)
    {
        return new SceneProjectionFrame(client.getCanvasWidth(), client.getCanvasHeight(),
            client.getViewportXOffset(), client.getViewportYOffset(),
            client.getViewportWidth(), client.getViewportHeight(), client.getScale());
    }

    boolean valid()
    {
        return canvasWidth > 0 && canvasHeight > 0 && x >= 0 && y >= 0
            && width > 0 && height > 0 && scale > 0
            && (long) x + width <= canvasWidth && (long) y + height <= canvasHeight;
    }

    Rectangle viewport() { return new Rectangle(x, y, width, height); }

    boolean sameGeometry(SceneProjectionFrame other)
    {
        return other != null && canvasWidth == other.canvasWidth && canvasHeight == other.canvasHeight
            && x == other.x && y == other.y && width == other.width
            && height == other.height && scale == other.scale;
    }

    Rectangle project(Rectangle marker, SceneProjectionFrame current)
    {
        if (marker == null || marker.isEmpty() || !valid() || !viewport().contains(marker)
            || current == null || !current.valid()) return null;
        double ratio = (double) current.scale / scale;
        double oldCenterX = x + width / 2d, oldCenterY = y + height / 2d;
        double centerX = current.x + current.width / 2d
            + (marker.getCenterX() - oldCenterX) * ratio;
        double centerY = current.y + current.height / 2d
            + (marker.getCenterY() - oldCenterY) * ratio;
        int newWidth = Math.max(1, (int) Math.round(marker.width * ratio));
        int newHeight = Math.max(1, (int) Math.round(marker.height * ratio));
        Rectangle projected = new Rectangle((int) Math.round(centerX - newWidth / 2d),
            (int) Math.round(centerY - newHeight / 2d), newWidth, newHeight);
        // Do not move an off-screen prediction to an unrelated canvas edge.
        return current.viewport().contains(projected) ? projected : null;
    }
}
