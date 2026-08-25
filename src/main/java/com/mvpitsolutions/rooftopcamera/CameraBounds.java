package com.mvpitsolutions.rooftopcamera;

final class CameraBounds
{
    private int minPitch;
    private int maxPitch;
    private int minZoom;
    private int maxZoom;

    CameraBounds()
    {
        this(128, 2040, 128, 8192);
    }

    CameraBounds(int minPitch, int maxPitch, int minZoom, int maxZoom)
    {
        this.minPitch = minPitch;
        this.maxPitch = maxPitch;
        this.minZoom = minZoom;
        this.maxZoom = maxZoom;
    }

    int clampPitch(int value) { return clamp(value, minPitch, maxPitch); }
    int clampZoom(int value) { return clamp(value, minZoom, maxZoom); }

    boolean learnPitchLimit(int target, int actual)
    {
        if (target < actual && actual > minPitch)
        {
            minPitch = actual;
            return true;
        }
        if (target > actual && actual < maxPitch)
        {
            maxPitch = actual;
            return true;
        }
        return false;
    }

    boolean learnZoomLimit(int target, int actual)
    {
        if (target < actual && actual > minZoom)
        {
            minZoom = actual;
            return true;
        }
        if (target > actual && actual < maxZoom)
        {
            maxZoom = actual;
            return true;
        }
        return false;
    }

    String serialize()
    {
        return minPitch + "," + maxPitch + "," + minZoom + "," + maxZoom;
    }

    static CameraBounds parse(String value)
    {
        if (value == null || value.isEmpty()) return new CameraBounds();
        try
        {
            String[] parts = value.split(",");
            if (parts.length != 4) return new CameraBounds();
            int minPitch = Integer.parseInt(parts[0]);
            int maxPitch = Integer.parseInt(parts[1]);
            int minZoom = Integer.parseInt(parts[2]);
            int maxZoom = Integer.parseInt(parts[3]);
            if (minPitch >= maxPitch || minZoom >= maxZoom)
            {
                return new CameraBounds();
            }
            return new CameraBounds(minPitch, maxPitch, minZoom, maxZoom);
        }
        catch (RuntimeException ignored)
        {
            return new CameraBounds();
        }
    }

    private static int clamp(int value, int min, int max)
    {
        return Math.max(min, Math.min(max, value));
    }
}
