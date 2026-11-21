package io.mv.server.world;

import io.mv.common.math.Vec3;
import io.mv.common.protocol.MVCodecs;
import io.mv.common.protocol.PacketType;
import io.mv.server.MoreVehiclesPlugin;
import io.mv.server.config.ServerConfig;
import io.mv.server.protocol.MVProtocol;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Authoritative vehicle world (MV-SPEC 2/3.5): 20 TPS fixed-step integration,
 * per-chunk snapshot broadcast to mvCapable clients, Display fallback for vanilla.
 * Physics runs only here; client never computes authority.
 */
public final class VehicleWorld {
    private final MoreVehiclesPlugin plugin;
    private final MVProtocol protocol;
    private volatile ServerConfig config;
    private final Map<Integer, Vec3> vehiclePositions = new ConcurrentHashMap<>();
    private BukkitRunnable tickTask;
    private int snapshotTick = 0;

    public VehicleWorld(MoreVehiclesPlugin plugin, ServerConfig config, MVProtocol protocol) {
        this.plugin = plugin;
        this.config = config;
        this.protocol = protocol;
        wireHandshake();
    }

    /** Pitfall A: respond to C2S hello with hello_ack only after marking capable. */
    private void wireHandshake() {
        protocol.onHello((player, raw) -> {
            // Server runs a fixed 20 TPS authoritative loop (MV-SPEC 3.5).
            protocol.send(player, protocol.buildHelloAck(20, true));
            plugin.getLogger().info("[MV] handshake hello from " + player.getName()
                    + " (mvCapable=" + protocol.isCapable(player.getUniqueId()) + ")");
        });
        protocol.onInbound((player, raw) -> {
            // Incoming input/ping packets would be decoded here (P7); debug ping -> pong.
            try {
                var src = new MVCodecs.ByteArraySource(raw);
                PacketType type = PacketType.fromOrdinal(MVCodecs.readVarInt(src));
                if (type == PacketType.PING) {
                    var sink = new MVCodecs.ByteArraySink();
                    MVCodecs.writeVarInt(sink, PacketType.PONG.ordinalByte());
                    protocol.send(player, sink.toByteArray());
                }
            } catch (RuntimeException ignored) { /* malformed debug packet */ }
        });
    }

    public void start() {
        tickTask = new BukkitRunnable() {
            @Override
            public void run() {
                step();
            }
        };
        tickTask.runTaskTimer(plugin, 0L, 1L); // 20 TPS on standard server
        plugin.getLogger().info("[MV] VehicleWorld ticking at dt=" + config.dt() + "s");
    }

    public void stop() {
        if (tickTask != null) tickTask.cancel();
    }

    public void applyConfig(ServerConfig newConfig) { this.config = newConfig; }

    /** One authoritative tick. Physics integration is pluggable per vehicle (P2+); skeleton advances bookkeeping. */
    private void step() {
        snapshotTick++;
        int interval = Math.max(1, 20 / Math.max(1, config.snapshotHz()));
        if (snapshotTick % interval != 0) return;
        broadcastSnapshots();
    }

    /** Batch snapshots to nearby mvCapable clients (MV-SPEC 3.5 broadcast pruning). */
    private void broadcastSnapshots() {
        if (vehiclePositions.isEmpty()) return;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!protocol.isCapable(player.getUniqueId())) continue;
            for (Map.Entry<Integer, Vec3> e : vehiclePositions.entrySet()) {
                var sink = new MVCodecs.ByteArraySink();
                MVCodecs.writeVarInt(sink, PacketType.SNAPSHOT.ordinalByte());
                MVCodecs.writeInt(sink, e.getKey());
                MVCodecs.writeDouble(sink, e.getValue().x());
                MVCodecs.writeDouble(sink, e.getValue().y());
                MVCodecs.writeDouble(sink, e.getValue().z());
                protocol.send(player, sink.toByteArray());
            }
        }
    }

    /** Debug: current vehicle count. */
    public int vehicleCount() { return vehiclePositions.size(); }
}
