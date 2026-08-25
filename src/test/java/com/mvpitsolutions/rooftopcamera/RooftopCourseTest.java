package com.mvpitsolutions.rooftopcamera;

import java.util.HashSet;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class RooftopCourseTest
{
    @Test
    public void allNineRooftopCoursesHaveUniqueRegionsAndOrderedObstacles()
    {
        assertEquals(9, RooftopCourse.values().length);
        Set<Integer> regions = new HashSet<>();
        for (RooftopCourse course : RooftopCourse.values())
        {
            assertTrue(regions.add(course.regionId));
            assertTrue(course.obstacles.length >= 6);
            assertNotNull(RooftopCourse.forRegion(course.regionId));
            for (int id : course.obstacles)
            {
                assertTrue(course.contains(id));
            }
        }
    }

    @Test
    public void sequenceWrapsFromLastObstacleToFirst()
    {
        RooftopCourse course = RooftopCourse.CANIFIS;
        assertEquals(course.obstacles[0], course.nextAfter(course.obstacles[course.obstacles.length - 1]));
    }
}
