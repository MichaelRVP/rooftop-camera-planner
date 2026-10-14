package com.mvpitsolutions.rooftopcamera;

import java.awt.Rectangle;
import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

public class SceneProjectionFrameTest
{
    @Test public void followsViewportCenterWithoutPretendingCanvasIsAnImage()
    {
        SceneProjectionFrame saved = new SceneProjectionFrame(800,600,0,0,600,400,512);
        SceneProjectionFrame current = new SceneProjectionFrame(800,600,100,50,600,400,512);
        Rectangle marker = new Rectangle(100,100,20,20);
        assertEquals(new Rectangle(200,150,20,20), saved.project(marker,current));
        assertEquals(new Rectangle(100,100,20,20),marker);
    }

    @Test public void scalesAroundSceneCenterUsingActualPerspectiveScale()
    {
        SceneProjectionFrame saved = new SceneProjectionFrame(800,600,0,0,600,400,512);
        SceneProjectionFrame current = new SceneProjectionFrame(1200,900,0,0,1000,800,1024);
        assertEquals(new Rectangle(100,200,40,40),
            saved.project(new Rectangle(100,100,20,20),current));
    }

    @Test public void doesNotMoveOffscreenPredictionsToAnUnrelatedEdge()
    {
        SceneProjectionFrame saved = new SceneProjectionFrame(800,600,0,0,800,600,512);
        SceneProjectionFrame current = new SceneProjectionFrame(200,200,0,0,200,200,512);
        assertNull(saved.project(new Rectangle(700,500,30,30),current));
        assertNull(saved.project(new Rectangle(10,10,30,30),null));
    }

    @Test public void projectionMetadataSurvivesPersistedHistory()
    {
        SceneProjectionFrame frame = new SceneProjectionFrame(800,600,30,40,600,400,768);
        ScreenMarkerLayout layout = new ScreenMarkerLayout(frame,Arrays.asList(new Rectangle(100,100,20,20),null));
        ScreenMarkerLayout restored = ScreenMarkerLayout.parse(layout.serialize());
        assertNotNull(restored);
        assertTrue(restored.projectionFrame.sameGeometry(frame));
        assertEquals(layout.markers,restored.projectedTo(frame));
        SearchHistory history = new SearchHistory();
        CameraCandidateStats candidate = history.getOrCreate(0,384,512);
        candidate.samples=1;candidate.representativeLayout=layout;
        assertTrue(SearchHistory.parse(history.serialize()).first().representativeLayout.projectionFrame.sameGeometry(frame));
    }

    @Test public void historicalLayoutsRemainReadableButHaveNoInventedProjectionFrame()
    {
        ScreenMarkerLayout legacy=ScreenMarkerLayout.parse("v3|800,600|100:100:20:20");
        assertNotNull(legacy);
        assertNull(legacy.projectionFrame);
        assertNull(legacy.projectedTo(new SceneProjectionFrame(800,600,0,0,800,600,512)).get(0));
        assertNull(ScreenMarkerLayout.parse("v4|800,600|0:0:900:600:512|100:100:20:20"));
    }
}
