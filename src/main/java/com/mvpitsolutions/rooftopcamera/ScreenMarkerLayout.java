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
    /** Captured by the current release's live-lap verifier. */
    final boolean releaseValidated;
    final SceneProjectionFrame projectionFrame;

    ScreenMarkerLayout(int canvasWidth, int canvasHeight, List<Rectangle> markers)
    {
        this(canvasWidth, canvasHeight, markers, true, true, null);
    }

    ScreenMarkerLayout(SceneProjectionFrame frame, List<Rectangle> markers)
    {
        this(frame.canvasWidth, frame.canvasHeight, markers, true, frame.valid(), frame);
    }

    List<Rectangle> projectedTo(SceneProjectionFrame frame)
    {
        List<Rectangle> result = new ArrayList<>(markers.size());
        for (Rectangle marker : markers)
        {
            result.add(projectionFrame == null ? null : projectionFrame.project(marker, frame));
        }
        return result;
    }

    private ScreenMarkerLayout(int canvasWidth, int canvasHeight, List<Rectangle> markers,
        boolean verifiedInnerRectangles, boolean releaseValidated, SceneProjectionFrame projectionFrame)
    {
        this.canvasWidth = canvasWidth;
        this.canvasHeight = canvasHeight;
        this.verifiedInnerRectangles = verifiedInnerRectangles;
        this.releaseValidated = releaseValidated;
        this.projectionFrame = projectionFrame;
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
        StringBuilder result = new StringBuilder((projectionFrame == null ? "v3|" : "v4|")
            + canvasWidth + "," + canvasHeight + "|");
        if (projectionFrame != null)
        {
            result.append(projectionFrame.x).append(':').append(projectionFrame.y).append(':')
                .append(projectionFrame.width).append(':').append(projectionFrame.height).append(':')
                .append(projectionFrame.scale).append('|');
        }
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
            boolean v4 = sections.length == 4 && "v4".equals(sections[0]);
            boolean v3 = sections.length == 3 && "v3".equals(sections[0]);
            boolean v2 = sections.length == 3 && "v2".equals(sections[0]);
            boolean verified = v2 || v3 || v4;
            if (!verified && sections.length != 2) return null;
            String[] size = sections[verified ? 1 : 0].split(",");
            if (size.length != 2) return null;
            SceneProjectionFrame projection = null;
            if (v4)
            {
                String[] p = sections[2].split(":");
                if (p.length != 5) return null;
                projection = new SceneProjectionFrame(Integer.parseInt(size[0]), Integer.parseInt(size[1]),
                    Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]),
                    Integer.parseInt(p[3]), Integer.parseInt(p[4]));
                if (!projection.valid()) return null;
            }
            List<Rectangle> markers = new ArrayList<>();
            String encodedMarkers = sections[v4 ? 3 : verified ? 2 : 1];
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
            // v2 layouts remain available as historical search evidence, but they
            // cannot be rendered until a current live lap verifies their geometry.
            return new ScreenMarkerLayout(Integer.parseInt(size[0]), Integer.parseInt(size[1]), markers,
                verified, v3 || v4, projection);
        }
        catch (NumberFormatException ignored)
        {
            return null;
        }
    }
}
