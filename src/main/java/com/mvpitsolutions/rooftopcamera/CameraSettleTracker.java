package com.mvpitsolutions.rooftopcamera;

final class CameraSettleTracker
{
    private static final int REQUIRED_STABLE_TICKS = 3;
    private boolean pending;
    private int stableTicks;
    private int yaw;
    private int pitch;
    private int zoom;

    void begin(int yaw, int pitch, int zoom)
    {
        pending = true;
        stableTicks = 0;
        this.yaw = yaw;
        this.pitch = pitch;
        this.zoom = zoom;
    }

    boolean observe(int yaw, int pitch, int zoom)
    {
        if (!pending) return false;
        boolean stable = Math.abs(RooftopCameraPlugin.signedYawDelta(this.yaw, yaw)) <= 2
            && Math.abs(this.pitch - pitch) <= 2 && Math.abs(this.zoom - zoom) <= 2;
        stableTicks = stable ? stableTicks + 1 : 0;
        this.yaw = yaw;
        this.pitch = pitch;
        this.zoom = zoom;
        if (stableTicks < REQUIRED_STABLE_TICKS) return false;
        pending = false;
        return true;
    }

    boolean isPending() { return pending; }

    void reset()
    {
        pending = false;
        stableTicks = 0;
    }
}
