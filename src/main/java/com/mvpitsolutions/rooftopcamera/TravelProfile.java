package com.mvpitsolutions.rooftopcamera;

final class TravelProfile
{
    final int yaw;
    final int pitch;
    final int zoom;
    final double markerTravel;
    final double markerGap;
    final double overlappingTransitions;
    final double overlapArea;
    final double observedMouseTravel;
    final int samples;

    TravelProfile(int yaw, int pitch, int zoom, double markerTravel, double markerGap,
        double overlappingTransitions, double overlapArea, double observedMouseTravel, int samples)
    {
        this.yaw = yaw;
        this.pitch = pitch;
        this.zoom = zoom;
        this.markerTravel = markerTravel;
        this.markerGap = markerGap;
        this.overlappingTransitions = overlappingTransitions;
        this.overlapArea = overlapArea;
        this.observedMouseTravel = observedMouseTravel;
        this.samples = samples;
    }

    String serialize()
    {
        return yaw + "," + pitch + "," + zoom + "," + markerTravel + "," + markerGap + ","
            + overlappingTransitions + "," + overlapArea + "," + observedMouseTravel + "," + samples;
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
            if (fields.length != 8 && fields.length != 9)
            {
                return null;
            }
            int offset = fields.length == 9 ? 1 : 0;
            return new TravelProfile(Integer.parseInt(fields[0]), Integer.parseInt(fields[1]),
                Integer.parseInt(fields[2]), Double.parseDouble(fields[3]), Double.parseDouble(fields[4]),
                Double.parseDouble(fields[5]), offset == 1 ? Double.parseDouble(fields[6]) : 0,
                Double.parseDouble(fields[6 + offset]), Integer.parseInt(fields[7 + offset]));
        }
        catch (NumberFormatException ignored)
        {
            return null;
        }
    }
}
