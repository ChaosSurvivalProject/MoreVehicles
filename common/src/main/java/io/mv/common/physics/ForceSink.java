package io.mv.common.physics;

import io.mv.common.math.Matrix3;
import io.mv.common.math.Vec3;

/**
 * A force contributor. Pure function of (state, config, dt) -> Forces; no MC types.
 * Implementors must not allocate in the hot path.
 */
@FunctionalInterface
public interface ForceSink {
    void accumulate(VehicleState state, PhysicsConfig cfg, double dt, Forces out);

    /** Convenience adapter for gravity in world frame: F = m g, applied at COM (no moment). */
    static ForceSink gravity(double mass, Vec3 gravityWorldSupplier) {
        return (state, cfg, dt, out) -> {
            // Transform world gravity into body frame for the force accumulator.
            Vec3 fb = state.orientation.rotateInverse(gravityWorldSupplier.scale(cfg.gravityScale() * mass));
            out.addForce(fb);
        };
    }

    /** Effective (added-mass) inertia: I + alpha*I_a. See AddedMass. */
    static Matrix3 effectiveInertia(Matrix3 base, double alpha, Matrix3 added) {
        Matrix3 r = base.copy();
        for (int i = 0; i < 3; i++)
            for (int j = 0; j < 3; j++)
                r.set(i, j, r.get(i, j) + alpha * added.get(i, j));
        return r;
    }
}
