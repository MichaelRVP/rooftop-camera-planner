package com.mvpitsolutions.rooftopcamera;

final class CameraReachabilityTracker
{
    private static final int INPUTS_AT_UNCHANGED_LIMIT = 5;
    private CameraTarget target;
    private int lastPitch;
    private int lastZoom;
    private int unchangedPitchInputs;
    private int unchangedZoomInputs;
    private boolean pitchInputPending;
    private boolean zoomInputPending;

    synchronized boolean observe(CameraTarget newTarget, int pitch, int zoom, CameraBounds bounds)
    {
        boolean changed = false;
        if (newTarget == null || target == null || !newTarget.key().equals(target.key()))
        {
            target = newTarget;
            unchangedPitchInputs = 0;
            unchangedZoomInputs = 0;
            pitchInputPending = false;
            zoomInputPending = false;
        }
        if (pitch != lastPitch)
        {
            unchangedPitchInputs = 0;
        }
        else if (pitchInputPending && target != null && Math.abs(target.pitch - pitch) > 4
            && ++unchangedPitchInputs >= INPUTS_AT_UNCHANGED_LIMIT)
        {
            changed |= bounds.learnPitchLimit(target.pitch, pitch);
            unchangedPitchInputs = 0;
        }
        if (zoom != lastZoom)
        {
            unchangedZoomInputs = 0;
        }
        else if (zoomInputPending && target != null && Math.abs(target.zoom - zoom) > 8
            && ++unchangedZoomInputs >= INPUTS_AT_UNCHANGED_LIMIT)
        {
            changed |= bounds.learnZoomLimit(target.zoom, zoom);
            unchangedZoomInputs = 0;
        }
        lastPitch = pitch;
        lastZoom = zoom;
        pitchInputPending = false;
        zoomInputPending = false;
        return changed;
    }

    synchronized void cameraDrag(int yaw)
    {
        if (target == null || Math.abs(RooftopCameraPlugin.signedYawDelta(yaw, target.yaw)) > 8
            || Math.abs(target.pitch - lastPitch) <= 4)
        {
            unchangedPitchInputs = 0;
            return;
        }
        pitchInputPending = true;
    }

    synchronized void zoomInput(int wheelRotation)
    {
        if (target == null || Math.abs(target.zoom - lastZoom) <= 8)
        {
            unchangedZoomInputs = 0;
            return;
        }
        boolean towardTarget = target.zoom < lastZoom ? wheelRotation < 0 : wheelRotation > 0;
        if (!towardTarget)
        {
            return;
        }
        zoomInputPending = true;
    }

    synchronized void reset()
    {
        target = null;
        unchangedPitchInputs = 0;
        unchangedZoomInputs = 0;
        pitchInputPending = false;
        zoomInputPending = false;
    }
}
