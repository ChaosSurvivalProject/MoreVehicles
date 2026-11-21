package io.mv.common.physics.fluid;

import io.mv.common.math.Vec3;

/**
 * Sampled fluid medium at a world position (MV-SPEC 11.1). Per-part sampling;
 * never a whole-vehicle boolean isInWater.
 *
 * @param type      medium kind
 * @param rho       effective density (already alpha-interpolated)
 * @param mu        dynamic viscosity
 * @param uFlow     flow velocity in WORLD frame (current/wind/bubble column)
 * @param pStatic   static pressure p0 + rho g h
 * @param alpha     immersion ratio [0,1] = smoothstep(d/eps)
 * @param surfaceY  free-surface height y_s (interpolated, not block-stepped)
 */
public record FluidMedium(MediumType type, double rho, double mu, Vec3 uFlow,
                          double pStatic, double alpha, double surfaceY) {

    public enum MediumType { AIR, WATER, LAVA, BUBBLE_COLUMN }

    public static FluidMedium air(double rho, Vec3 wind, double surfaceY) {
        return new FluidMedium(MediumType.AIR, rho, 1.8e-5, wind, 101.325, 0.0, surfaceY);
    }

    public boolean isLiquid() {
        return type != MediumType.AIR;
    }

    /** Blend two media by immersion ratio alpha (continuous, no step). */
    public static FluidMedium blend(FluidMedium air, FluidMedium water, double alpha) {
        double a = io.mv.common.math.MathUtil.clamp(alpha, 0, 1);
        return new FluidMedium(
                a > 0.5 ? water.type() : air.type(),
                io.mv.common.math.MathUtil.lerp(air.rho(), water.rho(), a),
                io.mv.common.math.MathUtil.lerp(air.mu(), water.mu(), a),
                Vec3.lerp(air.uFlow(), water.uFlow(), a),
                io.mv.common.math.MathUtil.lerp(air.pStatic(), water.pStatic(), a),
                a,
                water.surfaceY());
    }
}
