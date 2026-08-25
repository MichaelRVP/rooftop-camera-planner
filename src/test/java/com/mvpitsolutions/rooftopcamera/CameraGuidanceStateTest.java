package com.mvpitsolutions.rooftopcamera;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CameraGuidanceStateTest
{
    @Test
    public void reportsAllRequiredCameraMovements()
    {
        CameraGuidanceState state = new CameraGuidanceState(200, -40, 100, true, 2);
        assertEquals("TURN RIGHT", state.turnLabel());
        assertEquals("TILT DOWN", state.tiltLabel());
        assertEquals("ZOOM OUT", state.zoomLabel());
        assertFalse(state.isAligned());
    }

    @Test
    public void locksInsideTheSameToleranceUsedByCalibration()
    {
        CameraGuidanceState state = new CameraGuidanceState(8, -4, 8, true, 4);
        assertTrue(state.isAligned());
        assertEquals("YAW SET", state.turnLabel());
        assertEquals("PITCH SET", state.tiltLabel());
        assertEquals("ZOOM SET", state.zoomLabel());
    }

    @Test
    public void negativeCameraDistanceDeltaMeansZoomIn()
    {
        assertEquals("ZOOM IN", new CameraGuidanceState(0, 0, -100, true, 2).zoomLabel());
    }
}
