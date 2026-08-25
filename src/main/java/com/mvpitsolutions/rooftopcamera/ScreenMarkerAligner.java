package com.mvpitsolutions.rooftopcamera;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

final class ScreenMarkerAligner
{
    private static final int MIN_MATCHES = 2;
    private static final int MAX_CORRECTION = 160;

    private ScreenMarkerAligner() {}

    static List<Rectangle> align(List<Rectangle> saved, Map<Integer, Rectangle> live,
        int canvasWidth, int canvasHeight)
    {
        List<Integer> deltaX = new ArrayList<>();
        List<Integer> deltaY = new ArrayList<>();
        for (Map.Entry<Integer, Rectangle> entry : live.entrySet())
        {
            int index = entry.getKey();
            if (index < 0 || index >= saved.size()) continue;
            Rectangle before = saved.get(index);
            Rectangle now = entry.getValue();
            if (before == null || now == null) continue;
            deltaX.add((int) Math.round(now.getCenterX() - before.getCenterX()));
            deltaY.add((int) Math.round(now.getCenterY() - before.getCenterY()));
        }

        int dx = deltaX.size() >= MIN_MATCHES ? clamp(median(deltaX), -MAX_CORRECTION, MAX_CORRECTION) : 0;
        int dy = deltaY.size() >= MIN_MATCHES ? clamp(median(deltaY), -MAX_CORRECTION, MAX_CORRECTION) : 0;
        List<Rectangle> adjusted = new ArrayList<>(saved.size());
        for (Rectangle marker : saved)
        {
            if (marker == null)
            {
                adjusted.add(null);
                continue;
            }
            int x = clamp(marker.x + dx, 0, Math.max(0, canvasWidth - marker.width));
            int y = clamp(marker.y + dy, 0, Math.max(0, canvasHeight - marker.height));
            adjusted.add(new Rectangle(x, y, marker.width, marker.height));
        }
        return adjusted;
    }

    private static int median(List<Integer> values)
    {
        Collections.sort(values);
        int middle = values.size() / 2;
        return values.size() % 2 == 1 ? values.get(middle)
            : (int) Math.round((values.get(middle - 1) + values.get(middle)) / 2d);
    }

    private static int clamp(int value, int minimum, int maximum)
    {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
