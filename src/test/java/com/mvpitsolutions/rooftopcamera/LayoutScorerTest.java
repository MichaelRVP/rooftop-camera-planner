package com.mvpitsolutions.rooftopcamera;

import java.awt.Rectangle;
import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.assertTrue;

public class LayoutScorerTest
{
    @Test
    public void closerTargetsScoreBetter()
    {
        double compact = LayoutScorer.score(Arrays.asList(
            new Rectangle(100, 100, 80, 60), new Rectangle(210, 120, 80, 60)), 1000, 700);
        double scattered = LayoutScorer.score(Arrays.asList(
            new Rectangle(20, 20, 80, 60), new Rectangle(850, 600, 80, 60)), 1000, 700);
        assertTrue(compact > scattered);
    }

    @Test
    public void visibleTargetsAreWorthMoreThanOneTarget()
    {
        double one = LayoutScorer.score(Arrays.asList(new Rectangle(100, 100, 80, 60)), 1000, 700);
        double two = LayoutScorer.score(Arrays.asList(
            new Rectangle(100, 100, 80, 60), new Rectangle(210, 120, 80, 60)), 1000, 700);
        assertTrue(two > one);
    }
}
