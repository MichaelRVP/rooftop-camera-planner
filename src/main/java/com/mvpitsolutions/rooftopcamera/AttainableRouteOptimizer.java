package com.mvpitsolutions.rooftopcamera;

import java.awt.Rectangle;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Finds one legal click point per marker that minimizes the complete cyclic route. */
final class AttainableRouteOptimizer
{
    private static final int ITERATIONS = 6000;
    private static final double BETA_ONE = 0.9;
    private static final double BETA_TWO = 0.999;
    private static final double ADAM_EPSILON = 1e-8;

    static final class Result
    {
        final double travel;
        final List<Point2D.Double> points;

        Result(double travel, List<Point2D.Double> points)
        {
            this.travel = travel;
            this.points = points;
        }
    }

    private AttainableRouteOptimizer() {}

    static Result solve(Rectangle[] markers)
    {
        if (!valid(markers))
        {
            return new Result(Double.NaN, Collections.emptyList());
        }

        Rectangle common = new Rectangle(markers[0]);
        for (int i = 1; i < markers.length; i++)
        {
            common = common.intersection(markers[i]);
        }
        if (!common.isEmpty())
        {
            List<Point2D.Double> shared = new ArrayList<>(markers.length);
            Point2D.Double point = new Point2D.Double(common.getCenterX(), common.getCenterY());
            for (int i = 0; i < markers.length; i++)
            {
                shared.add(new Point2D.Double(point.x, point.y));
            }
            return new Result(0, Collections.unmodifiableList(shared));
        }

        int count = markers.length;
        double[] x = new double[count];
        double[] y = new double[count];
        double[] firstMomentX = new double[count];
        double[] firstMomentY = new double[count];
        double[] secondMomentX = new double[count];
        double[] secondMomentY = new double[count];
        double[] bestX = new double[count];
        double[] bestY = new double[count];
        double scale = 1;
        for (int i = 0; i < count; i++)
        {
            Rectangle marker = markers[i];
            x[i] = marker.getCenterX();
            y[i] = marker.getCenterY();
            scale = Math.max(scale, Math.hypot(marker.x + marker.width, marker.y + marker.height));
        }

        double bestTravel = travel(x, y);
        double checkpointTravel = bestTravel;
        int stagnantCheckpoints = 0;
        System.arraycopy(x, 0, bestX, 0, count);
        System.arraycopy(y, 0, bestY, 0, count);
        double baseStep = Math.max(2.0, scale * 0.018);

        for (int iteration = 1; iteration <= ITERATIONS; iteration++)
        {
            double step = baseStep / (1.0 + iteration / 350.0);
            for (int i = 0; i < count; i++)
            {
                int previous = (i + count - 1) % count;
                int next = (i + 1) % count;
                double gradientX = unitX(x[i], y[i], x[previous], y[previous])
                    + unitX(x[i], y[i], x[next], y[next]);
                double gradientY = unitY(x[i], y[i], x[previous], y[previous])
                    + unitY(x[i], y[i], x[next], y[next]);

                firstMomentX[i] = BETA_ONE * firstMomentX[i] + (1 - BETA_ONE) * gradientX;
                firstMomentY[i] = BETA_ONE * firstMomentY[i] + (1 - BETA_ONE) * gradientY;
                secondMomentX[i] = BETA_TWO * secondMomentX[i] + (1 - BETA_TWO) * gradientX * gradientX;
                secondMomentY[i] = BETA_TWO * secondMomentY[i] + (1 - BETA_TWO) * gradientY * gradientY;

                double firstCorrection = 1 - Math.pow(BETA_ONE, iteration);
                double secondCorrection = 1 - Math.pow(BETA_TWO, iteration);
                double adjustedX = firstMomentX[i] / firstCorrection;
                double adjustedY = firstMomentY[i] / firstCorrection;
                double varianceX = secondMomentX[i] / secondCorrection;
                double varianceY = secondMomentY[i] / secondCorrection;
                Rectangle marker = markers[i];
                x[i] = clamp(x[i] - step * adjustedX / (Math.sqrt(varianceX) + ADAM_EPSILON),
                    marker.getMinX(), marker.getMaxX());
                y[i] = clamp(y[i] - step * adjustedY / (Math.sqrt(varianceY) + ADAM_EPSILON),
                    marker.getMinY(), marker.getMaxY());
            }

            double candidateTravel = travel(x, y);
            if (candidateTravel < bestTravel)
            {
                bestTravel = candidateTravel;
                System.arraycopy(x, 0, bestX, 0, count);
                System.arraycopy(y, 0, bestY, 0, count);
            }
            if (iteration % 50 == 0)
            {
                if (checkpointTravel - bestTravel < 1e-5)
                {
                    stagnantCheckpoints++;
                    if (stagnantCheckpoints >= 10) break;
                }
                else
                {
                    stagnantCheckpoints = 0;
                }
                checkpointTravel = bestTravel;
            }
        }

        List<Point2D.Double> points = new ArrayList<>(count);
        for (int i = 0; i < count; i++)
        {
            points.add(new Point2D.Double(bestX[i], bestY[i]));
        }
        return new Result(bestTravel, Collections.unmodifiableList(points));
    }

    private static boolean valid(Rectangle[] markers)
    {
        if (markers == null || markers.length == 0) return false;
        for (Rectangle marker : markers)
        {
            if (marker == null || marker.width < 0 || marker.height < 0) return false;
        }
        return true;
    }

    private static double travel(double[] x, double[] y)
    {
        double total = 0;
        for (int i = 0; i < x.length; i++)
        {
            int next = (i + 1) % x.length;
            total += Math.hypot(x[next] - x[i], y[next] - y[i]);
        }
        return total;
    }

    private static double unitX(double x, double y, double otherX, double otherY)
    {
        double distance = Math.hypot(x - otherX, y - otherY);
        return distance < 1e-9 ? 0 : (x - otherX) / distance;
    }

    private static double unitY(double x, double y, double otherX, double otherY)
    {
        double distance = Math.hypot(x - otherX, y - otherY);
        return distance < 1e-9 ? 0 : (y - otherY) / distance;
    }

    private static double clamp(double value, double minimum, double maximum)
    {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
