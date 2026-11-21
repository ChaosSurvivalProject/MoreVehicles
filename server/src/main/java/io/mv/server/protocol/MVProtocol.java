package io.mv.server.protocol;

import io.mv.common.protocol.MVCodecs;
import io.mv.common.protocol.MVSpec;
import io.mv.common.protocol.PacketType;
import io.mv.server.MoreVehiclesPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * Bukkit Messenger bridge. Writes RAW bytes identical to Fabric StreamCodec (MVCodecs).
 * Handles the four known pitfalls from MV-SPEC 3.2:
 *  A) Bukkit only sends to clients that declared the channel; modern Fabric doesn't send
 *     minecraft:register -> S2C silently dropped. Solution: client sends C2S hello after
 *     JOIN, server marks the player mvCapable and registers the outgoing channel, plus
 *     a minecraft:register declaration as belt-and-suspenders.
 *  B) Fabric codec fallback -> fallback_mixin toggle (documented, off by default).
 *  C) No CONFIGURATION plugin message -> all handshake in PLAY.
 *  D) Velocity/Bungee may rewrite channels -> proxy flag in hello.
 */
public final class MVProtocol implements PluginMessageListener {
    private final MoreVehiclesPlugin plugin;
    private final Set<java.util.UUID> mvCapable = ConcurrentHashMap.newKeySet();
    private BiConsumer<Player, byte[]> helloHandler = (p, b) -> {};
    private BiConsumer<Player, byte[]> inboundHandler = (p, b) -> {};

    public MVProtocol(MoreVehiclesPlugin plugin) {
        this.plugin = plugin;
    }

    public void register() {
        var messenger = Bukkit.getMessenger();
        messenger.registerIncomingPluginChannel(plugin, MVSpec.CHANNEL, this);
        messenger.registerOutgoingPluginChannel(plugin, MVSpec.CHANNEL);
        // Pitfall A: also declare via minecraft:register-style channel on join (Bukkit side).
        messenger.registerOutgoingPluginChannel(plugin, "minecraft:register");
    }

    public void unregister() {
        var messenger = Bukkit.getMessenger();
        messenger.unregisterIncomingPluginChannel(plugin, MVSpec.CHANNEL, this);
        messenger.unregisterOutgoingPluginChannel(plugin, MVSpec.CHANNEL);
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!MVSpec.CHANNEL.equals(channel)) return;
        try {
            var src = new MVCodecs.ByteArraySource(message);
            PacketType type = PacketType.fromOrdinal(MVCodecs.readVarInt(src));
            if (type == PacketType.HELLO) {
                markCapable(player.getUniqueId());
                helloHandler.accept(player, message);
            }
            inboundHandler.accept(player, message);
        } catch (RuntimeException e) {
            plugin.getLogger().warning("Malformed MV packet from " + player.getName() + ": " + e.getMessage());
        }
    }

    /** Send a S2C packet; only to mvCapable players (pitfall A). */
    public void send(Player player, byte[] payload) {
        if (!mvCapable.contains(player.getUniqueId())) return;
        player.sendPluginMessage(plugin, MVSpec.CHANNEL, payload);
    }

    /** Build a hello_ack response with protocol/version/TPS/gravity/enhanced flag. */
    public byte[] buildHelloAck(int tps, boolean enhanced) {
        var sink = new MVCodecs.ByteArraySink();
        MVCodecs.writeVarInt(sink, PacketType.HELLO_ACK.ordinalByte());
        MVCodecs.writeVarInt(sink, MVSpec.PROTOCOL_VERSION);
        MVCodecs.writeVarInt(sink, tps);
        MVCodecs.writeDouble(sink, plugin.config().physics().gravityScale());
        sink.write((byte) (enhanced ? 1 : 0));
        return sink.toByteArray();
    }

    public void markCapable(java.util.UUID id) { mvCapable.add(id); }
    public boolean isCapable(java.util.UUID id) { return mvCapable.contains(id); }
    public void onHello(BiConsumer<Player, byte[]> h) { this.helloHandler = h; }
    public void onInbound(BiConsumer<Player, byte[]> h) { this.inboundHandler = h; }
}
