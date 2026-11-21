package io.mv.server.display;

import io.mv.common.math.Quaternion;
import io.mv.common.math.Vec3;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Vanilla fallback rendering (MV-SPEC 7.4 / P9): BlockDisplay + ItemDisplay + Interaction
 * assembled by rigid transform with interpolationDuration/teleportDuration smoothing.
 * Vanilla clients (no Fabric mod) can see vehicles; Fabric clients hide these after spawn.
 */
public final class DisplayRig {
    private final World world;
    private final int interpolationTicks;

    public DisplayRig(World world, int interpolationTicks) {
        this.world = world;
        this.interpolationTicks = interpolationTicks;
    }

    /** Spawn a block-display proxy at a pose; returns the entity id for later updates. */
    public int spawnBlock(Vec3 pos, Quaternion q, org.bukkit.Material material) {
        Location loc = new Location(world, pos.x(), pos.y(), pos.z());
        BlockDisplay display = (BlockDisplay) world.spawnEntity(loc, EntityType.BLOCK_DISPLAY);
        display.setBlock(material.createBlockData());
        applyTransform(display, pos, q);
        return display.getEntityId();
    }

    /** Update an existing Display's transform with server-side interpolation. */
    public void update(Display display, Vec3 pos, Quaternion q) {
        applyTransform(display, pos, q);
    }

    private void applyTransform(Display display, Vec3 pos, Quaternion q) {
        Vector3f translation = new Vector3f((float) pos.x(), (float) pos.y(), (float) pos.z());
        Quaternionf rotation = new Quaternionf(
                new AxisAngle4f(0, new Vector3f(1, 0, 0))); // default; caller may supply axis
        rotation.set((float) q.x(), (float) q.y(), (float) q.z(), (float) q.w());
        Transformation t = new Transformation(
                translation, rotation,
                new Vector3f(1, 1, 1), new Quaternionf(new AxisAngle4f(0, new Vector3f(0, 1, 0))));
        display.setTransformation(t);
        display.setInterpolationDuration(interpolationTicks);
        display.setTeleportDuration(Math.max(1, interpolationTicks / 2));
    }
}
