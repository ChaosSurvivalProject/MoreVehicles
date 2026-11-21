package io.mv.common.physics.fluid;

import io.mv.common.math.MathUtil;

/**
 * Total resistance coefficient (MV-SPEC 11.5):
 *   C_T = (1+k) C_F(Re) + C_form + C_W(Fr) + C_A
 *   C_F = 0.075 / (log10(Re) - 2)^2   (ITTC-1957)
 *   Fr = V / sqrt(g L_wl)
 *
 * Wave resistance C_W uses a configurable LUT with peaks at Fr~0.30 and ~0.50,
 * steep rise after ~0.40 (displacement hull limit), drop >0.7 (planing).
 * At full submersion (no free surface) C_W -> 0.
 */
public final class WaveResistance {

    public record Config(double formFactor, double formCoeff, double roughness, double waterlineLength) {
        public static Config defaults() {
            return new Config(0.10, 0.05, 0.0004, 4.0);
        }
    }

    // Fr breakpoints and wave-resistance curve (peaks 0.30 & 0.50, drop after 0.7).
    private static final double[] FR = {
            0.0, 0.1, 0.2, 0.28, 0.32, 0.38, 0.42, 0.48, 0.52, 0.6, 0.7, 0.85, 1.0
    };
    private static final double[] CW = {
            0.0, 0.0002, 0.0015, 0.0045, 0.0052, 0.0028, 0.0060, 0.0085, 0.0078,
            0.006, 0.003, 0.0012, 0.0008
    };

    private final Config cfg;

    public WaveResistance(Config cfg) {
        this.cfg = cfg;
    }

    /** Froude number. */
    public double froude(double v, double g) {
        double L = Math.max(0.1, cfg.waterlineLength());
        return v / Math.sqrt(g * L);
    }

    /** ITTC-1957 flat-plate friction line. */
    public static double cf(double re) {
        if (re <= 0) return 0;
        double l = Math.log10(Math.max(10, re)) - 2;
        return 0.075 / (l * l);
    }

    /** Wave resistance coefficient at a Froude number (free-surface effect). */
    public double cw(double fr, double freeSurfaceFactor) {
        return MathUtil.lookup(FR, CW, fr) * MathUtil.clamp(freeSurfaceFactor, 0, 1);
    }

    /** Total C_T. */
    public double ct(double re, double fr, double freeSurfaceFactor) {
        return (1 + cfg.formFactor()) * cf(re) + cfg.formCoeff()
                + cw(fr, freeSurfaceFactor) + cfg.roughness();
    }

    /** Resistance force magnitude: R = 0.5 rho v^2 S C_T. */
    public double resistance(double rho, double v, double area, double re, double fr, double freeSurfaceFactor) {
        return 0.5 * rho * v * v * area * ct(re, fr, freeSurfaceFactor);
    }
}
