package com.mvpitsolutions.rooftopcamera;

import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RooftopTrackingTest
{
    @Test
    public void spawnWithNullOldObjectAddsAcceptedObstacle()
    {
        Map<String, Integer> tracked = new HashMap<>();

        RooftopCameraPlugin.updateTracked(tracked, null, "obstacle-100", value -> 100,
            id -> id == 100, id -> 3);

        assertEquals(Integer.valueOf(3), tracked.get("obstacle-100"));
    }

    @Test
    public void replacementRemovesOldAndRejectsUnrelatedObject()
    {
        Map<String, Integer> tracked = new HashMap<>();
        tracked.put("old", 1);

        RooftopCameraPlugin.updateTracked(tracked, "old", "scenery", value -> 999,
            id -> id == 100, id -> 3);

        assertFalse(tracked.containsKey("old"));
        assertTrue(tracked.isEmpty());
    }
}
