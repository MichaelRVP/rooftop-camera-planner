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
        int markerCount = saved.size();
        for (Integer index : live.keySet())
        {
            if (index != null && index >= 0) markerCount = Math.max(markerCount, index + 1);
        }

        List<Rectangle> adjusted = new ArrayList<>(markerCount);
        for (int index = 0; index < markerCount; index++)
        {
            Rectangle liveMarker = live.get(index);
            if (liveMarker != null)
            {
                adjusted.add(clampToCanvas(liveMarker, canvasWidth, canvasHeight));
                continue;
            }

            Rectangle marker = index < saved.size() ? saved.get(index) : null;
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

    private static Rectangle clampToCanvas(Rectangle marker, int canvasWidth, int canvasHeight)
    {
        int width = Math.max(1, Math.min(marker.width, Math.max(1, canvasWidth)));
        int height = Math.max(1, Math.min(marker.height, Math.max(1, canvasHeight)));
        int x = clamp(marker.x, 0, Math.max(0, canvasWidth - width));
        int y = clamp(marker.y, 0, Math.max(0, canvasHeight - height));
        return new Rectangle(x, y, width, height);
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
