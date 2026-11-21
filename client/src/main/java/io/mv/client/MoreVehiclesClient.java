package io.mv.client;

import io.mv.client.network.ClientNetwork;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;

/**
 * Fabric 1.21.11 client entry point (environment: client). Presentation only:
 * input sampling, prediction, interpolation, rendering, HUD. Never authoritative physics.
 */
public final class MoreVehiclesClient implements ClientModInitializer {
    public static final String MOD_ID = "morevehicles";

    private final ClientNetwork network = new ClientNetwork();
    private static volatile MoreVehiclesClient instance;

    @Override
    public void onInitializeClient() {
        instance = this;
        network.register();
        // Pitfall A: proactively send C2S hello after JOIN so Bukkit marks us mvCapable.
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> network.onJoin());
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        System.out.println("[MoreVehicles] client initialized (protocol v"
                + io.mv.common.protocol.MVSpec.PROTOCOL_VERSION + ")");
    }

    private void tick(MinecraftClient mc) {
        if (mc.world == null || mc.player == null) return;
        network.sampleInput(mc);
        network.vehicles().interpolate();
        // HUD rendered via Hud overlay hook (P8).
    }

    public static MoreVehiclesClient instance() { return instance; }
    public ClientNetwork network() { return network; }
}
