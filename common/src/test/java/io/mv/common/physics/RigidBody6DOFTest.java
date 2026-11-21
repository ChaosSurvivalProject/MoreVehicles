package io.mv.common.physics;

import io.mv.common.math.Matrix3;
import io.mv.common.math.Quaternion;
import io.mv.common.math.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RigidBody6DOFTest {

    @Test
    void vacuumProjectileMatchesAnalyticSolution() {
        // Gravity as a constant world force sink, no drag. Apply through a custom sink.
        double m = 1.0, g = 9.81, dt = 0.05, T = 10.0;
        ForceSink gravity = (s, cfg, d, out) -> out.addForce(Vec3.Y.scale(-g * m)); // body ~ world (q=identity)
        RigidBody6DOF body = new RigidBody6DOF(m, Matrix3.diag(1, 1, 1), List.of(gravity));
        VehicleState s = new VehicleState(Vec3.ZERO, Vec3.X.scale(10), Quaternion.IDENTITY, Vec3.ZERO, 0);

        int steps = (int) (T / dt);
        for (int i = 0; i < steps; i++) body.step(s, PhysicsConfig.vanilla(), dt); // orientation stays identity

        // With identity orientation, body Y = world Y. Analytic: y = -0.5 g T^2 (semi-implicit adds ~g dt T/2 drift)
        double analyticY = -0.5 * g * T * T;
        double relErr = Math.abs(s.position.y() - analyticY) / Math.abs(analyticY);
        assertTrue(relErr < 0.01, "projectile y rel error " + relErr + " (num=" + s.position.y() + ")");
        assertEquals(100.0, s.position.x(), 0.5); // 10 m/s * 10 s, no drag
    }

    @Test
    void quaternionStaysUnitDuringIntegration() {
        RigidBody6DOF body = new RigidBody6DOF(1, Matrix3.diag(1, 1, 1), List.of());
        VehicleState s = new VehicleState(Vec3.ZERO, Vec3.ZERO,
                Quaternion.fromAxisAngle(Vec3.Y, 0.3), new Vec3(1.3, 0.7, -0.4), 0);
        for (int i = 0; i < 20_000; i++) body.step(s, PhysicsConfig.vanilla(), 0.05);
        assertTrue(s.orientation.isNormalized(1e-9));
    }

    @Test
    void symmetricBodyHasDiagonalInertia() {
        // A 3x3x3 cube of unit-density voxels is symmetric: inertia diagonal.
        io.mv.common.blueprint.VoxelSet set = new io.mv.common.blueprint.VoxelSet();
        for (int x = -1; x <= 1; x++)
            for (int y = -1; y <= 1; y++)
                for (int z = -1; z <= 1; z++)
                    set.add(new io.mv.common.blueprint.VoxelSet.Voxel(x, y, z, 1.0,
                            java.util.Set.of(), true, false, false));
        var mp = io.mv.common.blueprint.MassProperties.from(set);
        assertEquals(27.0, mp.mass, 1e-9);
        assertEquals(0.0, mp.centerOfMass.x(), 1e-9);
        assertTrue(Math.abs(mp.inertia.get(0, 1)) < 1e-9);
        assertTrue(Math.abs(mp.inertia.get(1, 2)) < 1e-9);
    }

    @Test
    void eccentricThrustProducesExpectedAngularAcceleration() {
        // Force along +X applied at offset +Y => moment = r x F = +Y x +X = -Z.
        double m = 2, dt = 0.05;
        double[] momentZ = {0};
        ForceSink eccentric = (s, cfg, d, out) -> out.addAt(Vec3.X.scale(10), new Vec3(0, 1, 0));
        RigidBody6DOF body = new RigidBody6DOF(m, Matrix3.diag(1, 1, 1), List.of(eccentric));
        VehicleState s = new VehicleState(Vec3.ZERO, Vec3.ZERO, Quaternion.IDENTITY, Vec3.ZERO, 0);
        body.step(s, PhysicsConfig.vanilla(), dt);
        // Mz = -10, Izz=1 => omega_z ~ -10*dt
        assertTrue(s.angularVelocity.z() < 0, "expected negative yaw rate, got " + s.angularVelocity.z());
        assertTrue(Math.abs(s.angularVelocity.z() + 10 * dt) < 0.5);
    }

    @Test
    void noNaNOverLongRun() {
        // Bounded, dissipative system: spring toward origin + damping config.
        // A constant-torque no-damping setup pumps energy without bound and is expected
        // to diverge — that is a property of the integrator input, not a NaN bug.
        double k = 4.0;
        // ForceSink outputs body-frame forces; convert world position into body frame
        // so the spring always points back toward the origin (otherwise rotating attitude
        // turns the spring into parametric excitation and pumps energy).
        RigidBody6DOF body = new RigidBody6DOF(3, Matrix3.diag(2, 3, 4),
                List.of((s, cfg, dt, out) -> {
                    Vec3 posBody = s.orientation.rotateInverse(s.position);
                    out.addForce(posBody.negate().scale(k));
                }));
        VehicleState s = new VehicleState(new Vec3(1, 2, 3), Vec3.ZERO,
                Quaternion.fromAxisAngle(Vec3.Y, 0.4), new Vec3(0.5, -0.3, 0.2), 0);
        PhysicsConfig damped = new PhysicsConfig(20, 1.2e-3, 900, 1.0, 2.7, 2.34e-3,
                101.325, 0.25, 40, 4, 0.4, 0.4, "semi_implicit", 0.05);
        for (int i = 0; i < 50_000; i++) body.step(s, damped, 0.05);
        assertTrue(Double.isFinite(s.position.length()));
        assertTrue(s.position.length() < 5.0, "damped spring should stay bounded, got " + s.position.length());
        assertTrue(s.orientation.isNormalized(1e-9));
    }
}
