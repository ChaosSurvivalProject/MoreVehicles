package io.mv.client.network;

import io.mv.common.protocol.MVSpec;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Fabric networking (MV-SPEC 3.1/3.2), Yarn-mapped names for 1.21.11 (verified against
 * minecraft-merged-1.21.11+build.6 and fabric-networking-api-v1 sources):
 * CustomPayload (not CustomPacketPayload), PacketCodec (not StreamCodec), PacketByteBuf.
 *
 * Pitfall A: send C2S hello after JOIN (C2S always reachable) so the server marks us
 * mvCapable and registers the outgoing channel; also declare minecraft:register.
 * Pitfall B: fully register each Id + PacketCodec in playS2C(); fallback Mixin (toggle
 * fallback_mixin) is a documented last resort if the vanilla interception still drops it.
 */
public final class ClientNetwork {
    /** hello payload (C2S): protocol version + capability bits + proxy flag (pitfall D). */
    public record Hello(int protocolVersion, int capabilities, boolean proxyAware)
            implements CustomPayload {
        public static final Id<Hello> ID = new Id<>(Identifier.of("morevehicles", "hello"));
        public static final PacketCodec<PacketByteBuf, Hello> CODEC = CustomPayload.codecOf(
                (p, buf) -> {
                    buf.writeVarInt(p.protocolVersion());
                    buf.writeVarInt(p.capabilities());
                    buf.writeBoolean(p.proxyAware());
                },
                buf -> new Hello(buf.readVarInt(), buf.readVarInt(), buf.readBoolean()));
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    /** hello_ack (S2C) record. ID must match the server channel (single source ProtocolSpec). */
    public record HelloAck(int serverProtocol, int tps, double gravityScale, boolean enhanced)
            implements CustomPayload {
        public static final Id<HelloAck> ID = new Id<>(Identifier.of("morevehicles", "v1"));
        public static final PacketCodec<PacketByteBuf, HelloAck> CODEC = CustomPayload.codecOf(
                (p, buf) -> {
                    buf.writeVarInt(p.serverProtocol());
                    buf.writeVarInt(p.tps());
                    buf.writeDouble(p.gravityScale());
                    buf.writeBoolean(p.enhanced());
                },
                buf -> new HelloAck(buf.readVarInt(), buf.readVarInt(),
                        buf.readDouble(), buf.readBoolean()));
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    /** snapshot (S2C): baseline pos + quaternion (P3/P8). */
    public record Snapshot(int vehicleId, double x, double y, double z,
                           float qx, float qy, float qz, float qw) implements CustomPayload {
        public static final Id<Snapshot> ID = new Id<>(Identifier.of("morevehicles", "snapshot"));
        public static final PacketCodec<PacketByteBuf, Snapshot> CODEC = CustomPayload.codecOf(
                (p, buf) -> {
                    buf.writeVarInt(p.vehicleId());
                    buf.writeDouble(p.x()); buf.writeDouble(p.y()); buf.writeDouble(p.z());
                    buf.writeFloat(p.qx()); buf.writeFloat(p.qy());
                    buf.writeFloat(p.qz()); buf.writeFloat(p.qw());
                },
                buf -> new Snapshot(buf.readVarInt(),
                        buf.readDouble(), buf.readDouble(), buf.readDouble(),
                        buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat()));
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    private boolean helloSent = false;
    private volatile Snapshot lastSnapshot = null;
    public Snapshot lastSnapshot() { return lastSnapshot; }

    public void register() {
        // Register every S2C codec (pitfall B): full Id + PacketCodec in playS2C().
        PayloadTypeRegistry.playS2C().register(HelloAck.ID, HelloAck.CODEC);
        PayloadTypeRegistry.playS2C().register(Snapshot.ID, Snapshot.CODEC);

        ClientPlayNetworking.registerGlobalReceiver(HelloAck.ID, (payload, context) ->
                context.client().execute(() -> System.out.println("[MoreVehicles] hello_ack: server v"
                        + payload.serverProtocol() + " gravity=" + payload.gravityScale()
                        + " enhanced=" + payload.enhanced())));

        ClientPlayNetworking.registerGlobalReceiver(Snapshot.ID, (payload, context) ->
                context.client().execute(() -> {
                    lastSnapshot = payload;
                    vehicles.onSnapshot(payload);
                }));
    }

    /** Called once after joining; sends C2S hello proactively (pitfall A). */
    public void onJoin() {
        if (helloSent) return;
        helloSent = true;
        ClientPlayNetworking.send(new Hello(MVSpec.PROTOCOL_VERSION, 0b111, false));
    }

    /** Sample WASD/mouse -> normalized controls and send input at 20 Hz (P7). */
    public void sampleInput(net.minecraft.client.MinecraftClient mc) {
        // P7: read keybindings, normalize to [-1,1], attach seq + clientTime, send INPUT (20 Hz).
        if (mc.player == null || !helloSent) return;
    }

    private final io.mv.client.render.ClientVehicleEntity vehicles = new io.mv.client.render.ClientVehicleEntity();
    public io.mv.client.render.ClientVehicleEntity vehicles() { return vehicles; }
}
