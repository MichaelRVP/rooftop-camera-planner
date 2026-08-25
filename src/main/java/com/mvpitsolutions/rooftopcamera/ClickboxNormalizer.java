package com.mvpitsolutions.rooftopcamera;

import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.Rectangle2D;
import java.util.ArrayDeque;
import java.util.Deque;

final class ClickboxNormalizer
{
    private static final int CELL = 4;

    private ClickboxNormalizer() {}

    /**
     * Finds a large axis-aligned rectangle whose sampled cells are all inside the real click shape.
     * This avoids treating transparent corners of an irregular polygon's bounds as clickable.
     */
    static Rectangle largestSafeRectangle(Shape shape, int canvasWidth, int canvasHeight)
    {
        if (shape == null || canvasWidth <= 0 || canvasHeight <= 0) return null;
        Rectangle clipped = shape.getBounds().intersection(new Rectangle(0, 0, canvasWidth, canvasHeight));
        if (clipped.isEmpty()) return null;

        int columns = Math.max(1, (clipped.width + CELL - 1) / CELL);
        int rows = Math.max(1, (clipped.height + CELL - 1) / CELL);
        int[] heights = new int[columns];
        Rectangle best = null;

        for (int row = 0; row < rows; row++)
        {
            int sampleY = Math.min(clipped.y + row * CELL + CELL / 2, clipped.y + clipped.height - 1);
            for (int column = 0; column < columns; column++)
            {
                int sampleX = Math.min(clipped.x + column * CELL + CELL / 2, clipped.x + clipped.width - 1);
                heights[column] = shape.contains(sampleX, sampleY) ? heights[column] + 1 : 0;
            }
            best = larger(best, largestHistogramRectangle(heights, clipped, row));
        }
        return makeContained(shape, best);
    }

    private static Rectangle largestHistogramRectangle(int[] heights, Rectangle origin, int row)
    {
        Deque<Integer> stack = new ArrayDeque<>();
        Rectangle best = null;
        for (int index = 0; index <= heights.length; index++)
        {
            int height = index == heights.length ? 0 : heights[index];
            while (!stack.isEmpty() && heights[stack.peek()] > height)
            {
                int bar = stack.pop();
                int left = stack.isEmpty() ? 0 : stack.peek() + 1;
                int widthCells = index - left;
                Rectangle candidate = new Rectangle(origin.x + left * CELL,
                    origin.y + (row - heights[bar] + 1) * CELL,
                    Math.min(widthCells * CELL, origin.x + origin.width - (origin.x + left * CELL)),
                    Math.min(heights[bar] * CELL,
                        origin.y + origin.height - (origin.y + (row - heights[bar] + 1) * CELL)));
                best = larger(best, candidate);
            }
            stack.push(index);
        }
        return best;
    }

    private static Rectangle larger(Rectangle current, Rectangle candidate)
    {
        if (candidate == null || candidate.isEmpty()) return current;
        return current == null || area(candidate) > area(current) ? candidate : current;
    }

    private static long area(Rectangle rectangle)
    {
        return (long) rectangle.width * rectangle.height;
    }

    private static Rectangle makeContained(Shape shape, Rectangle candidate)
    {
        if (candidate == null) return null;
        Rectangle safe = new Rectangle(candidate);
        while (safe.width > 2 && safe.height > 2)
        {
            Rectangle2D interior = new Rectangle2D.Double(
                safe.x + 1, safe.y + 1, safe.width - 2, safe.height - 2);
            if (shape.contains(interior)) return safe;
            safe.grow(-CELL, -CELL);
        }
        return null;
    }
}
