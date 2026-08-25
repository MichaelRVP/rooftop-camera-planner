package com.mvpitsolutions.rooftopcamera;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AutomaticCameraShiftDetectorTest
{
    @Test
    public void forcedVerticalShiftFailsActiveCalibration()
    {
        AutomaticCameraShiftDetector detector = new AutomaticCameraShiftDetector();
        assertFalse(detector.observe(100, 1000, 500, false, false));
        assertFalse(detector.observe(100, 1000, 500, false, true));
        assertTrue(detector.observe(100, 1020, 500, false, true));
    }

    @Test
    public void forcedWrappedYawShiftFailsActiveCalibration()
    {
        AutomaticCameraShiftDetector detector = new AutomaticCameraShiftDetector();
        detector.observe(2040, 1000, 500, false, false);
        detector.observe(2040, 1000, 500, false, true);
        assertTrue(detector.observe(20, 1000, 500, false, true));
    }

    @Test
    public void manualCorrectionAndSettlingAreNotClassifiedAsForced()
    {
        AutomaticCameraShiftDetector detector = new AutomaticCameraShiftDetector();
        detector.observe(100, 1000, 500, false, false);
        detector.observe(100, 1000, 500, false, true);
        assertFalse(detector.observe(130, 1040, 530, true, true));
        for (int i = 0; i < 25; i++)
        {
            assertFalse(detector.observe(130 + i, 1040 + i, 530, false, true));
        }
        assertFalse(detector.observe(154, 1064, 530, false, true));
    }

    @Test
    public void movementOutsideCalibrationNeverRejectsHistory()
    {
        AutomaticCameraShiftDetector detector = new AutomaticCameraShiftDetector();
        detector.observe(100, 1000, 500, false, false);
        assertFalse(detector.observe(900, 1400, 800, false, false));
    }
}
