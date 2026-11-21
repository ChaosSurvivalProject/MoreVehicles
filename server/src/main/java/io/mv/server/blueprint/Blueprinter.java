package io.mv.server.blueprint;

import io.mv.common.blueprint.Blueprint;
import io.mv.common.blueprint.MassProperties;
import io.mv.common.blueprint.VoxelSet;
import io.mv.common.math.Vec3;
import io.mv.common.physics.fluid.Hydrostatics;

import java.util.Map;

/**
 * Assemble() pipeline (MV-SPEC 6.2 / P6-F): seat root -> connected voxels -> blueprint
 * -> mass properties -> geometry -> hydrostatic curves -> VehicleRig. All derived data
 * is precomputed once at assembly time (LUT, zero per-tick recompute).
 * Block-to-voxel conversion is injected so common stays free of MC types.
 */
public final class Blueprinter {
    private final int maxVoxels;

    public Blueprinter(int maxVoxels) {
        this.maxVoxels = maxVoxels;
    }

    public record AssembleResult(boolean success, Blueprint blueprint, String reason) {}

    /** Assemble from a prebuilt voxel map (server converts blocks -> voxels). */
    public AssembleResult assemble(String name, Map<Long, VoxelSet.Voxel> allBlocks, Vec3 seatRoot) {
        VoxelSet connected = VoxelSet.assemble(allBlocks, seatRoot, maxVoxels);
        if (connected == null) return new AssembleResult(false, null,
                "voxel cap exceeded (" + maxVoxels + ")");
        if (connected.size() == 0) return new AssembleResult(false, null, "no seat/root voxel");

        MassProperties mass = MassProperties.from(connected);
        Blueprint.DerivedGeometry geo = Blueprint.deriveGeometry(connected);
        var parts = Blueprint.collectParts(connected, mass.centerOfMass);

        // Precompute hydrostatic LUT at 0.05-block steps (P6-F).
        Hydrostatics.Curves curves = buildHydrostaticCurves(geo);

        double waterlineArea = geo.waterlineLength() * geo.waterlineLength();
        double fsm = sumBallastFreeSurface(parts);

        Blueprint bp = new Blueprint(name, connected, parts, mass, geo, curves, waterlineArea, fsm);
        return new AssembleResult(true, bp, null);
    }

    private Hydrostatics.Curves buildHydrostaticCurves(Blueprint.DerivedGeometry geo) {
        int steps = 40; // 0..2 blocks at 0.05
        double[] depths = new double[steps], vols = new double[steps], kb = new double[steps], iwp = new double[steps];
        for (int i = 0; i < steps; i++) {
            double d = i * 0.05;
            depths[i] = d;
            vols[i] = geo.area() * Math.min(d, geo.waterlineLength());
            kb[i] = d * 0.5;
            iwp[i] = geo.waterlineLength() * Math.pow(geo.waterlineLength(), 3) / 12.0 * Math.min(1, d / 0.5);
        }
        double[] ga = {-0.5, -0.25, 0, 0.25, 0.5};
        double[] gv = {-0.3, -0.15, 0, 0.15, 0.3};
        return new Hydrostatics.Curves(depths, vols, kb, iwp, ga, gv);
    }

    private double sumBallastFreeSurface(java.util.List<io.mv.common.blueprint.Part> parts) {
        double fsm = 0;
        for (var p : parts) {
            if (p.kind == io.mv.common.blueprint.Part.Kind.BALLAST_TANK) {
                // Free-surface moment proportional to tank area; half-full worst case.
                fsm += 0.05 * Math.max(1, p.params.length);
            }
        }
        return fsm;
    }
}
