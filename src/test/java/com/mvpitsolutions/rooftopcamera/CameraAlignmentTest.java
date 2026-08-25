package com.mvpitsolutions.rooftopcamera;

import java.awt.Rectangle;
import java.util.Arrays;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
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

    @Test
    public void requestedCalibrationTargetUsesTheSameStrictTolerance()
    {
        CameraTarget target = new CameraTarget(2044, 1000, 500);
        assertTrue(RooftopCameraPlugin.cameraAligned(2, 1004, 508, target));
        assertFalse(RooftopCameraPlugin.cameraAligned(20, 1000, 500, target));
    }

    @Test
    public void markersAppearOnlyAfterCampaignAndAtWinningCamera()
    {
        ScreenMarkerLayout layout = new ScreenMarkerLayout(800, 600,
            Arrays.asList(new Rectangle(1, 2, 3, 4)));
        assertFalse(RooftopCameraPlugin.markersAvailable(false, layout, PROFILE, 2044, 1000, 500));
        assertFalse(RooftopCameraPlugin.markersAvailable(true, layout, PROFILE, 20, 1000, 500));
        assertTrue(RooftopCameraPlugin.markersAvailable(true, layout, PROFILE, 2044, 1000, 500));
    }

    @Test
    public void completedSearchKeepsWinnerAsAlignmentTarget()
    {
        CameraTarget target = RooftopCameraPlugin.effectiveAlignmentTarget(null, PROFILE);
        assertEquals(new CameraTarget(PROFILE.yaw, PROFILE.pitch, PROFILE.zoom).key(), target.key());
    }
}
