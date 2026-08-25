package com.mvpitsolutions.rooftopcamera;

final class CameraTarget
{
    final int yaw;
    final int pitch;
    final int zoom;

    CameraTarget(int yaw, int pitch, int zoom)
    {
        this.yaw = yaw;
        this.pitch = pitch;
        this.zoom = zoom;
    }

    String key() { return yaw + ":" + pitch + ":" + zoom; }
}
