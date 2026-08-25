package com.mvpitsolutions.rooftopcamera;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CameraReachabilityTrackerTest
{
    @Test
    public void learnsZoomBoundaryOnlyAfterRepeatedInputWithoutMovement()
    {
        CameraBounds bounds = new CameraBounds();
        CameraReachabilityTracker tracker = new CameraReachabilityTracker();
        CameraTarget target = new CameraTarget(0, 1288, 384);
        tracker.observe(target, 1288, 512, bounds);
        for (int i = 0; i < 4; i++)
        {
            tracker.zoomInput(1);
            assertFalse(tracker.observe(target, 1288, 512, bounds));
        }
        tracker.zoomInput(1);
        assertTrue(tracker.observe(target, 1288, 512, bounds));
        assertEquals(512, bounds.clampZoom(384));
    }

    @Test
    public void movementResetsStallEvidence()
    {
        CameraBounds bounds = new CameraBounds();
        CameraReachabilityTracker tracker = new CameraReachabilityTracker();
        CameraTarget target = new CameraTarget(0, 1288, 384);
        tracker.observe(target, 1288, 512, bounds);
        for (int i = 0; i < 4; i++)
        {
            tracker.zoomInput(1);
            tracker.observe(target, 1288, 512, bounds);
        }
        tracker.zoomInput(1);
        tracker.observe(target, 1288, 496, bounds);
        tracker.zoomInput(1);
        assertFalse(tracker.observe(target, 1288, 496, bounds));
        assertEquals(384, bounds.clampZoom(384));
    }

    @Test
    public void ignoresWheelInputMovingAwayFromRequestedZoom()
    {
        CameraBounds bounds = new CameraBounds();
        CameraReachabilityTracker tracker = new CameraReachabilityTracker();
        CameraTarget target = new CameraTarget(0, 1288, 384);
        tracker.observe(target, 1288, 512, bounds);
        for (int i = 0; i < 8; i++)
        {
            tracker.zoomInput(-1);
            assertFalse(tracker.observe(target, 1288, 512, bounds));
        }
        assertEquals(384, bounds.clampZoom(384));
    }

    @Test
    public void pitchBoundaryRequiresYawToBeAligned()
    {
        CameraBounds bounds = new CameraBounds();
        CameraReachabilityTracker tracker = new CameraReachabilityTracker();
        CameraTarget target = new CameraTarget(0, 1100, 512);
        tracker.observe(target, 1200, 512, bounds);
        for (int i = 0; i < 6; i++)
        {
            tracker.cameraDrag(500);
            assertFalse(tracker.observe(target, 1200, 512, bounds));
        }
        for (int i = 0; i < 4; i++)
        {
            tracker.cameraDrag(0);
            assertFalse(tracker.observe(target, 1200, 512, bounds));
        }
        tracker.cameraDrag(0);
        assertTrue(tracker.observe(target, 1200, 512, bounds));
        assertEquals(1200, bounds.clampPitch(1100));
    }

    @Test
    public void learnsUpperZoomAndPitchLimits()
    {
        CameraBounds bounds = new CameraBounds();
        assertTrue(bounds.learnZoomLimit(900, 800));
        assertTrue(bounds.learnPitchLimit(1600, 1500));
        assertEquals(800, bounds.clampZoom(900));
        assertEquals(1500, bounds.clampPitch(1600));
    }

    @Test
    public void cameraBoundsRoundTripAndRejectMalformedStorage()
    {
        CameraBounds bounds = new CameraBounds();
        bounds.learnZoomLimit(300, 512);
        bounds.learnPitchLimit(1900, 1800);
        CameraBounds restored = CameraBounds.parse(bounds.serialize());
        assertEquals(512, restored.clampZoom(300));
        assertEquals(1800, restored.clampPitch(1900));
        assertEquals(300, CameraBounds.parse("broken").clampZoom(300));
        assertEquals(300, CameraBounds.parse("1200,1800,512,512").clampZoom(300));
    }

}
