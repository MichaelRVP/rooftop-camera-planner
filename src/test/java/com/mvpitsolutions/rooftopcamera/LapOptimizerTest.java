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
        LapOptimizer.CompletedLap lap = optimizer.obstacleClicked(2, 6, 8, 100, 200, 500, marker(6, 8));
        assertEquals(10.0, lap.mouseTravel, 0.001);
        assertEquals(20.0, lap.markerTravel, 0.001);
        assertEquals(0.0, lap.markerGap, 0.001);
        assertEquals(92.0, lap.overlapArea, 0.001);
        assertEquals(3, lap.overlappingTransitions);
        assertTrue(lap.stableCamera);
        assertFalse(optimizer.isActive());
        assertEquals(1, optimizer.getCompletedLaps());
    }

    @Test
    public void missingClickboxDoesNotProduceGeometryScore()
    {
        LapOptimizer optimizer = new LapOptimizer();
        optimizer.reset(2);
        optimizer.obstacleClicked(0, 0, 0, 100, 200, 500, marker(0, 0));
        LapOptimizer.CompletedLap lap = optimizer.obstacleClicked(1, 10, 0, 100, 200, 500, null);
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
    public void automaticCameraSettlingDoesNotInvalidateLap()
    {
        LapOptimizer optimizer = new LapOptimizer();
        optimizer.reset(2);
        optimizer.obstacleClicked(0, 0, 0, 100, 200, 500, marker(0, 0));
        LapOptimizer.CompletedLap lap = optimizer.obstacleClicked(1, 20, 0, 150, 200, 500, marker(20, 0));
        assertTrue(lap.stableCamera);
    }

    @Test
    public void midLapCameraCorrectionCanRecover()
    {
        LapOptimizer optimizer = new LapOptimizer();
        optimizer.reset(2);
        optimizer.obstacleClicked(0, 0, 0, 100, 200, 500, marker(0, 0));
        optimizer.cameraAdjusted();
        LapOptimizer.CompletedLap lap = optimizer.obstacleClicked(1, 20, 0, 100, 200, 500, marker(20, 0));
        assertTrue(lap.stableCamera);
    }

    @Test
    public void forcedCameraMismatchAtObstacleRejectsLap()
    {
        LapOptimizer optimizer = new LapOptimizer();
        optimizer.reset(2);
        optimizer.obstacleClicked(0, 0, 0, 100, 200, 500, marker(0, 0), true);
        LapOptimizer.CompletedLap lap = optimizer.obstacleClicked(1, 20, 0, 100, 250, 500,
            marker(20, 0), false);
        assertFalse(lap.stableCamera);
    }

    @Test
    public void fallingOrSkippingDoesNotCompleteACalibrationLap()
    {
        LapOptimizer optimizer = new LapOptimizer();
        optimizer.reset(4);
        optimizer.obstacleClicked(0, 0, 0, 100, 200, 500, marker(0, 0));
        optimizer.obstacleClicked(1, 10, 0, 100, 200, 500, marker(10, 0));
        optimizer.obstacleClicked(3, 20, 0, 100, 200, 500, marker(20, 0));
        assertFalse(optimizer.isActive());
        assertEquals(0, optimizer.getCompletedLaps());

        assertNull(optimizer.obstacleClicked(0, 0, 0, 100, 200, 500, marker(0, 0)));
        assertTrue(optimizer.isActive());
    }

    @Test
    public void incidentalMarkPickupCannotChangeMarkerGeometryScore()
    {
        LapOptimizer optimizer = new LapOptimizer();
        optimizer.reset(3);
        optimizer.obstacleClicked(0, 0, 0, 100, 200, 500, marker(0, 0));
        optimizer.sampleMouse(500, 500, 100, 200, 500);
        optimizer.pauseMouseSampling();
        optimizer.sampleMouse(3, 4, 100, 200, 500);
        optimizer.obstacleClicked(1, 3, 4, 100, 200, 500, marker(3, 4));
        LapOptimizer.CompletedLap lap = optimizer.obstacleClicked(2, 6, 8, 100, 200, 500, marker(6, 8));

        assertEquals(20.0, lap.markerTravel, 0.001);
        assertEquals(3, lap.overlappingTransitions);
    }
}
