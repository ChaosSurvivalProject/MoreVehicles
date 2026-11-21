package io.mv.common.blueprint;

import io.mv.common.math.Matrix3;
import io.mv.common.math.Vec3;

/**
 * Mass properties derived from a voxel blueprint (MV-SPEC 4.5 / 6.2):
 *   m = sum rho
 *   c = sum m_i r_i / m
 *   I = sum m_i (|r_i|^2 E - r_i r_i^T) + I_box_i   (off-diagonal terms preserved)
 */
public final class MassProperties {
    public final double mass;
    public final Vec3 centerOfMass;
    public final Matrix3 inertia;

    public MassProperties(double mass, Vec3 centerOfMass, Matrix3 inertia) {
        this.mass = mass;
        this.centerOfMass = centerOfMass;
        this.inertia = inertia;
    }

    /** Compute from voxels. Unit cube inertia per voxel = (1/6) m I. */
    public static MassProperties from(VoxelSet voxels) {
        double m = 0;
        Vec3 weighted = Vec3.ZERO;
        for (VoxelSet.Voxel v : voxels.voxels()) {
            m += v.density();
            weighted = weighted.add(new Vec3(v.x(), v.y(), v.z()).scale(v.density()));
        }
        if (m <= 0) return new MassProperties(0, Vec3.ZERO, Matrix3.identity());
        Vec3 com = weighted.scale(1.0 / m);

        Matrix3 I = new Matrix3(); // zero-initialized; accumulate below
        for (VoxelSet.Voxel v : voxels.voxels()) {
            Vec3 r = new Vec3(v.x(), v.y(), v.z()).sub(com);
            double r2 = r.lengthSq();
            double mi = v.density();
            // Parallel-axis: m(|r|^2 E - r r^T)
            for (int i = 0; i < 3; i++) {
                for (int j = 0; j < 3; j++) {
                    double delta = (i == j) ? r2 : 0;
                    double ri = axis(r, i), rj = axis(r, j);
                    double val = I.get(i, j) + mi * (delta - ri * rj);
                    // Self inertia of a unit cube: (1/6) m on diagonal
                    if (i == j) val += mi / 6.0;
                    I.set(i, j, val);
                }
            }
        }
        return new MassProperties(m, com, I.symmetric());
    }

    private static double axis(Vec3 v, int i) {
        return i == 0 ? v.x() : (i == 1 ? v.y() : v.z());
    }
}
