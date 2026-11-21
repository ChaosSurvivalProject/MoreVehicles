package io.mv.common.physics.fluid;

import io.mv.common.math.MathUtil;

/**
 * Marine propeller / jet curves (MV-SPEC 11.7):
 *   J = V_A / (n D)
 *   K_T ~= K_T0 - k J   (Wageningen B simplified; decreases with advance ratio)
 *   T = rho n^2 D^4 K_T(J);  Q = rho n^2 D^5 K_Q;  eta0 = J K_T / (2 pi K_Q)
 *
 * Medium whitelist: jet = AIR only (fails in water); propeller = WATER only;
 * amphibious = both curves blended by alpha; waterjet intake must alpha > 0.8.
 */
public final class PropellerCurve {

    public enum Medium { AIR, WATER, AMPHIBIOUS }

    public record Config(double diameter, double kt0, double kSlope, double kq,
                         Medium medium, double minIntakeAlpha) {
        public static Config defaults() {
            return new Config(0.8, 0.45, 0.30, 0.04, Medium.WATER, 0.8);
        }
    }

    private final Config cfg;

    public PropellerCurve(Config cfg) {
        this.cfg = cfg;
    }

    /** Advance ratio J. */
    public double advanceRatio(double vA, double n) {
        double d = cfg.diameter();
        if (n <= 0 || d <= 0) return 0;
        return vA / (n * d);
    }

    /** Thrust coefficient K_T(J), clamped non-negative (reverse/ventilation handled by caller). */
    public double kt(double J) {
        return Math.max(0, cfg.kt0() - cfg.kSlope() * J);
    }

    /** Open-water efficiency eta0 = J K_T / (2 pi K_Q). */
    public double efficiency(double J) {
        double num = J * kt(J);
        double den = 2 * Math.PI * Math.max(1e-6, cfg.kq());
        return MathUtil.clamp(num / den, 0, 1);
    }

    /**
     * Thrust magnitude. Returns 0 when the medium is incompatible (jet in water,
     * waterjet with intake not sufficiently submerged).
     */
    public double thrust(double rho, double n, double vA, FluidMedium medium) {
        boolean water = medium.isLiquid();
        boolean canRun = switch (cfg.medium()) {
            case AIR -> !water;                         // jet fails in water
            case WATER -> water;                        // propeller needs water
            case AMPHIBIOUS -> true;                     // dual curves blended by alpha elsewhere
        };
        if (cfg.medium() == Medium.WATER && medium.alpha() < cfg.minIntakeAlpha()) return 0; // waterjet吸气
        if (!canRun) return 0;

        double d = cfg.diameter();
        double J = advanceRatio(vA, n);
        return rho * n * n * Math.pow(d, 4) * kt(J);
    }

    /** Torque magnitude Q = rho n^2 D^5 K_Q. */
    public double torque(double rho, double n) {
        return rho * n * n * Math.pow(cfg.diameter(), 5) * cfg.kq();
    }
}
