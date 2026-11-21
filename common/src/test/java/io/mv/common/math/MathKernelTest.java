package io.mv.common.math;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MathKernelTest {

    @Test
    void quaternionStaysNormalizedAfter100kSteps() {
        Quaternion q = Quaternion.IDENTITY;
        Vec3 axis = new Vec3(0.3, 0.5, 0.81).normalized();
        Quaternion dq = Quaternion.fromAxisAngle(axis, 0.001);
        for (int i = 0; i < 100_000; i++) q = q.mul(dq).normalized();
        assertTrue(Math.abs(q.normSq() - 1.0) < 1e-9,
                "quaternion norm drift: " + (q.normSq() - 1));
    }

    @Test
    void quaternionRotationMatchesAxisAngle() {
        Quaternion q = Quaternion.fromAxisAngle(Vec3.Y, Math.PI / 2);
        Vec3 rotated = q.rotate(Vec3.X);
        assertTrue(Math.abs(rotated.x()) < 1e-9);
        assertTrue(Math.abs(rotated.z() + 1) < 1e-9, "expected -Z after +90deg about Y, got " + rotated);
    }

    @Test
    void rotationInverseUndoesRotation() {
        Quaternion q = Quaternion.fromAxisAngle(new Vec3(0.2, 0.7, 0.5).normalized(), 1.1);
        Vec3 v = new Vec3(3, -2, 5);
        Vec3 round = q.rotateInverse(q.rotate(v));
        assertTrue(Math.abs(round.x() - v.x()) < 1e-9);
        assertTrue(Math.abs(round.y() - v.y()) < 1e-9);
        assertTrue(Math.abs(round.z() - v.z()) < 1e-9);
    }

    @Test
    void inertiaInvertRoundTrips() {
        Matrix3 I = Matrix3.diag(2, 3, 4).set(0, 1, 0.2).set(1, 0, 0.2);
        Matrix3 prod = I.mul(I.invert());
        for (int i = 0; i < 3; i++)
            for (int j = 0; j < 3; j++)
                assertTrue(Math.abs(prod.get(i, j) - (i == j ? 1 : 0)) < 1e-9);
    }

    @Test
    void slerpEndpointsAndNormalization() {
        Quaternion a = Quaternion.IDENTITY;
        Quaternion b = new Quaternion(0.1, 0.2, 0.3, 0.9).normalized();
        assertEquals(0.0, Quaternion.slerp(a, b, 0.0).x(), 1e-9);
        Quaternion atOne = Quaternion.slerp(a, b, 1.0);
        assertTrue(Math.abs(atOne.x() - b.x()) < 1e-9
                && Math.abs(atOne.w() - b.w()) < 1e-9, "slerp(t=1) should equal b");
        assertTrue(Quaternion.slerp(a, b, 0.5).isNormalized(1e-9));
    }
}
