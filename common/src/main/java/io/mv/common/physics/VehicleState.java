package io.mv.common.physics;

import io.mv.common.math.Quaternion;
import io.mv.common.math.Vec3;

/**
 * 6DOF state x = [r, v_b, q, omega, E] per MV-SPEC 4.1.
 * Mutable fields so the integrator can step in place with zero per-tick allocation.
 */
public final class VehicleState {
    public Vec3 position = Vec3.ZERO;   // world frame r
    public Vec3 velocity = Vec3.ZERO;   // body frame v_b
    public Quaternion orientation = Quaternion.IDENTITY;
    public Vec3 angularVelocity = Vec3.ZERO; // body frame omega
    public double energy = 0.0;         // stored energy E

    public VehicleState() {}

    public VehicleState(Vec3 position, Vec3 velocity, Quaternion orientation, Vec3 angularVelocity, double energy) {
        this.position = position;
        this.velocity = velocity;
        this.orientation = orientation;
        this.angularVelocity = angularVelocity;
        this.energy = energy;
    }

    public VehicleState copy() {
        return new VehicleState(position, velocity, orientation, angularVelocity, energy);
    }

    public void setTo(VehicleState o) {
        this.position = o.position;
        this.velocity = o.velocity;
        this.orientation = o.orientation;
        this.angularVelocity = o.angularVelocity;
        this.energy = o.energy;
    }

    /** World-frame linear velocity: R(q) v_b (for relative-flow / F_slam). */
    public Vec3 worldVelocity() {
        return orientation.rotate(velocity);
    }
}
