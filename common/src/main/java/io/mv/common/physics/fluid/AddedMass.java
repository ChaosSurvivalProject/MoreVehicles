package io.mv.common.physics.fluid;

import io.mv.common.math.Matrix3;
import io.mv.common.physics.PhysicsConfig;

/**
 * Added mass (MV-SPEC 11.4): accelerating a body drags surrounding fluid.
 * m_a = C_a rho V enters the INERTIA term (m+m_a)a, zero at constant velocity
 * (unlike drag). Slender body: axial C_a~0.1, transverse~1.0, sphere 0.5.
 *
 * C_a(theta) = 0.1 + 0.9 sin^2(theta)  (theta = angle between travel and body axis).
 */
public final class AddedMass {
    public static final double AXIAL = 0.1;
    public static final double TRANSVERSE = 1.0;

    public static double caAtAngle(double theta) {
        double s = Math.sin(theta);
        return AXIAL + (TRANSVERSE - AXIAL) * s * s;
    }

    /** Added mass magnitude for a given direction coefficient. */
    public static double addedMass(double ca, double rho, double volume) {
        return ca * rho * volume;
    }

    /** Effective mass = m + alpha*m_a (alpha = immersion). */
    public static double effectiveMass(double m, double ca, double rho, double volume, double alpha) {
        return m + alpha * addedMass(ca, rho, volume);
    }

    /** Effective inertia tensor = I + alpha*I_a; I_a = C_a^rot rho V L^2 (diagonal approx). */
    public static Matrix3 effectiveInertia(Matrix3 baseI, double rho, double volume,
                                           double length, double alpha, double caTransverse) {
        Matrix3 r = baseI.copy();
        double ia = caTransverse * rho * volume * length * length * alpha;
        // Rotation added-mass dominant about transverse axes (pitch/yaw), smaller about roll axis.
        r.set(0, 0, r.get(0, 0) + ia * 0.3); // roll
        r.set(1, 1, r.get(1, 1) + ia);       // pitch
        r.set(2, 2, r.get(2, 2) + ia);       // yaw
        return r;
    }
}
