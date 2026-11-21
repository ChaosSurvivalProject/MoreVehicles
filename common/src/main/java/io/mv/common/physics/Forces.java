package io.mv.common.physics;

import io.mv.common.math.Vec3;

/** Accumulated force and moment in body frame, plus energy delta. Reusable mutable accumulator. */
public final class Forces {
    public Vec3 force = Vec3.ZERO;
    public Vec3 moment = Vec3.ZERO;
    public double energyDelta = 0.0;

    public void add(Vec3 f, Vec3 m) {
        force = force.add(f);
        moment = moment.add(m);
    }

    public void addForce(Vec3 f) {
        force = force.add(f);
    }

    public void addMoment(Vec3 m) {
        moment = moment.add(m);
    }

    /** Add a force applied at body-frame offset r: contributes r × F moment (thrust eccentricity). */
    public void addAt(Vec3 f, Vec3 offsetFromCom) {
        force = force.add(f);
        moment = moment.add(offsetFromCom.cross(f));
    }

    public void reset() {
        force = Vec3.ZERO;
        moment = Vec3.ZERO;
        energyDelta = 0.0;
    }

    public Forces copy() {
        Forces f = new Forces();
        f.force = force;
        f.moment = moment;
        f.energyDelta = energyDelta;
        return f;
    }
}
