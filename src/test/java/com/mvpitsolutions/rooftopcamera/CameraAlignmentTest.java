package com.mvpitsolutions.rooftopcamera;

import java.awt.Rectangle;
import java.util.Arrays;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CameraAlignmentTest
{
    @Test
    public void forcedCameraRecoveryUsesTheActualReachableCameraAsTheNextTarget()
    {
        CameraTarget recovered = RooftopCameraPlugin.canonicalCameraTarget(2055, 1173, 509);

        assertEquals(0, recovered.yaw);
        assertEquals(1176, recovered.pitch);
        assertEquals(512, recovered.zoom);
        assertTrue(RooftopCameraPlugin.cameraAligned(2055, 1173, 509, recovered));
    }

    @Test
    public void forcedCourseShiftAllowsTheCurrentLapToRemainValid()
    {
        CameraTarget start = new CameraTarget(512, 1200, 512);

        assertFalse(RooftopCameraPlugin.calibrationCameraAccepted(start, 650, 1376, 512));
        assertTrue(RooftopCameraPlugin.calibrationCameraAccepted(start, 650, 1376, 512, true));
    }

    @Test
    public void hiddenCourseViewIsRejectedOnlyBeforeTheLapBegins()
    {
        assertTrue(RooftopCameraPlugin.shouldRejectVisuallyUselessView(false, true, 6, 0));
        assertFalse(RooftopCameraPlugin.shouldRejectVisuallyUselessView(true, true, 6, 0));
        assertFalse(RooftopCameraPlugin.shouldRejectVisuallyUselessView(false, true, 6, 1));
        assertFalse(RooftopCameraPlugin.shouldRejectVisuallyUselessView(false, false, 6, 0));
    }

    @Test
    public void firstCalibrationTargetFollowsTheLiveCameraUntilTheLapStarts()
    {
        assertTrue(RooftopCameraPlugin.shouldFollowLiveCameraForBaseline(0, false));
        assertFalse(RooftopCameraPlugin.shouldFollowLiveCameraForBaseline(0, true));
        assertFalse(RooftopCameraPlugin.shouldFollowLiveCameraForBaseline(1, false));
    }

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
    public void firstUntargetedCalibrationLapIsAccepted()
    {
        assertTrue(RooftopCameraPlugin.calibrationCameraAccepted(null, 1000, 500, 400));
        assertTrue(RooftopCameraPlugin.calibrationCameraAccepted(
            new CameraTarget(1000, 500, 400), 1004, 504, 408));
        assertFalse(RooftopCameraPlugin.calibrationCameraAccepted(
            new CameraTarget(1000, 500, 400), 1030, 500, 400));
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
    public void historicalV2MarkerGeometryNeverRendersAsActiveGuidance()
    {
        ScreenMarkerLayout legacy = ScreenMarkerLayout.parse("v2|800,600|1:2:30:40");

        assertFalse(RooftopCameraPlugin.markersAvailable(true, legacy, PROFILE, 2044, 1000, 500));
    }

    @Test
    public void completedSearchKeepsWinnerAsAlignmentTarget()
    {
        CameraTarget target = RooftopCameraPlugin.effectiveAlignmentTarget(null, PROFILE);
        assertEquals(new CameraTarget(PROFILE.yaw, PROFILE.pitch, PROFILE.zoom).key(), target.key());
    }
}
