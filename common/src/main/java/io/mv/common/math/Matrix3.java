package io.mv.common.math;

import java.util.Arrays;

/**
 * 3x3 matrix stored row-major. Used for inertia tensors (keeps off-diagonal
 * terms per MV-SPEC 4.1) and rotation matrices.
 */
public final class Matrix3 {
    private final double[] m = new double[9];

    public static Matrix3 identity() {
        Matrix3 r = new Matrix3();
        r.m[0] = r.m[4] = r.m[8] = 1;
        return r;
    }

    /** Diagonal matrix from three axis values. */
    public static Matrix3 diag(double a, double b, double c) {
        Matrix3 r = new Matrix3();
        r.m[0] = a; r.m[4] = b; r.m[8] = c;
        return r;
    }

    public Matrix3 set(int row, int col, double v) {
        m[row * 3 + col] = v;
        return this;
    }

    public double get(int row, int col) {
        return m[row * 3 + col];
    }

    public Matrix3 copy() {
        Matrix3 r = new Matrix3();
        System.arraycopy(m, 0, r.m, 0, 9);
        return r;
    }

    /** Rotation matrix from unit quaternion. */
    public static Matrix3 fromQuaternion(Quaternion q) {
        double x = q.x(), y = q.y(), z = q.z(), w = q.w();
        Matrix3 r = new Matrix3();
        r.m[0] = 1 - 2 * (y * y + z * z);
        r.m[1] = 2 * (x * y - z * w);
        r.m[2] = 2 * (x * z + y * w);
        r.m[3] = 2 * (x * y + z * w);
        r.m[4] = 1 - 2 * (x * x + z * z);
        r.m[5] = 2 * (y * z - x * w);
        r.m[6] = 2 * (x * z - y * w);
        r.m[7] = 2 * (y * z + x * w);
        r.m[8] = 1 - 2 * (x * x + y * y);
        return r;
    }

    /** this * v (matrix times column vector). */
    public Vec3 mul(Vec3 v) {
        return new Vec3(
                m[0] * v.x() + m[1] * v.y() + m[2] * v.z(),
                m[3] * v.x() + m[4] * v.y() + m[5] * v.z(),
                m[6] * v.x() + m[7] * v.y() + m[8] * v.z());
    }

    /** A * B. */
    public Matrix3 mul(Matrix3 b) {
        Matrix3 r = new Matrix3();
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                double s = 0;
                for (int k = 0; k < 3; k++) s += m[i * 3 + k] * b.m[k * 3 + j];
                r.m[i * 3 + j] = s;
            }
        }
        return r;
    }

    public Matrix3 transpose() {
        Matrix3 r = new Matrix3();
        for (int i = 0; i < 3; i++)
            for (int j = 0; j < 3; j++) r.m[i * 3 + j] = m[j * 3 + i];
        return r;
    }

    /** Inverse via adjugate/determinant. Fails loudly for singular input. */
    public Matrix3 invert() {
        double a = m[0], b = m[1], c = m[2];
        double d = m[3], e = m[4], f = m[5];
        double g = m[6], h = m[7], i = m[8];
        double A = e * i - f * h, B = -(d * i - f * g), C = d * h - e * g;
        double det = a * A + b * B + c * C;
        if (Math.abs(det) < 1e-12) throw new IllegalStateException("Singular matrix (det=" + det + ")");
        Matrix3 r = new Matrix3();
        r.m[0] = A / det; r.m[1] = (c * h - b * i) / det; r.m[2] = (b * f - c * e) / det;
        r.m[3] = B / det; r.m[4] = (a * i - c * g) / det; r.m[5] = (c * d - a * f) / det;
        r.m[6] = C / det; r.m[7] = (b * g - a * h) / det; r.m[8] = (a * e - b * d) / det;
        return r;
    }

    public double trace() {
        return m[0] + m[4] + m[8];
    }

    /** Symmetric part; inertia tensors must be symmetric. */
    public Matrix3 symmetric() {
        Matrix3 r = new Matrix3();
        for (int i = 0; i < 3; i++)
            for (int j = 0; j < 3; j++)
                r.m[i * 3 + j] = (get(i, j) + get(j, i)) * 0.5;
        return r;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Matrix3[");
        for (int i = 0; i < 3; i++) {
            sb.append('[').append(String.format("%.4f %.4f %.4f", m[i * 3], m[i * 3 + 1], m[i * 3 + 2])).append(']');
        }
        return sb.append(']').toString();
    }
}
