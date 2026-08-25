package com.mvpitsolutions.rooftopcamera;

import java.awt.Rectangle;
import java.awt.geom.Point2D;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AttainableRouteOptimizerTest
{
    @Test
    public void choosesOneLegalPointPerMarkerDeterministically()
    {
        Rectangle[] markers = {
            new Rectangle(0, 0, 20, 20), new Rectangle(80, 0, 20, 20),
            new Rectangle(80, 80, 20, 20), new Rectangle(0, 80, 20, 20)
        };
        AttainableRouteOptimizer.Result first = AttainableRouteOptimizer.solve(markers);
        AttainableRouteOptimizer.Result second = AttainableRouteOptimizer.solve(markers);

        assertEquals(first.travel, second.travel, 0.0001);
        assertEquals(markers.length, first.points.size());
        for (int i = 0; i < markers.length; i++)
        {
            Point2D point = first.points.get(i);
            assertTrue(markers[i].contains(point) || onInclusiveEdge(markers[i], point));
        }
    }

    @Test
    public void attainableRouteStaysBetweenIndependentGapBoundAndCenterRoute()
    {
        Rectangle[] markers = {
            new Rectangle(0, 0, 1, 1),
            new Rectangle(0, 0, 101, 1),
            new Rectangle(100, 0, 1, 1)
        };
        LapOptimizer.MarkerRouteScore score = LapOptimizer.scoreCyclicMarkers(markers);

        assertTrue(score.attainableTravel > score.gapTravel * 1.5);
        assertTrue(score.attainableTravel <= score.centerTravel + 0.01);
    }

    @Test
    public void overlappingMarkersCanProduceZeroTravel()
    {
        Rectangle[] markers = {
            new Rectangle(10, 10, 50, 50), new Rectangle(20, 20, 20, 20),
            new Rectangle(15, 15, 30, 30)
        };
        assertEquals(0, AttainableRouteOptimizer.solve(markers).travel, 0.01);
    }

    private static boolean onInclusiveEdge(Rectangle rectangle, Point2D point)
    {
        return point.getX() >= rectangle.getMinX() && point.getX() <= rectangle.getMaxX()
            && point.getY() >= rectangle.getMinY() && point.getY() <= rectangle.getMaxY();
    }
}
