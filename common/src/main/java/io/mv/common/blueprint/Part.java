package io.mv.common.blueprint;

import io.mv.common.math.Vec3;

/**
 * A functional component of a vehicle, derived from block tags (MV-SPEC 6.3 / 11.10).
 * Untagged voxels only contribute mass/inertia/collision/appearance.
 */
public final class Part {
    public enum Kind {
        SEAT, THRUSTER, WING, CONTROL_SURFACE, WHEEL, BOGIE, CONNECTOR, ENERGY, BALLAST,
        BUOYANCY, BALLAST_TANK, HYDROFOIL, RUDDER, PROPELLER, JET, WATERJET, KEEL, SAIL
    }

    public final Kind kind;
    public final Vec3 offset;      // body frame, relative to COM
    public final Vec3 axis;        // mounting/force axis (body frame)
    public final double[] params;  // kind-specific numeric parameters (config-driven)

    public Part(Kind kind, Vec3 offset, Vec3 axis, double[] params) {
        this.kind = kind;
        this.offset = offset;
        this.axis = axis;
        this.params = params;
    }

    /** Map a block tag (mv:thruster etc.) to a Part.Kind; null if not a functional tag. */
    public static Kind kindFromTag(String tag) {
        if (tag == null || !tag.startsWith("mv:")) return null;
        return switch (tag.substring(3)) {
            case "seat" -> Kind.SEAT;
            case "thruster" -> Kind.THRUSTER;
            case "wing" -> Kind.WING;
            case "control_surface" -> Kind.CONTROL_SURFACE;
            case "wheel" -> Kind.WHEEL;
            case "bogie" -> Kind.BOGIE;
            case "connector" -> Kind.CONNECTOR;
            case "energy" -> Kind.ENERGY;
            case "ballast" -> Kind.BALLAST;
            case "buoyancy" -> Kind.BUOYANCY;
            case "ballast_tank" -> Kind.BALLAST_TANK;
            case "hydrofoil" -> Kind.HYDROFOIL;
            case "rudder" -> Kind.RUDDER;
            case "propeller" -> Kind.PROPELLER;
            case "jet" -> Kind.JET;
            case "waterjet" -> Kind.WATERJET;
            case "keel" -> Kind.KEEL;
            case "sail" -> Kind.SAIL;
            default -> null;
        };
    }
}
