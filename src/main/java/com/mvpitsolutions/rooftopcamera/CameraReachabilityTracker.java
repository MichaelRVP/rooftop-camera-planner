package com.mvpitsolutions.rooftopcamera;

final class CameraReachabilityTracker
{
    private static final int INPUTS_AT_UNCHANGED_LIMIT = 5;
    private static final int DRAGS_AT_UNREACHABLE_YAW = 12;
    private CameraTarget target;
    private int lastYaw;
    private int lastPitch;
    private int lastZoom;
    private int unchangedYawInputs;
    private int unchangedPitchInputs;
    private int unchangedZoomInputs;
    private boolean yawInputPending;
    private boolean pitchInputPending;
    private boolean zoomInputPending;
    private boolean targetUnreachable;

    synchronized boolean observe(CameraTarget newTarget, int pitch, int zoom, CameraBounds bounds)
    {
        return observe(newTarget, newTarget == null ? 0 : newTarget.yaw, pitch, zoom, bounds);
    }

    synchronized boolean observe(CameraTarget newTarget, int yaw, int pitch, int zoom, CameraBounds bounds)
    {
        boolean changed = false;
        if (newTarget == null || target == null || !newTarget.key().equals(target.key()))
        {
            target = newTarget;
            unchangedYawInputs = 0;
            unchangedPitchInputs = 0;
            unchangedZoomInputs = 0;
            yawInputPending = false;
            pitchInputPending = false;
            zoomInputPending = false;
            targetUnreachable = false;
        }
        if (yaw != lastYaw)
        {
            unchangedYawInputs = 0;
        }
        else if (yawInputPending && target != null
            && Math.abs(RooftopCameraPlugin.signedYawDelta(yaw, target.yaw)) > 8
            && ++unchangedYawInputs >= DRAGS_AT_UNREACHABLE_YAW)
        {
            targetUnreachable = true;
            unchangedYawInputs = 0;
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
        lastYaw = yaw;
        lastPitch = pitch;
        lastZoom = zoom;
        yawInputPending = false;
        pitchInputPending = false;
        zoomInputPending = false;
        return changed;
    }

    synchronized void cameraDrag(int yaw)
    {
        if (target == null)
        {
            return;
        }
        if (Math.abs(RooftopCameraPlugin.signedYawDelta(yaw, target.yaw)) > 8)
        {
            yawInputPending = true;
        }
        else
        {
            unchangedYawInputs = 0;
        }
        if (Math.abs(RooftopCameraPlugin.signedYawDelta(yaw, target.yaw)) > 8
            || Math.abs(target.pitch - lastPitch) <= 4)
        {
            unchangedPitchInputs = 0;
            return;
        }
        pitchInputPending = true;
    }

    synchronized boolean consumeTargetUnreachable()
    {
        boolean result = targetUnreachable;
        targetUnreachable = false;
        return result;
    }

    synchronized void zoomInput(int wheelRotation)
    {
        if (target == null || Math.abs(target.zoom - lastZoom) <= 8)
        {
            unchangedZoomInputs = 0;
            return;
        }
        boolean towardTarget = target.zoom < lastZoom ? wheelRotation > 0 : wheelRotation < 0;
        if (!towardTarget)
        {
            return;
        }
        zoomInputPending = true;
    }

    synchronized void reset()
    {
        target = null;
        unchangedYawInputs = 0;
        unchangedPitchInputs = 0;
        unchangedZoomInputs = 0;
        yawInputPending = false;
        pitchInputPending = false;
        zoomInputPending = false;
        targetUnreachable = false;
    }
}
