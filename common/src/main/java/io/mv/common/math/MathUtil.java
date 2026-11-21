package io.mv.common.math;

/** Shared scalar math helpers. All coefficients are configuration-driven; no magic numbers in logic. */
public final class MathUtil {
    private MathUtil() {}

    public static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    /** Positive part: max(x, 0). */
    public static double pos(double x) {
        return Math.max(0.0, x);
    }

    /** Hermite smoothstep over [edge0, edge1]; used for immersion ratio α = smoothstep(d/ε). */
    public static double smoothstep(double edge0, double edge1, double x) {
        double t = clamp((x - edge0) / (edge1 - edge0), 0, 1);
        return t * t * (3 - 2 * t);
    }

    public static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    public static double toRadians(double deg) {
        return Math.toRadians(deg);
    }

    public static double sign(double x) {
        return Math.signum(x);
    }

    /** Linear interpolation into a monotonically increasing lookup table. */
    public static double lookup(double[] xs, double[] ys, double x) {
        if (x <= xs[0]) return ys[0];
        if (x >= xs[xs.length - 1]) return ys[ys.length - 1];
        int lo = 0, hi = xs.length - 1;
        while (hi - lo > 1) {
            int mid = (lo + hi) >>> 1;
            if (xs[mid] <= x) lo = mid; else hi = mid;
        }
        double t = (x - xs[lo]) / (xs[hi] - xs[lo]);
        return lerp(ys[lo], ys[hi], t);
    }
}
