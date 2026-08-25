package com.mvpitsolutions.rooftopcamera;

final class CameraProfile
{
    final int yaw;
    final int pitch;
    final int scale;
    final double score;

    CameraProfile(int yaw, int pitch, int scale, double score)
    {
        this.yaw = yaw;
        this.pitch = pitch;
        this.scale = scale;
        this.score = score;
    }

    String serialize()
    {
        return yaw + "," + pitch + "," + scale + "," + score;
    }

    static CameraProfile parse(String value)
    {
        if (value == null || value.isEmpty())
        {
            return null;
        }
        try
        {
            String[] fields = value.split(",");
            if (fields.length != 4)
            {
                return null;
            }
            return new CameraProfile(
                Integer.parseInt(fields[0]), Integer.parseInt(fields[1]),
                Integer.parseInt(fields[2]), Double.parseDouble(fields[3]));
        }
        catch (NumberFormatException ignored)
        {
            return null;
        }
    }
}
