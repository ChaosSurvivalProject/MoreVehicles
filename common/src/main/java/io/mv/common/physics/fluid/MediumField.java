package io.mv.common.physics.fluid;

import io.mv.common.math.MathUtil;
import io.mv.common.math.Vec3;
import io.mv.common.physics.PhysicsConfig;

/**
 * Maps the block world into a continuous fluid field (MV-SPEC 11.2).
 * The density sampler (water/lava height, currents, bubble columns) is injected so the
 * common layer stays free of MC types. Uses continuous immersion alpha = smoothstep(d/eps)
 * and interpolated surface height to avoid block-stepped jumps.
 */
public final class MediumField {
    private final PhysicsConfig cfg;
    private final DensitySampler sampler;

    /** Supplied by the server (WaterSampler) — common layer never touches blocks directly. */
    public interface DensitySampler {
        /** Surface height at (x,z), interpolated between water blocks + level. */
        double surfaceY(double x, double z);
        /** True if the column at (x,z) near y is lava (2.7 density, high viscosity). */
        boolean isLava(double x, double y, double z);
        /** True if a bubble column at (x,z) — adds upward flow. */
        boolean isBubbleColumn(double x, double y, double z);
        /** Horizontal current vector at (x,y,z), world frame. */
        Vec3 current(double x, double y, double z);
        /** Deterministic gust/wind vector at (x,y,z,tick). */
        Vec3 wind(double x, double y, double z, long tick);
    }

    public MediumField(PhysicsConfig cfg, DensitySampler sampler) {
        this.cfg = cfg;
        this.sampler = sampler;
    }

    /** Air density with altitude: rho(h) = rho_0 exp(-h/H). */
    public double airDensity(double y) {
        return cfg.airDensitySea() * Math.exp(-Math.max(0, y) / cfg.scaleHeight());
    }

    /**
     * Sample medium at a world position. Signed distance d = surfaceY - y (positive below surface).
     * alpha = smoothstep over [-eps, +eps].
     */
    public FluidMedium sample(Vec3 worldPos, long tick) {
        double surfaceY = sampler.surfaceY(worldPos.x(), worldPos.z());
        double eps = cfg.surfaceEpsilon();

        boolean lava = sampler.isLava(worldPos.x(), worldPos.y(), worldPos.z());
        boolean bubble = sampler.isBubbleColumn(worldPos.x(), worldPos.y(), worldPos.z());

        double rhoFluid = lava ? cfg.lavaDensity() : cfg.waterDensity();
        double muFluid = lava ? 8.0 : 1.0e-3; // lava: high viscosity

        // Signed distance to free surface (positive when submerged).
        double d = surfaceY - worldPos.y();
        double alpha = MathUtil.smoothstep(-eps, eps, d);

        double rhoAir = airDensity(worldPos.y());
        Vec3 wind = sampler.wind(worldPos.x(), worldPos.y(), worldPos.z(), tick);
        Vec3 current = sampler.current(worldPos.x(), worldPos.y(), worldPos.z());
        if (bubble) current = current.add(new Vec3(0, 1.2, 0)); // upward flow lifts vehicles

        Vec3 flowWorld = Vec3.lerp(wind, current, alpha);
        double rhoEff = MathUtil.lerp(rhoAir, rhoFluid, alpha);
        double muEff = MathUtil.lerp(1.8e-5, muFluid, alpha);

        // Hydrostatic pressure: p = p0 + rho g h (h = depth below surface, >=0).
        double depth = Math.max(0, d);
        double pStatic = cfg.pStatic0() + rhoEff * cfg.gravityScale() * depth;

        FluidMedium.MediumType type = lava ? FluidMedium.MediumType.LAVA
                : (bubble ? FluidMedium.MediumType.BUBBLE_COLUMN
                : (alpha > 0.5 ? FluidMedium.MediumType.WATER : FluidMedium.MediumType.AIR));

        return new FluidMedium(type, rhoEff, muEff, flowWorld, pStatic, alpha, surfaceY);
    }
}
