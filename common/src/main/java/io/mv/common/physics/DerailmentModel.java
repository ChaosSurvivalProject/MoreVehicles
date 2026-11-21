package io.mv.common.physics;

import io.mv.common.math.MathUtil;

/**
 * Nadal / Wagner L/V derailment criterion (MV-SPEC 5.4).
 *
 * <pre>
 *   (L/V)_lim = (tan(delta) - mu) / (1 + mu tan(delta))
 *   L = m v^2/R cos(theta) - m g sin(e) + F_c,perp + Y_aero
 *   V = m g cos(theta) +/- dV
 *   r = (L/V) / (L/V)_lim
 *   P_derail = clamp(smoothstep(r-1)) * (1 + gamma Cd+)
 * </pre>
 *
 * Deterministic probability: seed = vehicleId ^ tick (server-side), reproducible.
 * Modes: off / probabilistic (default; r<1 never derails) / hardcore (r>=1 always).
 */
public final class DerailmentModel {

    public enum Mode { OFF, PROBABILISTIC, HARDCORE }

    public record Config(Mode mode, double flangeAngle, double friction, double nadalLimit,
                         double gammaCant, boolean wagner) {
        public static Config defaults() {
            return new Config(Mode.PROBABILISTIC, MathUtil.toRadians(70), 0.15, 0.8, 2.0, false);
        }
    }

    private final Config cfg;

    public DerailmentModel(Config cfg) {
        this.cfg = cfg;
    }

    /** Nadal limiting ratio. */
    public double limitRatio() {
        double t = Math.tan(cfg.flangeAngle());
        double mu = cfg.friction();
        return (t - mu) / (1 + mu * t);
    }

    /**
     * Lateral/vertical wheel ratio. cantDeficit = Cd (m), attackAngle = beta (rad, for Wagner).
     */
    public double lvRatio(double mass, double v, double radius, double theta, double cant,
                          double lateralForce, double verticalDelta, double attackAngle) {
        double g = 9.81; // ratio scale cancels g for pure L/V; caller uses same g as sim elsewhere
        double centrifugal = radius > 0 ? mass * v * v / radius : 0;
        double L = centrifugal * Math.cos(theta) - mass * g * Math.sin(cant) + lateralForce;
        double V = mass * g * Math.cos(theta) + verticalDelta;
        if (cfg.wagner() && Math.abs(attackAngle) > 1e-6) {
            L *= 1.0 / Math.max(0.2, Math.cos(attackAngle));
        }
        if (Math.abs(V) < 1e-9) return Double.POSITIVE_INFINITY;
        return L / V;
    }

    /** Equilibrium cant (mm); V in km/h, R in m, G = track gauge (mm). */
    public static double equilibriumCant(double gaugeMm, double vKmh, double radiusM) {
        if (radiusM <= 0) return 0;
        return gaugeMm * vKmh * vKmh / (127 * radiusM);
    }

    /** Probability in [0,1] from ratio r and positive cant deficit Cd. */
    public double derailProbability(double r, double cantDeficitMm) {
        if (cfg.mode() == Mode.OFF) return 0;
        double excess = r - 1.0;
        double p = MathUtil.smoothstep(0, 1, excess);
        double cdTerm = 1 + cfg.gammaCant() * Math.max(0, cantDeficitMm) / 1000.0;
        return MathUtil.clamp(p * cdTerm, 0, 1);
    }

    /** Deterministic decision given a seed (vehicleId ^ tick). True => derail this tick. */
    public boolean shouldDerail(double r, double cantDeficitMm, long seed) {
        if (cfg.mode() == Mode.OFF) return false;
        if (cfg.mode() == Mode.HARDCORE) return r >= 1.0;
        double p = derailProbability(r, cantDeficitMm);
        if (p <= 0) return false;
        return deterministicUnit(seed) < p;
    }

    /** E.g. r < 1 never derails in probabilistic mode. */
    public boolean canDerail() {
        return cfg.mode() != Mode.OFF;
    }

    /** Maps a long seed to [0,1) deterministically (splitmix64 finalizer). */
    public static double deterministicUnit(long seed) {
        long z = seed + 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        z = z ^ (z >>> 31);
        return (z >>> 11) / (double) (1L << 53);
    }
}
