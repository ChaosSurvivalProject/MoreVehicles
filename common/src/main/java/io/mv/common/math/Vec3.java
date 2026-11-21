package io.mv.common.math;

/**
 * Immutable 3-component vector in a right-handed, Y-up coordinate system.
 * Body-axis convention matches MV-SPEC 4.1: +X forward, +Y up, +Z right.
 * Lightweight value type; the integrator reuses preallocated scratch instances
 * so the hot path allocates nothing per tick.
 */
public record Vec3(double x, double y, double z) {

    public static final Vec3 ZERO = new Vec3(0, 0, 0);
    public static final Vec3 X = new Vec3(1, 0, 0);
    public static final Vec3 Y = new Vec3(0, 1, 0);
    public static final Vec3 Z = new Vec3(0, 0, 1);

    public Vec3 add(Vec3 o) {
        return new Vec3(x + o.x, y + o.y, z + o.z);
    }

    public Vec3 sub(Vec3 o) {
        return new Vec3(x - o.x, y - o.y, z - o.z);
    }

    public Vec3 scale(double s) {
        return new Vec3(x * s, y * s, z * s);
    }

    public Vec3 negate() {
        return new Vec3(-x, -y, -z);
    }

    public double dot(Vec3 o) {
        return x * o.x + y * o.y + z * o.z;
    }

    public Vec3 cross(Vec3 o) {
        return new Vec3(
                y * o.z - z * o.y,
                z * o.x - x * o.z,
                x * o.y - y * o.x);
    }

    public double lengthSq() {
        return x * x + y * y + z * z;
    }

    public double length() {
        return Math.sqrt(lengthSq());
    }

    public Vec3 normalized() {
        double l = length();
        return l < 1e-12 ? ZERO : new Vec3(x / l, y / l, z / l);
    }

    /** Linear interpolation. */
    public static Vec3 lerp(Vec3 a, Vec3 b, double t) {
        return new Vec3(
                a.x + (b.x - a.x) * t,
                a.y + (b.y - a.y) * t,
                a.z + (b.z - a.z) * t);
    }

    /** Component-wise sum of a variable list (for accumulating force sinks). */
    public static Vec3 sum(Vec3... vs) {
        double x = 0, y = 0, z = 0;
        for (Vec3 v : vs) {
            x += v.x;
            y += v.y;
            z += v.z;
        }
        return new Vec3(x, y, z);
    }
}
