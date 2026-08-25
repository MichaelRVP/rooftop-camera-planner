package com.mvpitsolutions.rooftopcamera;

import java.awt.Rectangle;
import java.util.List;

final class LayoutScorer
{
    private LayoutScorer() {}

    static double score(List<Rectangle> orderedClickboxes, int viewportWidth, int viewportHeight)
    {
        if (orderedClickboxes.isEmpty() || viewportWidth <= 0 || viewportHeight <= 0)
        {
            return 0;
        }

        double diagonal = Math.hypot(viewportWidth, viewportHeight);
        double travel = 0;
        double targetUsability = 0;
        for (int i = 0; i < orderedClickboxes.size(); i++)
        {
            Rectangle box = orderedClickboxes.get(i);
            double area = Math.max(0, box.width * box.height);
            targetUsability += Math.min(250.0, area / 40.0);
            if (i > 0)
            {
                Rectangle previous = orderedClickboxes.get(i - 1);
                travel += Math.hypot(box.getCenterX() - previous.getCenterX(),
                    box.getCenterY() - previous.getCenterY()) / diagonal;
            }
        }

        double visibility = orderedClickboxes.size() * 1_000.0;
        return visibility + targetUsability - travel * 120.0;
    }
}
