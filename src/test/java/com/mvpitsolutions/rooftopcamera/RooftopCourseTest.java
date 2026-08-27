package com.mvpitsolutions.rooftopcamera;

import java.util.HashSet;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.awt.Rectangle;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import net.runelite.api.gameval.ObjectID;

public class RooftopCourseTest
{
    @Test
    public void allNineRooftopCoursesHaveUniqueRegionsAndOrderedObstacles()
    {
        Map<RooftopCourse, Integer> expectedRouteSizes = new EnumMap<>(RooftopCourse.class);
        expectedRouteSizes.put(RooftopCourse.DRAYNOR, 7);
        expectedRouteSizes.put(RooftopCourse.AL_KHARID, 8);
        expectedRouteSizes.put(RooftopCourse.VARROCK, 9);
        expectedRouteSizes.put(RooftopCourse.CANIFIS, 8);
        expectedRouteSizes.put(RooftopCourse.FALADOR, 13);
        expectedRouteSizes.put(RooftopCourse.SEERS, 6);
        expectedRouteSizes.put(RooftopCourse.POLLNIVNEACH, 9);
        expectedRouteSizes.put(RooftopCourse.RELLEKKA, 7);
        expectedRouteSizes.put(RooftopCourse.ARDOUGNE, 7);

        assertEquals(9, RooftopCourse.values().length);
        Set<Integer> regions = new HashSet<>();
        for (RooftopCourse course : RooftopCourse.values())
        {
            assertTrue(regions.add(course.regionId));
            assertEquals(expectedRouteSizes.get(course).intValue(), course.routeSize());
            assertNotNull(RooftopCourse.forRegion(course.regionId));
            Set<Integer> objectIds = new HashSet<>();
            for (int id : course.obstacles)
            {
                assertTrue(course.contains(id));
                assertTrue("Duplicate object id in " + course, objectIds.add(id));
            }
        }
    }

    @Test
    public void everyCourseTraversesAContiguousCycleAndCompletesALap()
    {
        for (RooftopCourse course : RooftopCourse.values())
        {
            LapOptimizer optimizer = new LapOptimizer();
            optimizer.reset(course.routeSize());
            int objectId = course.obstacles[0];
            LapOptimizer.CompletedLap completed = null;
            Set<Integer> visitedSteps = new HashSet<>();

            for (int i = 0; i < course.routeSize(); i++)
            {
                int step = course.indexOf(objectId);
                assertEquals(i, step);
                assertTrue("Repeated logical step in " + course, visitedSteps.add(step));
                completed = optimizer.obstacleClicked(step, i * 10, i * 5, 100, 200, 500,
                    new Rectangle(i * 10, i * 5, 20, 20));
                objectId = course.nextAfter(objectId);
            }

            assertNotNull("Lap did not complete for " + course, completed);
            assertEquals(course.obstacles[0], objectId);
            assertEquals(1, optimizer.getCompletedLaps());
        }
    }

    @Test
    public void sequenceWrapsFromLastObstacleToFirst()
    {
        RooftopCourse course = RooftopCourse.CANIFIS;
        assertEquals(course.obstacles[0], course.nextAfter(course.obstacles[course.obstacles.length - 1]));
    }

    @Test
    public void faladorAlternateLedgesAreOneLogicalStep()
    {
        RooftopCourse course = RooftopCourse.FALADOR;
        assertEquals(13, course.routeSize());
        assertEquals(10, course.indexOf(ObjectID.ROOFTOPS_FALADOR_LEDGE_3A));
        assertEquals(10, course.indexOf(ObjectID.ROOFTOPS_FALADOR_LEDGE_3B));
        assertEquals(ObjectID.ROOFTOPS_FALADOR_LEDGE_4,
            course.nextAfter(ObjectID.ROOFTOPS_FALADOR_LEDGE_3A));
        assertEquals(ObjectID.ROOFTOPS_FALADOR_LEDGE_4,
            course.nextAfter(ObjectID.ROOFTOPS_FALADOR_LEDGE_3B));
    }

    @Test
    public void eitherFaladorLedgeCompletesTheSameLap()
    {
        assertFaladorLapCompletes(ObjectID.ROOFTOPS_FALADOR_LEDGE_3A);
        assertFaladorLapCompletes(ObjectID.ROOFTOPS_FALADOR_LEDGE_3B);
    }

    private static void assertFaladorLapCompletes(int alternateLedge)
    {
        RooftopCourse course = RooftopCourse.FALADOR;
        int[] route = {
            ObjectID.ROOFTOPS_FALADOR_WALLCLIMB, ObjectID.ROOFTOPS_FALADOR_TIGHTROPE_1,
            ObjectID.ROOFTOPS_FALADOR_HANDHOLDS_START, ObjectID.ROOFTOPS_FALADOR_GAP_1,
            ObjectID.ROOFTOPS_FALADOR_GAP_2, ObjectID.ROOFTOPS_FALADOR_TIGHTROPE_2,
            ObjectID.ROOFTOPS_FALADOR_TIGHTROPE_3, ObjectID.ROOFTOPS_FALADOR_GAP_3,
            ObjectID.ROOFTOPS_FALADOR_LEDGE_1, ObjectID.ROOFTOPS_FALADOR_LEDGE_2,
            alternateLedge, ObjectID.ROOFTOPS_FALADOR_LEDGE_4, ObjectID.ROOFTOPS_FALADOR_EDGE
        };
        LapOptimizer optimizer = new LapOptimizer();
        optimizer.reset(course.routeSize());
        LapOptimizer.CompletedLap completed = null;
        for (int i = 0; i < route.length; i++)
        {
            completed = optimizer.obstacleClicked(course.indexOf(route[i]), i * 10, i * 5,
                100, 200, 500, new Rectangle(i * 10, i * 5, 20, 20));
        }
        assertNotNull(completed);
        assertEquals(1, optimizer.getCompletedLaps());
    }
}
