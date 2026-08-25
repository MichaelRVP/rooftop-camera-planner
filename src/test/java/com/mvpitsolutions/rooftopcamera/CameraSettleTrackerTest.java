package com.mvpitsolutions.rooftopcamera;

import org.junit.Test;
import static org.junit.Assert.*;

public class CameraSettleTrackerTest
{
    @Test
    public void requiresThreeStableTicksAndRestartsAfterMovement()
    {
        CameraSettleTracker tracker = new CameraSettleTracker();
        tracker.begin(100, 200, 300);
        assertFalse(tracker.observe(101, 200, 300));
        assertFalse(tracker.observe(102, 201, 300));
        assertFalse(tracker.observe(120, 201, 300));
        assertFalse(tracker.observe(120, 201, 300));
        assertFalse(tracker.observe(120, 201, 301));
        assertTrue(tracker.observe(120, 201, 301));
        assertFalse(tracker.isPending());
    }
}
