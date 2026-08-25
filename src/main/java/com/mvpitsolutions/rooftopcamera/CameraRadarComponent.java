package com.mvpitsolutions.rooftopcamera;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import net.runelite.client.ui.overlay.components.LayoutableRenderableEntity;

final class CameraRadarComponent implements LayoutableRenderableEntity
{
    private static final Color SURFACE = new Color(20, 31, 41, 245);
    private static final Color SURFACE_EDGE = new Color(69, 96, 113, 210);
    private static final Color MUTED = new Color(151, 170, 182);
    private static final Color GRID = new Color(83, 111, 126, 125);
    private static final Color AQUA = new Color(68, 224, 196);
    private static final Color GOLD = new Color(255, 194, 68);
    private static final Color TRACK = new Color(46, 64, 76);

    private final Rectangle bounds = new Rectangle();
    private Point preferredLocation = new Point();
    private Dimension preferredSize = new Dimension(344, 260);
    private CameraGuidanceState state;
    private String courseName = "Rooftop course";
    private int nextObstacle = -1;
    private int routeProgress;
    private int routeTotal;
    private boolean stableLap = true;
    private double bestOverlap = Double.NaN;
    private double bestGap = Double.NaN;
    private int evidenceLaps;
    private int testedViews;
    private double lastTravel = Double.NaN;
    private String calibrationNote;

    void setState(CameraGuidanceState state)
    {
        this.state = state;
    }

    void setCalibrationNote(String calibrationNote)
    {
        this.calibrationNote = calibrationNote;
    }

    void setRouteState(String courseName, int nextObstacle, int routeProgress,
        int routeTotal, boolean stableLap)
    {
        this.courseName = courseName;
        this.nextObstacle = nextObstacle;
        this.routeProgress = routeProgress;
        this.routeTotal = routeTotal;
        this.stableLap = stableLap;
    }

    void setHistoryState(TravelProfile profile, double lastTravel)
    {
        setHistoryState(profile, lastTravel, profile == null ? 0 : 1);
    }

    void setHistoryState(TravelProfile profile, double lastTravel, int testedViews)
    {
        bestOverlap = profile == null ? Double.NaN : profile.overlappingTransitions;
        bestGap = profile == null ? Double.NaN : profile.markerGap;
        evidenceLaps = profile == null ? 0 : profile.samples;
        this.testedViews = testedViews;
        this.lastTravel = lastTravel;
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (state == null) return null;

        int x = preferredLocation.x;
        int y = preferredLocation.y;
        int width = Math.max(320, preferredSize.width);
        int height = 260;
        bounds.setBounds(x, y, width, height);

        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(SURFACE);
        g.fillRoundRect(x, y, width, height, 8, 8);
        g.setColor(SURFACE_EDGE);
        g.drawRoundRect(x, y, width - 1, height - 1, 8, 8);

        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
        g.setColor(Color.WHITE);
        g.drawString(courseName.toUpperCase(), x + 14, y + 23);
        String nextLabel = nextObstacle < 0 ? "ACQUIRING" : "NEXT  " + nextObstacle;
        int nextWidth = g.getFontMetrics().stringWidth(nextLabel);
        g.setColor(GOLD);
        g.drawString(nextLabel, x + width - nextWidth - 14, y + 23);

        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        g.setColor(state.isAligned() ? AQUA : Color.WHITE);
        String cameraStatus = state.isAligned() ? "CAMERA LOCKED"
            : state.calibration ? "ALIGN CAMERA" : "ALIGN WINNING CAMERA";
        if (!stableLap) cameraStatus = "CAMERA CHANGED - NEXT LAP COUNTS";
        if (calibrationNote != null) cameraStatus = calibrationNote;
        if (calibrationNote != null) g.setColor(GOLD);
        g.drawString(cameraStatus, x + 14, y + 44);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        g.setColor(MUTED);
        String lapLabel = "LAP  " + routeProgress + " / " + routeTotal;
        int lapWidth = g.getFontMetrics().stringWidth(lapLabel);
        g.drawString(lapLabel, x + width - lapWidth - 14, y + 44);

        drawRadar(g, x + 76, y + 104, 43);
        drawZoom(g, x + 143, y + 67, 16, 76);
        drawReadout(g, x + 181, y + 78);
        drawRouteProgress(g, x + 14, y + 161, width - 28);
        drawCalibrationProgress(g, x + 14, y + 191, width - 28);
        drawHistory(g, x + 14, y + 218, width - 28);
        g.dispose();
        return new Dimension(width, height);
    }

    private void drawRadar(Graphics2D g, int centerX, int centerY, int radius)
    {
        g.setStroke(new BasicStroke(1f));
        g.setColor(GRID);
        g.drawOval(centerX - radius, centerY - radius, radius * 2, radius * 2);
        g.drawOval(centerX - radius / 2, centerY - radius / 2, radius, radius);
        g.drawLine(centerX - radius, centerY, centerX + radius, centerY);
        g.drawLine(centerX, centerY - radius, centerX, centerY + radius);

        int targetX = centerX + scale(state.yawDelta, 512, radius - 4);
        int targetY = centerY - scale(state.pitchDelta, 256, radius - 4);
        g.setStroke(new BasicStroke(2f));
        g.setColor(state.isAligned() ? AQUA : GOLD);
        g.drawLine(centerX, centerY, targetX, targetY);
        g.fillOval(targetX - 5, targetY - 5, 10, 10);
        g.setColor(AQUA);
        g.fillOval(centerX - 3, centerY - 3, 6, 6);

        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
        g.setColor(MUTED);
        g.drawString("L", centerX - radius - 9, centerY + 3);
        g.drawString("R", centerX + radius + 4, centerY + 3);
        g.drawString("UP", centerX - 6, centerY - radius - 5);
        g.drawString("DN", centerX - 6, centerY + radius + 11);
    }

    private void drawZoom(Graphics2D g, int x, int y, int width, int height)
    {
        g.setColor(TRACK);
        g.fillRoundRect(x, y, width, height, width, width);
        int markerY = y + (height / 2) - scale(state.zoomDelta, 512, (height / 2) - 5);
        g.setColor(state.isZoomAligned() ? AQUA : GOLD);
        g.fillRoundRect(x - 3, markerY - 3, width + 6, 6, 6, 6);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
        g.setColor(MUTED);
        g.drawString("IN", x - 1, y - 5);
        g.drawString("OUT", x - 3, y + height + 11);
    }

    private void drawReadout(Graphics2D g, int x, int y)
    {
        drawStatus(g, x, y, state.turnLabel(), state.isYawAligned());
        drawStatus(g, x, y + 22, state.tiltLabel(), state.isPitchAligned());
        drawStatus(g, x, y + 44, state.zoomLabel(), state.isZoomAligned());
    }

    private void drawStatus(Graphics2D g, int x, int y, String label, boolean complete)
    {
        g.setColor(complete ? AQUA : GOLD);
        g.fillOval(x, y - 7, 7, 7);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        g.drawString(label, x + 13, y);
    }

    private void drawRouteProgress(Graphics2D g, int x, int y, int width)
    {
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
        g.setColor(MUTED);
        g.drawString("ROUTE", x, y);
        int barY = y + 6;
        g.setColor(TRACK);
        g.fillRoundRect(x, barY, width, 6, 6, 6);
        int filled = routeTotal <= 0 ? 0 : (int) Math.round(width
            * Math.min(routeProgress, routeTotal) / (double) routeTotal);
        if (filled > 0)
        {
            g.setColor(AQUA);
            g.fillRoundRect(x, barY, filled, 6, 6, 6);
        }
    }

    private void drawCalibrationProgress(Graphics2D g, int x, int y, int width)
    {
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
        g.setColor(MUTED);
        String label = state.calibration
            ? "CALIBRATION  " + Math.min(state.completedLaps, CameraSearchPlanner.MAX_VALID_LAPS)
                + " / " + CameraSearchPlanner.MAX_VALID_LAPS
            : "CALIBRATION COMPLETE";
        g.drawString(label, x, y);
        int gap = 3;
        int segmentWidth = (width - (CameraSearchPlanner.MAX_VALID_LAPS - 1) * gap)
            / CameraSearchPlanner.MAX_VALID_LAPS;
        for (int i = 0; i < CameraSearchPlanner.MAX_VALID_LAPS; i++)
        {
            g.setColor(!state.calibration || i < state.completedLaps ? AQUA : TRACK);
            g.fillRoundRect(x + i * (segmentWidth + gap), y + 6, segmentWidth, 7, 4, 4);
        }
    }

    private void drawHistory(Graphics2D g, int x, int y, int width)
    {
        g.setColor(SURFACE_EDGE);
        g.drawLine(x, y, x + width, y);
        String[] labels = {"BEST OBSERVED", "MARKER GAP", "TESTED VIEWS", "LAST LAP"};
        String[] values = {
            Double.isNaN(bestOverlap) ? "--" : String.format("%.1f / %d", bestOverlap, routeTotal),
            Double.isNaN(bestGap) ? "--" : String.format("%.0f px", bestGap),
            testedViews == 0 ? "--" : testedViews + " views",
            Double.isNaN(lastTravel) ? "--" : String.format("%.0f px", lastTravel)
        };
        int columnWidth = width / labels.length;
        for (int i = 0; i < labels.length; i++)
        {
            int center = x + i * columnWidth + columnWidth / 2;
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 8));
            g.setColor(MUTED);
            drawCentered(g, labels[i], center, y + 13);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
            g.setColor(i == 0 ? AQUA : Color.WHITE);
            drawCentered(g, values[i], center, y + 29);
        }
    }

    private static void drawCentered(Graphics2D g, String text, int centerX, int baseline)
    {
        g.drawString(text, centerX - g.getFontMetrics().stringWidth(text) / 2, baseline);
    }

    private static int scale(int value, int range, int output)
    {
        double normalized = Math.max(-1d, Math.min(1d, value / (double) range));
        return (int) Math.round(normalized * output);
    }

    @Override
    public Rectangle getBounds() { return bounds; }

    @Override
    public void setPreferredLocation(Point preferredLocation)
    {
        this.preferredLocation = preferredLocation;
    }

    @Override
    public void setPreferredSize(Dimension preferredSize)
    {
        this.preferredSize = preferredSize;
    }
}
