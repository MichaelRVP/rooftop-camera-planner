package com.mvpitsolutions.rooftopcamera;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CameraAlignmentTest
{
    private static final TravelProfile PROFILE = new TravelProfile(
        2044, 1000, 500, 1, 1, 1, 1, 1, 2);

    @Test
    public void acceptsSmallWrappedYawDifference()
    {
        assertTrue(RooftopCameraPlugin.cameraAligned(2, 1004, 508, PROFILE));
    }

    @Test
    public void rejectsAnyMaterialCameraMismatch()
    {
        assertFalse(RooftopCameraPlugin.cameraAligned(20, 1000, 500, PROFILE));
        assertFalse(RooftopCameraPlugin.cameraAligned(2044, 1005, 500, PROFILE));
        assertFalse(RooftopCameraPlugin.cameraAligned(2044, 1000, 509, PROFILE));
    }
}
