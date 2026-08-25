package com.mvpitsolutions.rooftopcamera;

final class CameraGuidanceState
{
    final int yawDelta;
    final int pitchDelta;
    final int zoomDelta;
    final boolean calibration;
    final int completedLaps;
    final int targetLaps;

    CameraGuidanceState(int yawDelta, int pitchDelta, int zoomDelta,
        boolean calibration, int completedLaps)
    {
        this(yawDelta, pitchDelta, zoomDelta, calibration, completedLaps,
            CameraSearchPlanner.INITIAL_VALID_LAPS);
    }

    CameraGuidanceState(int yawDelta, int pitchDelta, int zoomDelta,
        boolean calibration, int completedLaps, int targetLaps)
    {
        this.yawDelta = yawDelta;
        this.pitchDelta = pitchDelta;
        this.zoomDelta = zoomDelta;
        this.calibration = calibration;
        this.completedLaps = completedLaps;
        this.targetLaps = Math.max(1, targetLaps);
    }

    boolean isYawAligned() { return Math.abs(yawDelta) <= 8; }
    boolean isPitchAligned() { return Math.abs(pitchDelta) <= 4; }
    boolean isZoomAligned() { return Math.abs(zoomDelta) <= 8; }
    boolean isAligned() { return isYawAligned() && isPitchAligned() && isZoomAligned(); }

    String turnLabel()
    {
        return isYawAligned() ? "YAW SET" : yawDelta > 0 ? "TURN RIGHT" : "TURN LEFT";
    }

    String tiltLabel()
    {
        return isPitchAligned() ? "PITCH SET" : pitchDelta > 0 ? "TILT UP" : "TILT DOWN";
    }

    String zoomLabel()
    {
        return isZoomAligned() ? "ZOOM SET" : zoomDelta > 0 ? "ZOOM IN" : "ZOOM OUT";
    }
}
