package com.mvpitsolutions.rooftopcamera;

import java.awt.Rectangle;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class LapOptimizerTest
{
    private static Rectangle marker(int x, int y) { return new Rectangle(x, y, 10, 10); }

    @Test
    public void measuresCyclicMouseAndMarkerRoutes()
    {
        LapOptimizer optimizer = new LapOptimizer();
        optimizer.reset(3);
        assertNull(optimizer.obstacleClicked(0, 0, 0, 100, 200, 500, marker(0, 0)));
        assertNull(optimizer.obstacleClicked(1, 3, 4, 100, 200, 500, marker(3, 4)));
        assertNull(optimizer.obstacleClicked(2, 6, 8, 100, 200, 500, marker(6, 8)));
        LapOptimizer.CompletedLap lap = optimizer.obstacleClicked(0, 0, 0, 100, 200, 500, marker(0, 0));
        assertEquals(20.0, lap.mouseTravel, 0.001);
        assertEquals(20.0, lap.markerTravel, 0.001);
        assertEquals(0.0, lap.markerGap, 0.001);
        assertEquals(92.0, lap.overlapArea, 0.001);
        assertEquals(3, lap.overlappingTransitions);
        assertTrue(lap.stableCamera);
        assertTrue(optimizer.isActive());
        assertEquals(1, optimizer.getCompletedLaps());
    }

    @Test
    public void missingClickboxDoesNotProduceGeometryScore()
    {
        LapOptimizer optimizer = new LapOptimizer();
        optimizer.reset(2);
        optimizer.obstacleClicked(0, 0, 0, 100, 200, 500, marker(0, 0));
        optimizer.obstacleClicked(1, 10, 0, 100, 200, 500, null);
        LapOptimizer.CompletedLap lap = optimizer.obstacleClicked(0, 0, 0, 100, 200, 500, marker(0, 0));
        assertTrue(Double.isNaN(lap.markerTravel));
    }

    @Test
    public void outOfOrderObstacleInvalidatesLap()
    {
        LapOptimizer optimizer = new LapOptimizer();
        optimizer.reset(4);
        optimizer.obstacleClicked(0, 0, 0, 100, 200, 500, marker(0, 0));
        assertNull(optimizer.obstacleClicked(2, 10, 10, 100, 200, 500, marker(10, 10)));
        assertFalse(optimizer.isActive());
        assertEquals(0, optimizer.getCompletedLaps());
    }

    @Test
    public void cameraMovementMarksLapAsUnstable()
    {
        LapOptimizer optimizer = new LapOptimizer();
        optimizer.reset(2);
        optimizer.obstacleClicked(0, 0, 0, 100, 200, 500, marker(0, 0));
        optimizer.obstacleClicked(1, 20, 0, 150, 200, 500, marker(20, 0));
        assertFalse(optimizer.isCurrentLapStableSoFar());
        LapOptimizer.CompletedLap lap = optimizer.obstacleClicked(0, 0, 0, 150, 200, 500, marker(0, 0));
        assertFalse(lap.stableCamera);
    }
}
