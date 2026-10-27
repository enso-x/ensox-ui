package com.ensox.base;

public final class CubicBezier {
    // Tuning constants (similar to popular bezier-easing implementations)
    private static final int NEWTON_ITERATIONS = 8;
    private static final double NEWTON_MIN_SLOPE = 1e-6;
    private static final double SUBDIVISION_PRECISION = 1e-7;
    private static final int SUBDIVISION_MAX_ITERATIONS = 20;

    private static final int SPLINE_TABLE_SIZE = 11;
    private static final double SAMPLE_STEP = 1.0 / (SPLINE_TABLE_SIZE - 1.0);

    private final double x1, y1, x2, y2;
    private final double[] sampleValues; // precomputed x(u) samples

    public CubicBezier(double x1, double y1, double x2, double y2) {
        this.x1 = x1;
        this.y1 = y1;
        this.x2 = x2;
        this.y2 = y2;

        this.sampleValues = new double[SPLINE_TABLE_SIZE];
        for (int i = 0; i < SPLINE_TABLE_SIZE; i++) {
            double u = i * SAMPLE_STEP;
            sampleValues[i] = calcBezier(u, this.x1, this.x2); // x(u)
        }
    }

    /**
     * Evaluate y for given t (t is x in [0..1], like CSS cubic-bezier timing function).
     */
    public double ease(double t) {
        if (t <= 0.0) return 0.0;
        if (t >= 1.0) return 1.0;

        double u = getUForX(t);
        return calcBezier(u, y1, y2);
    }

    /**
     * Static convenience method: compute eased y at x=t for cubic-bezier(x1,y1,x2,y2).
     */
    public static double ease(double x1, double y1, double x2, double y2, double t) {
        return new CubicBezier(x1, y1, x2, y2).ease(t);
    }

    // ----------------- internals -----------------

    private double getUForX(double x) {
        // 1) Find the interval in the precomputed table.
        int intervalStart = 0;
        int currentSample = 1;
        int lastSample = SPLINE_TABLE_SIZE - 1;

        while (currentSample != lastSample && sampleValues[currentSample] <= x) {
            intervalStart = currentSample;
            currentSample++;
        }
        currentSample--;

        // 2) Linear interpolate to get an initial guess for u.
        double xStart = sampleValues[currentSample];
        double xEnd = sampleValues[currentSample + 1];
        double dist = (x - xStart) / (xEnd - xStart);

        double uGuess = (currentSample + dist) * SAMPLE_STEP;

        // 3) Refine with Newton-Raphson if slope is ok, otherwise bisection.
        double slope = getSlope(uGuess, x1, x2);
        if (slope >= NEWTON_MIN_SLOPE) {
            return newtonRaphsonIterate(x, uGuess);
        }

        if (slope == 0.0) {
            // Very flat: bisection around the interval is safer.
            double u0 = currentSample * SAMPLE_STEP;
            double u1 = (currentSample + 1) * SAMPLE_STEP;
            return binarySubdivide(x, u0, u1);
        }

        // Low slope but non-zero: still safer to bisect in the local interval.
        double u0 = currentSample * SAMPLE_STEP;
        double u1 = (currentSample + 1) * SAMPLE_STEP;
        return binarySubdivide(x, u0, u1);
    }

    private double newtonRaphsonIterate(double x, double u) {
        for (int i = 0; i < NEWTON_ITERATIONS; i++) {
            double xEstimate = calcBezier(u, x1, x2) - x;
            double slope = getSlope(u, x1, x2);
            if (Math.abs(slope) < NEWTON_MIN_SLOPE) {
                break;
            }
            u -= xEstimate / slope;
        }
        // Keep in bounds (helps stability)
        if (u < 0.0) return 0.0;
        if (u > 1.0) return 1.0;
        return u;
    }

    private double binarySubdivide(double x, double a, double b) {
        double currentX;
        double currentU;
        int i = 0;

        do {
            currentU = a + (b - a) * 0.5;
            currentX = calcBezier(currentU, x1, x2) - x;

            if (currentX > 0.0) {
                b = currentU;
            } else {
                a = currentU;
            }
            i++;
        } while (Math.abs(currentX) > SUBDIVISION_PRECISION && i < SUBDIVISION_MAX_ITERATIONS);

        return currentU;
    }

    // Cubic Bezier with endpoints (0,0) and (1,1), control points (a1, ?), (a2, ?)
    private static double calcBezier(double u, double a1, double a2) {
        // ((A*u + B)*u + C)*u
        double A = 1.0 - 3.0 * a2 + 3.0 * a1;
        double B = 3.0 * a2 - 6.0 * a1;
        double C = 3.0 * a1;
        return ((A * u + B) * u + C) * u;
    }

    private static double getSlope(double u, double a1, double a2) {
        // derivative of calcBezier
        double A = 1.0 - 3.0 * a2 + 3.0 * a1;
        double B = 3.0 * a2 - 6.0 * a1;
        double C = 3.0 * a1;
        return 3.0 * A * u * u + 2.0 * B * u + C;
    }
}
