package io.mv.common.physics;

import io.mv.common.math.MathUtil;

/**
 * Davis equation per car: R(v) = A + B v + C v^2 (MV-SPEC 5.3).
 * C is scaled by a train-shielding factor (head car larger, middle cars smaller).
 */
public final class DavisResistance {

    /** Per-car Davis coefficients + shielding factor. */
    public record Coeffs(double A, double B, double C) {}

    /**
     * @param v        speed (m/s)
     * @param coeff    Davis A/B/C for this car
     * @param shield   shielding multiplier applied to the quadratic term (head=1.0, middle<1.0)
     */
    public static double resistance(double v, Coeffs coeff, double shield) {
        double speed = Math.abs(v);
        return coeff.A() + coeff.B() * speed + coeff.C() * shield * speed * speed;
    }

    /**
     * Curtius-Kniffler adhesion coefficient. V in km/h.
     * dry:   mu = 0.161 + 7.5/(V+44)
     * wet:   mu = 0.130 + 7.5/(V+44)
     * Config maps rail states (rain/nether/soul sand) to dry/wet.
     */
    public static double adhesion(double speedMs, boolean wet) {
        double Vkmh = speedMs * 3.6;
        double base = wet ? 0.130 : 0.161;
        return base + 7.5 / (Vkmh + 44.0);
    }

    /** Soft positive-limit factor (1 - (v/vmax)^n)_+ avoiding hard cutoff. */
    public static double softLimit(double v, double vmax, double n) {
        if (vmax <= 0) return 1.0;
        double ratio = Math.abs(v) / vmax;
        return MathUtil.pos(1.0 - Math.pow(ratio, n));
    }
}
