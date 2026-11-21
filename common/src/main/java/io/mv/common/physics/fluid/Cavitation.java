package io.mv.common.physics.fluid;

import io.mv.common.math.MathUtil;
import io.mv.common.physics.PhysicsConfig;

/**
 * Cavitation number and continuous degradation (MV-SPEC 11.6):
 *   sigma = (p_inf - p_v) / (0.5 rho V^2),  p_inf = p0 + rho_w g h
 *   sigma < sigma_crit => CL drops, CD rises, thrust falls — all continuous/differentiable.
 * Deeper submersion raises p_inf, relieving cavitation (real reason high-speed subs dive).
 */
public final class Cavitation {
    public static final double SIGMA_CRIT = 1.5;

    /** Cavitation number from relative speed and depth. */
    public static double sigma(PhysicsConfig cfg, double vRel, double depth) {
        double pInf = cfg.pStatic0() + cfg.waterDensity() * cfg.gravityScale() * Math.max(0, depth);
        double dyn = 0.5 * cfg.waterDensity() * vRel * vRel;
        if (dyn < 1e-9) return Double.POSITIVE_INFINITY;
        return (pInf - cfg.vaporPressure()) / dyn;
    }

    /** Continuous lift multiplier: 1 well above sigma_crit, -> ~0.2 below. */
    public static double liftFactor(double sigma, double sigmaCrit) {
        double x = MathUtil.clamp((sigma - sigmaCrit * 0.3) / (sigmaCrit - sigmaCrit * 0.3), 0, 1);
        return 0.2 + 0.8 * MathUtil.smoothstep(0, 1, x);
    }

    /** Continuous drag increment as cavitation onset approaches. */
    public static double dragIncrement(double sigma, double sigmaCrit) {
        double x = MathUtil.clamp((sigmaCrit - sigma) / sigmaCrit, 0, 1);
        return MathUtil.smoothstep(0, 1, x);
    }

    /** True when the multiplicative lift has dropped appreciably (drives the event/bubbles). */
    public static boolean active(double sigma, double sigmaCrit) {
        return sigma < sigmaCrit;
    }
}
