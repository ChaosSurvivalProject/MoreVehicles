package io.mv.common.math;

/**
 * Unit quaternion {@code (x, y, z, w)} for attitude. Never integrate Euler angles
 * (gimbal lock); normalize every step per MV-SPEC 4.1.
 */
public record Quaternion(double x, double y, double z, double w) {

    public static final Quaternion IDENTITY = new Quaternion(0, 0, 0, 1);

    /** Axis must be normalized; angle in radians (right-hand rule). */
    public static Quaternion fromAxisAngle(Vec3 axis, double angle) {
        double h = angle * 0.5;
        double s = Math.sin(h);
        return new Quaternion(axis.x() * s, axis.y() * s, axis.z() * s, Math.cos(h));
    }

    /** Hamilton product: this ⊗ o. Used for q_dot = 1/2 q ⊗ (0, ω). */
    public Quaternion mul(Quaternion o) {
        return new Quaternion(
                w * o.x + x * o.w + y * o.z - z * o.y,
                w * o.y - x * o.z + y * o.w + z * o.x,
                w * o.z + x * o.y - y * o.x + z * o.w,
                w * o.w - x * o.x - y * o.y - z * o.z);
    }

    /** Rotate a body-frame vector into world frame: r = R(q) v_b. */
    public Vec3 rotate(Vec3 v) {
        // t = 2 * (q_vec × v); v' = v + w*t + q_vec × t
        double tx = 2 * (y * v.z() - z * v.y());
        double ty = 2 * (z * v.x() - x * v.z());
        double tz = 2 * (x * v.y() - y * v.x());
        return new Vec3(
                v.x() + w * tx + y * tz - z * ty,
                v.y() + w * ty + z * tx - x * tz,
                v.z() + w * tz + x * ty - y * tx);
    }

    /** Rotate a world-frame vector into body frame: v_b = R(q)^T v_world. */
    public Vec3 rotateInverse(Vec3 v) {
        return conjugate().rotate(v);
    }

    public Quaternion conjugate() {
        return new Quaternion(-x, -y, -z, w);
    }

    public double normSq() {
        return x * x + y * y + z * z + w * w;
    }

    public double norm() {
        return Math.sqrt(normSq());
    }

    public boolean isNormalized(double tol) {
        return Math.abs(normSq() - 1.0) <= tol;
    }

    public Quaternion normalized() {
        double n = norm();
        return n < 1e-12 ? IDENTITY : new Quaternion(x / n, y / n, z / n, w / n);
    }

    /** Shortest-arc spherical interpolation (negate q when dot<0), per FIX-4. */
    public static Quaternion slerp(Quaternion a, Quaternion b, double t) {
        double dot = a.x * b.x + a.y * b.y + a.z * b.z + a.w * b.w;
        double bx = b.x, by = b.y, bz = b.z, bw = b.w;
        if (dot < 0) {
            dot = -dot;
            bx = -bx; by = -by; bz = -bz; bw = -bw;
        }
        if (dot > 0.9995) {
            return new Quaternion(
                    a.x + (bx - a.x) * t,
                    a.y + (by - a.y) * t,
                    a.z + (bz - a.z) * t,
                    a.w + (bw - a.w) * t).normalized();
        }
        double theta = Math.acos(Math.min(1, dot));
        double sinTheta = Math.sin(theta);
        double wa = Math.sin((1 - t) * theta) / sinTheta;
        double wb = Math.sin(t * theta) / sinTheta;
        return new Quaternion(
                a.x * wa + bx * wb,
                a.y * wa + by * wb,
                a.z * wa + bz * wb,
                a.w * wa + bw * wb).normalized();
    }
}
