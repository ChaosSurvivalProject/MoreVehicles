package io.mv.common.physics;

import io.mv.common.math.Matrix3;
import io.mv.common.math.Quaternion;
import io.mv.common.math.Vec3;

import java.util.List;

/**
 * 6-DOF rigid-body integrator (MV-SPEC 4.1). State = [r, v_b, q, omega, E].
 *
 * <pre>
 *   r_dot      = R(q) v_b
 *   m v_b_dot  = F - omega × (m v_b)
 *   I omega_dot= M - omega × (I omega)
 *   q_dot      = 0.5 q ⊗ (0, omega)
 * </pre>
 *
 * Pure Java, zero MC dependencies. Supports semi-implicit Euler (default) and RK4
 * (hardcore). Quaternion is normalized every step. Off-diagonal inertia terms are
 * preserved and inverted via Matrix3.invert().
 */
public final class RigidBody6DOF {
    private final double mass;
    private final Matrix3 inertiaBody;      // I (body frame)
    private final Matrix3 inertiaInv;       // I^-1
    private final List<ForceSink> sinks;

    // Scratch: reused across steps so the hot path allocates nothing.
    private final Quaternion qOmega = new Quaternion(0, 0, 0, 1);
    private final Forces fScratch = new Forces();

    public RigidBody6DOF(double mass, Matrix3 inertiaBody, List<ForceSink> sinks) {
        if (mass <= 0) throw new IllegalArgumentException("mass must be > 0");
        this.mass = mass;
        this.inertiaBody = inertiaBody.symmetric();
        this.inertiaInv = this.inertiaBody.invert();
        this.sinks = List.copyOf(sinks);
    }

    public double mass() { return mass; }
    public Matrix3 inertia() { return inertiaBody; }

    /** Accumulate forces from all sinks into a reusable accumulator. */
    private Forces accumulate(VehicleState s, PhysicsConfig cfg, double dt) {
        fScratch.reset();
        for (ForceSink sink : sinks) sink.accumulate(s, cfg, dt, fScratch);
        return fScratch;
    }

    /** State derivative, materialized into a lightweight DState (reused via caller-provided out). */
    private static final class DState {
        Vec3 dr, dv, dOmega;
        Quaternion dq;
        double dE;
    }

    private final DState dScratch = new DState();

    private void derivative(VehicleState s, PhysicsConfig cfg, double dt, Forces forces, DState out) {
        Vec3 Fb = forces.force;
        Vec3 Mb = forces.moment;

        // dv_b = (F - omega × (m v_b)) / m
        Vec3 coriolisLin = s.angularVelocity.cross(s.velocity.scale(mass));
        Vec3 dv = Fb.sub(coriolisLin).scale(1.0 / mass);

        // domega = I^-1 (M - omega × (I omega))
        Vec3 Iomega = inertiaBody.mul(s.angularVelocity);
        Vec3 coriolisAng = s.angularVelocity.cross(Iomega);
        Vec3 dOmega = inertiaInv.mul(Mb.sub(coriolisAng));

        // dq = 0.5 q ⊗ (0, omega)
        Quaternion omegaQ = new Quaternion(
                s.angularVelocity.x(), s.angularVelocity.y(), s.angularVelocity.z(), 0);
        Quaternion dq = s.orientation.mul(omegaQ);
        double h = 0.5;
        dq = new Quaternion(dq.x() * h, dq.y() * h, dq.z() * h, dq.w() * h);

        // dr = R(q) v_b
        Vec3 dr = s.orientation.rotate(s.velocity);

        out.dr = dr;
        out.dv = dv;
        out.dOmega = dOmega;
        out.dq = dq;
        out.dE = forces.energyDelta;
    }

    /** Semi-implicit (symplectic) Euler: advance velocity first, then position/orientation. */
    private void stepEuler(VehicleState s, PhysicsConfig cfg, double dt) {
        Forces f = accumulate(s, cfg, dt);
        derivative(s, cfg, dt, f, dScratch);

        s.velocity = s.velocity.add(dScratch.dv.scale(dt));
        s.angularVelocity = s.angularVelocity.add(dScratch.dOmega.scale(dt));
        s.position = s.position.add(dScratch.dr.scale(dt));

        // q integration uses the OLD orientation's derivative but new omega: q += 0.5 q⊗(0,omega_new) dt
        Quaternion omegaQ = new Quaternion(
                s.angularVelocity.x(), s.angularVelocity.y(), s.angularVelocity.z(), 0);
        Quaternion qd = s.orientation.mul(omegaQ);
        double h = 0.5 * dt;
        s.orientation = new Quaternion(
                s.orientation.x() + qd.x() * h,
                s.orientation.y() + qd.y() * h,
                s.orientation.z() + qd.z() * h,
                s.orientation.w() + qd.w() * h).normalized();

        s.energy += dScratch.dE * dt;
        applyDamping(s, dt, cfg);
    }

    /** 4th-order Runge-Kutta (hardcore mode). Forces are re-evaluated at each stage. */
    private void stepRk4(VehicleState s, PhysicsConfig cfg, double dt) {
        Forces f1 = accumulate(s, cfg, dt);
        Stage k1 = stage(s, cfg, dt, f1);

        VehicleState s2 = shifted(s, k1, dt * 0.5);
        Forces f2 = accumulate(s2, cfg, dt);
        Stage k2 = stage(s2, cfg, dt, f2);

        VehicleState s3 = shifted(s, k2, dt * 0.5);
        Forces f3 = accumulate(s3, cfg, dt);
        Stage k3 = stage(s3, cfg, dt, f3);

        VehicleState s4 = shifted(s, k3, dt);
        Forces f4 = accumulate(s4, cfg, dt);
        Stage k4 = stage(s4, cfg, dt, f4);

        s.position = s.position.add(combine(k1.dr, k2.dr, k3.dr, k4.dr, dt / 6.0));
        s.velocity = s.velocity.add(combine(k1.dv, k2.dv, k3.dv, k4.dv, dt / 6.0));
        s.angularVelocity = s.angularVelocity.add(combine(k1.dOm, k2.dOm, k3.dOm, k4.dOm, dt / 6.0));

        Quaternion dq = combineQ(k1.dq, k2.dq, k3.dq, k4.dq, dt / 6.0);
        s.orientation = new Quaternion(
                s.orientation.x() + dq.x(), s.orientation.y() + dq.y(),
                s.orientation.z() + dq.z(), s.orientation.w() + dq.w()).normalized();

        s.energy += (k1.dE + 2 * k2.dE + 2 * k3.dE + k4.dE) * (dt / 6.0);
        applyDamping(s, dt, cfg);
    }

    private record Stage(Vec3 dr, Vec3 dv, Vec3 dOm, Quaternion dq, double dE) {}

    private Stage stage(VehicleState s, PhysicsConfig cfg, double dt, Forces f) {
        derivative(s, cfg, dt, f, dScratch);
        return new Stage(dScratch.dr, dScratch.dv, dScratch.dOmega, dScratch.dq, dScratch.dE);
    }

    private VehicleState shifted(VehicleState s, Stage k, double h) {
        Quaternion q = new Quaternion(
                s.orientation.x() + k.dq.x() * h, s.orientation.y() + k.dq.y() * h,
                s.orientation.z() + k.dq.z() * h, s.orientation.w() + k.dq.w() * h).normalized();
        return new VehicleState(
                s.position.add(k.dr.scale(h)),
                s.velocity.add(k.dv.scale(h)),
                q,
                s.angularVelocity.add(k.dOm.scale(h)),
                s.energy + k.dE * h);
    }

    private static Vec3 combine(Vec3 a, Vec3 b, Vec3 c, Vec3 d, double w) {
        return new Vec3(
                (a.x() + 2 * b.x() + 2 * c.x() + d.x()) * w,
                (a.y() + 2 * b.y() + 2 * c.y() + d.y()) * w,
                (a.z() + 2 * b.z() + 2 * c.z() + d.z()) * w);
    }

    private static Quaternion combineQ(Quaternion a, Quaternion b, Quaternion c, Quaternion d, double w) {
        return new Quaternion(
                (a.x() + 2 * b.x() + 2 * c.x() + d.x()) * w,
                (a.y() + 2 * b.y() + 2 * c.y() + d.y()) * w,
                (a.z() + 2 * b.z() + 2 * c.z() + d.z()) * w,
                (a.w() + 2 * b.w() + 2 * c.w() + d.w()) * w);
    }

    private static void applyDamping(VehicleState s, double dt, PhysicsConfig cfg) {
        if (cfg.linearDamping() > 0)
            s.velocity = s.velocity.scale(Math.max(0, 1 - cfg.linearDamping() * dt));
        if (cfg.angularDamping() > 0)
            s.angularVelocity = s.angularVelocity.scale(Math.max(0, 1 - cfg.angularDamping() * dt));
    }

    /** Advance one fixed step. Enforces fixed dt from config; caller should pass cfg.dt(). */
    public void step(VehicleState s, PhysicsConfig cfg, double dt) {
        if (cfg.isRk4()) stepRk4(s, cfg, dt);
        else stepEuler(s, cfg, dt);

        // Safety: guarantee unit quaternion and finite state after every step.
        s.orientation = s.orientation.normalized();
        if (!finite(s)) throw new IllegalStateException("Non-finite state after integration step");
    }

    private static boolean finite(VehicleState s) {
        return isFinite(s.position) && isFinite(s.velocity) && isFinite(s.angularVelocity)
                && isFinite(s.orientation.x(), s.orientation.y(), s.orientation.z(), s.orientation.w());
    }

    private static boolean isFinite(Vec3 v) { return isFinite(v.x(), v.y(), v.z()); }
    private static boolean isFinite(double... xs) {
        for (double x : xs) if (!Double.isFinite(x)) return false;
        return true;
    }
}
