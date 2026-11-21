package io.mv.common.physics.fluid;

import io.mv.common.math.MathUtil;
import io.mv.common.math.Vec3;
import io.mv.common.physics.Forces;
import io.mv.common.physics.PhysicsConfig;

/**
 * Air/water cross-domain transition (MV-SPEC 11.9) — the most numerically fragile part.
 *
 * <pre>
 *   1) per-part medium sampling (caller loops parts) -> sum forces/moments, never whole-mode switch
 *   2) alpha-continuous interpolation of ALL medium quantities
 *   3) slamming impulse F_slam = 0.5 rho_w A_wet v_perp^2 C_s, <=2 ticks, impulse clamped
 *   4) alpha crossing 0/1 => caller splits into 4 substeps
 *   5) implicit damping suppresses surface bobbing; per-step force/moment clamps
 * </pre>
 */
public final class TransitionSolver {
    private final PhysicsConfig cfg;
    private final double slamCoeff;
    private final double forceClamp;

    public TransitionSolver(PhysicsConfig cfg, double slamCoeff, double forceClamp) {
        this.cfg = cfg;
        this.slamCoeff = slamCoeff;
        this.forceClamp = forceClamp;
    }

    /** True if a medium crossing happened (alpha near a boundary), triggering substeps. */
    public static boolean needsSubsteps(double alphaPrev, double alphaNow) {
        return (alphaPrev < 0.05 && alphaNow >= 0.05) || (alphaPrev > 0.95 && alphaNow <= 0.95);
    }

    public int substepCount() {
        return Math.max(1, cfg.substepsOnEntry());
    }

    /**
     * Slamming impact force (world -Y penetration into surface). vPerp = downward speed.
     * Very short lived; caller gates to <=2 ticks and clamps total impulse.
     */
    public double slammingForce(double areaWet, double vPerp) {
        double v = Math.max(0, vPerp);
        double f = 0.5 * cfg.waterDensity() * areaWet * v * v * slamCoeff;
        return Math.min(f, cfg.slamCap());
    }

    /** Apply a world-space slamming impulse (+Y) into the accumulator. */
    public void accumulateSlam(Vec3 worldVelocity, double surfaceY, double areaWet, Forces out) {
        double vPerp = -Math.min(0, worldVelocity.y()); // downward speed (>0 when descending)
        if (vPerp <= 0) return;
        double f = slammingForce(areaWet, vPerp);
        out.addForce(new Vec3(0, f, 0));
    }

    /** Clamp a force vector to a maximum magnitude (per-step stability). */
    public Vec3 clampForce(Vec3 f) {
        double len = f.length();
        if (len <= forceClamp || len < 1e-9) return f;
        return f.scale(forceClamp / len);
    }

    /** Implicit-like surface damping: reduces vertical velocity oscillation near alpha~0.5. */
    public Vec3 surfaceDamping(Vec3 worldVelocity, double alpha, double dt, double damping) {
        double band = 4 * alpha * (1 - alpha); // peaks at alpha=0.5
        double factor = Math.max(0, 1 - damping * band * dt);
        return worldVelocity.scale(factor);
    }

    /** Blend lift coefficient by immersion (shared by hydrofoils and wings near surface). */
    public double blendedLift(double clAir, double clWater, double alpha) {
        return MathUtil.lerp(clAir, clWater, MathUtil.clamp(alpha, 0, 1));
    }
}
