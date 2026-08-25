package com.mvpitsolutions.rooftopcamera;

final class AutomaticCameraShiftDetector
{
    private static final int MANUAL_SETTLE_TICKS = 25;
    private boolean initialized;
    private int baselineYaw;
    private int baselinePitch;
    private int baselineZoom;
    private int manualSettleTicks;

    boolean observe(int yaw, int pitch, int zoom, boolean manualInput, boolean calibrationLapActive)
    {
        if (!initialized || !calibrationLapActive)
        {
            setBaseline(yaw, pitch, zoom);
            manualSettleTicks = manualInput ? MANUAL_SETTLE_TICKS : 0;
            return false;
        }
        if (manualInput)
        {
            manualSettleTicks = MANUAL_SETTLE_TICKS;
        }
        if (manualSettleTicks > 0)
        {
            manualSettleTicks--;
            setBaseline(yaw, pitch, zoom);
            return false;
        }

        boolean shifted = Math.abs(RooftopCameraPlugin.signedYawDelta(baselineYaw, yaw)) > 16
            || Math.abs(pitch - baselinePitch) > 8
            || Math.abs(zoom - baselineZoom) > 16;
        if (shifted)
        {
            setBaseline(yaw, pitch, zoom);
        }
        return shifted;
    }

    void reset()
    {
        initialized = false;
        manualSettleTicks = 0;
    }

    private void setBaseline(int yaw, int pitch, int zoom)
    {
        initialized = true;
        baselineYaw = yaw;
        baselinePitch = pitch;
        baselineZoom = zoom;
    }
}
