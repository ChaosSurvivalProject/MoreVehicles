package io.mv.client.render;

import io.mv.client.network.ClientNetwork;
import io.mv.common.math.Quaternion;
import io.mv.common.math.Vec3;
import io.mv.client.predict.SnapshotBuffer;
import net.minecraft.client.MinecraftClient;

import java.util.HashMap;
import java.util.Map;

/**
 * Pure-client vehicle entities: added to ClientWorld only, never registered as server
 * EntityType. After spawn, hides the server's Display group to avoid double rendering (P8).
 */
public final class ClientVehicleEntity {
    private final SnapshotBuffer buffer = new SnapshotBuffer();
    private final Map<Integer, Vec3> poses = new HashMap<>();

    /** Feed a network snapshot into the interpolation buffer. */
    public void onSnapshot(ClientNetwork.Snapshot s) {
        buffer.push(new SnapshotBuffer.Sample(System.currentTimeMillis(),
                new Vec3(s.x(), s.y(), s.z()),
                new Quaternion(s.qx(), s.qy(), s.qz(), s.qw())));
    }

    /** Interpolate all known vehicle poses (called every client tick). */
    public void interpolate() {
        SnapshotBuffer.Sample pose = buffer.sample();
        if (pose != null) poses.put(0, pose.pos());
    }

    public Vec3 pose(int vehicleId) { return poses.get(vehicleId); }
    public SnapshotBuffer buffer() { return buffer; }
}
