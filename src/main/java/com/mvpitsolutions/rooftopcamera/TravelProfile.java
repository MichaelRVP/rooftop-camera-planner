package com.mvpitsolutions.rooftopcamera;

final class TravelProfile
{
    final int yaw;
    final int pitch;
    final int zoom;
    final double markerTravel;
    final double markerGap;
    final double overlappingTransitions;
    final double observedMouseTravel;
    final int samples;

    TravelProfile(int yaw, int pitch, int zoom, double markerTravel, double markerGap,
        double overlappingTransitions, double observedMouseTravel, int samples)
    {
        this.yaw = yaw;
        this.pitch = pitch;
        this.zoom = zoom;
        this.markerTravel = markerTravel;
        this.markerGap = markerGap;
        this.overlappingTransitions = overlappingTransitions;
        this.observedMouseTravel = observedMouseTravel;
        this.samples = samples;
    }

    String serialize()
    {
        return yaw + "," + pitch + "," + zoom + "," + markerTravel + "," + markerGap + ","
            + overlappingTransitions + "," + observedMouseTravel + "," + samples;
    }

    static TravelProfile parse(String value)
    {
        if (value == null || value.isEmpty())
        {
            return null;
        }
        try
        {
            String[] fields = value.split(",");
            if (fields.length != 8)
            {
                return null;
            }
            return new TravelProfile(Integer.parseInt(fields[0]), Integer.parseInt(fields[1]),
                Integer.parseInt(fields[2]), Double.parseDouble(fields[3]), Double.parseDouble(fields[4]),
                Double.parseDouble(fields[5]), Double.parseDouble(fields[6]), Integer.parseInt(fields[7]));
        }
        catch (NumberFormatException ignored)
        {
            return null;
        }
    }
}
