package io.mv.common.blueprint;

import io.mv.common.math.Vec3;
import io.mv.common.physics.fluid.Hydrostatics;

import java.util.ArrayList;
import java.util.List;

/**
 * Serialized vehicle blueprint (MV-SPEC 6.4): voxel table + parts + derived params + version.
 * Persisted as JSON under plugins/MoreVehicles/blueprints/ by the server; common layer
 * holds the pure data model (JSON (de)serialization is done with a small hand-rolled codec
 * to avoid external dependencies and keep the module unit-testable).
 */
public final class Blueprint {
    public static final int FORMAT_VERSION = 1;

    public final String name;
    public final VoxelSet voxels;
    public final List<Part> parts;
    public final MassProperties mass;
    public final DerivedGeometry geometry;
    public final Hydrostatics.Curves hydrostaticCurves;
    public final double waterlineArea;
    public final double freeSurfaceMoment;

    /** Derived geometry: reference area S, span b, chord cbar, AR, wet surface, waterline length. */
    public record DerivedGeometry(double area, double span, double chord, double ar,
                                  double wetSurface, double waterlineLength) {}

    public Blueprint(String name, VoxelSet voxels, List<Part> parts, MassProperties mass,
                     DerivedGeometry geometry, Hydrostatics.Curves hydrostaticCurves,
                     double waterlineArea, double freeSurfaceMoment) {
        this.name = name;
        this.voxels = voxels;
        this.parts = List.copyOf(parts);
        this.mass = mass;
        this.geometry = geometry;
        this.hydrostaticCurves = hydrostaticCurves;
        this.waterlineArea = waterlineArea;
        this.freeSurfaceMoment = freeSurfaceMoment;
    }

    /** Collect functional parts from voxel tags. */
    public static List<Part> collectParts(VoxelSet voxels, Vec3 com) {
        List<Part> parts = new ArrayList<>();
        for (VoxelSet.Voxel v : voxels.voxels()) {
            for (String tag : v.tags()) {
                Part.Kind kind = Part.kindFromTag(tag);
                if (kind != null) {
                    Vec3 offset = new Vec3(v.x(), v.y(), v.z());
                    parts.add(new Part(kind, offset, Vec3.X, new double[0]));
                }
            }
        }
        return parts;
    }

    /** Reference geometry from voxel bounds: S = max projected area, b/cbar from span. */
    public static DerivedGeometry deriveGeometry(VoxelSet voxels) {
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (VoxelSet.Voxel v : voxels.voxels()) {
            minX = Math.min(minX, v.x()); maxX = Math.max(maxX, v.x());
            minY = Math.min(minY, v.y()); maxY = Math.max(maxY, v.y());
            minZ = Math.min(minZ, v.z()); maxZ = Math.max(maxZ, v.z());
        }
        double sx = (maxX - minX + 1.0) * (maxY - minY + 1.0); // YZ projection
        double sy = (maxX - minX + 1.0) * (maxZ - minZ + 1.0); // XZ projection
        double sz = (maxY - minY + 1.0) * (maxZ - minZ + 1.0); // XY projection
        double span = Math.max(maxX - minX + 1, Math.max(maxY - minY + 1, maxZ - minZ + 1));
        double area = Math.max(sx, Math.max(sy, sz));
        double lengthWl = Math.max(maxX - minX + 1.0, maxZ - minZ + 1.0);
        return new DerivedGeometry(area, span, Math.max(1, area / span), span * span / area,
                area * 2, lengthWl);
    }
}
