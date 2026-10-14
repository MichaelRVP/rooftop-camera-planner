package com.mvpitsolutions.rooftopcamera;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.TileObject;
import org.junit.Test;
import static org.junit.Assert.*;

/** Exercises the same plugin result and SceneOverlay rendering path used in RuneLite. */
public class CalibratedMarkerRenderTest
{
    private static void field(Object target,String name,Object value) throws Exception
    {
        Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);f.set(target,value);
    }
    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type,Map<String,Object> values)
    {
        return (T)Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},(p,m,args)->{
            if (m.getName().equals("hashCode"))return System.identityHashCode(p);
            if (m.getName().equals("equals"))return p==args[0];
            if(values.containsKey(m.getName()))return values.get(m.getName());
            Class<?> r=m.getReturnType();
            if(r==boolean.class)return false;
            if(r==int.class)return 0;
            if(r==long.class)return 0L;
            if(r==double.class)return 0d;
            if(r==float.class)return 0f;
            if(r==short.class)return (short)0;
            if(r==byte.class)return (byte)0;
            return null;
        });
    }
    private static final class Fixture
    {
        final RooftopCameraPlugin plugin=new RooftopCameraPlugin();
        final RooftopCourse course;
        final Map<String,Object> camera=new HashMap<>();
        final List<Rectangle> saved=new ArrayList<>();
        Fixture() throws Exception { this(RooftopCourse.CANIFIS); }
        Fixture(RooftopCourse course) throws Exception
        {
            this.course=course;
            camera.put("getCanvasWidth",800);camera.put("getCanvasHeight",600);
            camera.put("getViewportWidth",800);camera.put("getViewportHeight",600);
            camera.put("getScale",512);camera.put("getCameraPitch",384);
            camera.put("getCameraPitchTarget",384);camera.put("getVarcIntValue",512);
            Client client=proxy(Client.class,camera);field(plugin,"client",client);
            field(plugin,"course",course);
            for(int i=0;i<course.routeSize();i++)
                saved.add(new Rectangle(i==0 ? 30 : 250+i*20,30+i*25,20,20));
            ScreenMarkerLayout layout=new ScreenMarkerLayout(SceneProjectionFrame.capture(client),saved);
            field(plugin,"bestMarkerLayout",layout);
            field(plugin,"bestTravelProfile",new TravelProfile(0,384,512,0,0,0,0,0,6));
            SearchHistory history=new SearchHistory();
            for(int i=0;i<6;i++){
                CameraCandidateStats candidate=history.getOrCreate(i*16,384,512);
                candidate.samples=1;candidate.representativeLayout=layout;
            }
            field(plugin,"searchHistory",history);
        }
        int logicalId(int index)
        {
            int id=course.nextAfter(-1);
            for(int i=0;i<index;i++)id=course.nextAfter(id);
            return id;
        }
        void obstacle(int index,Shape shape)
        {
            Map<String,Object> data=new HashMap<>();
            data.put("getClickbox",shape);data.put("getId",logicalId(index));
            plugin.getTracked().put(proxy(TileObject.class,data),index);
        }
    }

    @Test public void actualPluginReturnsCurrentSafeHoverInsteadOfUnusedSavedCoordinates() throws Exception
    {
        Fixture f=new Fixture();Rectangle shape=new Rectangle(400,150,32,32);f.obstacle(0,shape);
        List<Rectangle> actual=f.plugin.getScaledBestMarkers();
        assertEquals(shape,actual.get(0));
        assertEquals(f.saved.get(1),actual.get(1)); // future calibration is not translated from another roof
        assertEquals(new Rectangle(30,30,20,20),f.saved.get(0));
        f.obstacle(1,new Rectangle(700,300,24,24));
        assertEquals(f.saved.get(1),f.plugin.getScaledBestMarkers().get(1));
        field(f.plugin,"lastClickedObstacle",RooftopCourse.CANIFIS.obstacles[0]);
        assertEquals(new Rectangle(700,300,24,24),f.plugin.getScaledBestMarkers().get(1));
    }

    @Test public void renderedMarkerPixelsFollowActualHoverRegion() throws Exception
    {
        Fixture f=new Fixture();f.obstacle(0,new Rectangle(400,150,32,32));
        Map<String,Object> options=new HashMap<>();
        options.put("nextColor",Color.MAGENTA);options.put("obstacleColor",Color.GRAY);
        RooftopSceneOverlay overlay=new RooftopSceneOverlay(f.plugin,proxy(RooftopCameraConfig.class,options));
        BufferedImage image=new BufferedImage(800,600,BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics=image.createGraphics();
        try{overlay.render(graphics);}finally{graphics.dispose();}
        assertNotEquals(0,image.getRGB(405,155)>>>24);
        assertEquals(0,image.getRGB(35,35)>>>24); // stale original M1 is no longer drawn here
    }

    @Test public void renderUsesActualCameraNotOnlyItsAlreadyAlignedTarget() throws Exception
    {
        Fixture f=new Fixture();f.obstacle(0,new Rectangle(400,150,32,32));
        f.camera.put("getCameraYaw",160);
        assertTrue(f.plugin.getScaledBestMarkers().isEmpty());
        f.camera.put("getCameraYaw",0);
        assertFalse(f.plugin.getScaledBestMarkers().isEmpty());
    }

    @Test public void viewportShiftReprojectsFutureAndKeepsCurrentHoverAuthoritative() throws Exception
    {
        Fixture f=new Fixture();f.obstacle(0,new Rectangle(400,150,32,32));
        f.camera.put("getViewportXOffset",100);f.camera.put("getViewportWidth",600);
        // Canvas remains 800 wide; projected scene center remains 400.
        assertEquals(f.saved.get(1),f.plugin.getScaledBestMarkers().get(1));
        f.camera.put("getViewportXOffset",0);
        assertEquals(new Rectangle(f.saved.get(1).x-100,f.saved.get(1).y,20,20),
            f.plugin.getScaledBestMarkers().get(1));
        assertEquals(new Rectangle(400,150,32,32),f.plugin.getScaledBestMarkers().get(0));
    }

    @Test public void pendingCameraAndIncompleteCalibrationNeverDrawStaleTargets() throws Exception
    {
        Fixture f=new Fixture();f.obstacle(0,new Rectangle(400,150,32,32));
        Field trackerField=RooftopCameraPlugin.class.getDeclaredField("cameraSettleTracker");
        trackerField.setAccessible(true);CameraSettleTracker tracker=(CameraSettleTracker)trackerField.get(f.plugin);
        tracker.begin(0,384,512);
        assertTrue(f.plugin.getScaledBestMarkers().isEmpty());
        tracker.observe(0,384,512);tracker.observe(0,384,512);tracker.observe(0,384,512);
        assertFalse(f.plugin.getScaledBestMarkers().isEmpty());
        field(f.plugin,"calibrationTargetSamples",7);
        assertTrue(f.plugin.getScaledBestMarkers().isEmpty());
    }

    @Test public void everySupportedRouteAndWraparoundUsesItsActualNextHoverRegion() throws Exception
    {
        for(RooftopCourse course:RooftopCourse.values()){
            Fixture f=new Fixture(course);List<Rectangle> live=new ArrayList<>();
            for(int i=0;i<course.routeSize();i++){
                Rectangle rectangle=new Rectangle(450+(i%3)*80,250+(i/3)*50,24,24);
                live.add(rectangle);f.obstacle(i,rectangle);
            }
            for(int i=0;i<course.routeSize();i++){
                assertEquals(course.name()+" step "+i,live.get(i),f.plugin.getScaledBestMarkers().get(i));
                field(f.plugin,"lastClickedObstacle",f.logicalId(i));
            }
            assertEquals(course.name()+" wraparound",live.get(0),f.plugin.getScaledBestMarkers().get(0));
        }
    }

    @Test public void viewportChangeRejectsAnInFlightCalibrationLapInsteadOfMixingFrames() throws Exception
    {
        Fixture f=new Fixture();LapOptimizer lap=f.plugin.getLapOptimizer();
        lap.reset(f.course.routeSize());
        lap.obstacleClicked(0,400,150,0,384,512,new Rectangle(400,150,24,24));
        field(f.plugin,"calibrationProjection",new SceneProjectionFrame(800,600,0,0,800,600,512));
        f.camera.put("getCanvasWidth",900);
        f.plugin.onClientTick(new net.runelite.api.events.ClientTick());
        assertFalse(lap.isActive());
        assertEquals("LAP SKIPPED: VIEWPORT CHANGED",f.plugin.getCalibrationNote());
    }

    @Test public void noClickboxCannotBecomeAClaimedVerifiedCursorMarker() throws Exception
    {
        Fixture f=new Fixture();f.obstacle(0,null);
        java.lang.reflect.Method m=RooftopCameraPlugin.class.getDeclaredMethod("clickboxFor",int.class,int.class,int.class);
        m.setAccessible(true);
        assertNull(m.invoke(f.plugin,f.logicalId(0),400,150));
    }

    @Test public void actionableMarkerIsPaintedOnTopOfOverlappingFutureDestinations() throws Exception
    {
        Fixture f=new Fixture();Rectangle live=new Rectangle(400,150,32,32);f.obstacle(0,live);
        List<Rectangle> overlapping=new ArrayList<>(f.saved);overlapping.set(1,new Rectangle(live));
        field(f.plugin,"bestMarkerLayout",new ScreenMarkerLayout(
            new SceneProjectionFrame(800,600,0,0,800,600,512),overlapping));
        Map<String,Object> options=new HashMap<>();options.put("nextColor",Color.MAGENTA);
        options.put("obstacleColor",Color.GRAY);
        BufferedImage image=new BufferedImage(800,600,BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics=image.createGraphics();
        try{new RooftopSceneOverlay(f.plugin,proxy(RooftopCameraConfig.class,options)).render(graphics);}
        finally{graphics.dispose();}
        Color pixel=new Color(image.getRGB(405,155),true);
        assertTrue("current cyan marker must remain on top",pixel.getRed()<140 && pixel.getBlue()>140);
    }
}
