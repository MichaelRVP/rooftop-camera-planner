package com.mvpitsolutions.rooftopcamera;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class ScreenMarkerLayout
{
    final int canvasWidth;
    final int canvasHeight;
    final List<Rectangle> markers;
    final boolean verifiedInnerRectangles;

    ScreenMarkerLayout(int canvasWidth, int canvasHeight, List<Rectangle> markers)
    {
        this(canvasWidth, canvasHeight, markers, true);
    }

    private ScreenMarkerLayout(int canvasWidth, int canvasHeight, List<Rectangle> markers,
        boolean verifiedInnerRectangles)
    {
        this.canvasWidth = canvasWidth;
        this.canvasHeight = canvasHeight;
        this.verifiedInnerRectangles = verifiedInnerRectangles;
        List<Rectangle> copy = new ArrayList<>(markers.size());
        for (Rectangle marker : markers)
        {
            copy.add(marker == null ? null : new Rectangle(marker));
        }
        this.markers = Collections.unmodifiableList(copy);
    }

    List<Rectangle> scaledTo(int width, int height)
    {
        if (canvasWidth <= 0 || canvasHeight <= 0 || width <= 0 || height <= 0)
        {
            return Collections.emptyList();
        }
        double scaleX = (double) width / canvasWidth;
        double scaleY = (double) height / canvasHeight;
        List<Rectangle> scaled = new ArrayList<>(markers.size());
        for (Rectangle marker : markers)
        {
            scaled.add(marker == null ? null : new Rectangle(
                (int) Math.round(marker.x * scaleX), (int) Math.round(marker.y * scaleY),
                Math.max(1, (int) Math.round(marker.width * scaleX)),
                Math.max(1, (int) Math.round(marker.height * scaleY))));
        }
        return scaled;
    }

    String serialize()
    {
        StringBuilder result = new StringBuilder("v2|" + canvasWidth + "," + canvasHeight + "|");
        for (int i = 0; i < markers.size(); i++)
        {
            if (i > 0) result.append(';');
            Rectangle marker = markers.get(i);
            if (marker != null)
            {
                result.append(marker.x).append(':').append(marker.y).append(':')
                    .append(marker.width).append(':').append(marker.height);
            }
        }
        return result.toString();
    }

    static ScreenMarkerLayout parse(String value)
    {
        if (value == null || value.isEmpty()) return null;
        try
        {
            String[] sections = value.split("\\|", -1);
            boolean verified = sections.length == 3 && "v2".equals(sections[0]);
            if (!verified && sections.length != 2) return null;
            String[] size = sections[verified ? 1 : 0].split(",");
            if (size.length != 2) return null;
            List<Rectangle> markers = new ArrayList<>();
            String encodedMarkers = sections[verified ? 2 : 1];
            if (!encodedMarkers.isEmpty())
            {
                for (String encoded : encodedMarkers.split(";", -1))
                {
                    if (encoded.isEmpty())
                    {
                        markers.add(null);
                        continue;
                    }
                    String[] fields = encoded.split(":");
                    if (fields.length != 4) return null;
                    markers.add(new Rectangle(Integer.parseInt(fields[0]), Integer.parseInt(fields[1]),
                        Integer.parseInt(fields[2]), Integer.parseInt(fields[3])));
                }
            }
            return new ScreenMarkerLayout(Integer.parseInt(size[0]), Integer.parseInt(size[1]), markers, verified);
        }
        catch (NumberFormatException ignored)
        {
            return null;
        }
    }
}
