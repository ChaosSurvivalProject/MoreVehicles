package io.mv.common.physics;

/**
 * All tunable physics coefficients live here (no magic numbers in logic).
 * Mirrors config.yml keys; supports hot reload by rebuilding this immutable snapshot.
 * Units: 1 block = 1 m, mass in "block-mass units", time in seconds.
 */
public record PhysicsConfig(
        double gravityScale,        // default ~20 (vanilla feel); hardcore 9.81
        double airDensitySea,       // rho_0 ~= 1.2e-3
        double scaleHeight,         // atmosphere H
        double waterDensity,        // rho_w = 1.0
        double lavaDensity,         // ~2.7
        double vaporPressure,       // p_v for cavitation
        double pStatic0,            // surface pressure p_0
        double surfaceEpsilon,      // immersion epsilon ~= 0.25
        double slamCap,             // max slamming impulse
        int substepsOnEntry,        // substeps when alpha crosses 0/1
        double linearDamping,
        double angularDamping,
        String integrator,          // "semi_implicit" | "rk4"
        double dt                   // fixed 0.05 (20 TPS)
) {
    public static PhysicsConfig vanilla() {
        return new PhysicsConfig(
                20.0, 1.2e-3, 900.0,
                1.0, 2.7, 2.34e-3, 101.325,
                0.25, 40.0, 4,
                0.0, 0.0,
                "semi_implicit", 0.05);
    }

    public static PhysicsConfig hardcore() {
        return new PhysicsConfig(
                9.81, 1.225, 8500.0,
                1.0, 2.7, 2.34e-3, 101.325,
                0.25, 40.0, 4,
                0.0, 0.0,
                "rk4", 0.05);
    }

    public boolean isRk4() {
        return "rk4".equalsIgnoreCase(integrator);
    }
}
