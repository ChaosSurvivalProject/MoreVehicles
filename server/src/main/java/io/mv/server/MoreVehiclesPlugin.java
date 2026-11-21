package io.mv.server;

import io.mv.common.protocol.MVSpec;
import io.mv.server.command.MVCommand;
import io.mv.server.config.ServerConfig;
import io.mv.server.protocol.MVProtocol;
import io.mv.server.world.VehicleWorld;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Server entry point. Purpur 1.21.11 (Bukkit/Paper plugin, paper-plugin.yml).
 * Server-authoritative physics; Fabric client only presents. No hybrid/Arclight.
 */
public final class MoreVehiclesPlugin extends JavaPlugin {

    private static MoreVehiclesPlugin instance;
    private ServerConfig config;
    private VehicleWorld vehicleWorld;
    private MVProtocol protocol;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        config = ServerConfig.from(getConfig());

        protocol = new MVProtocol(this);
        protocol.register();

        vehicleWorld = new VehicleWorld(this, config, protocol);
        vehicleWorld.start();

        var cmd = getCommand("mv");
        if (cmd != null) {
            MVCommand executor = new MVCommand(this);
            cmd.setExecutor(executor);
            cmd.setTabCompleter(executor);
        }

        getLogger().info("MoreVehicles enabled - protocol v" + MVSpec.PROTOCOL_VERSION
                + " channel " + MVSpec.CHANNEL + " (server-authoritative, dt=" + config.dt() + "s)");
    }

    @Override
    public void onDisable() {
        if (vehicleWorld != null) vehicleWorld.stop();
        if (protocol != null) protocol.unregister();
        getLogger().info("MoreVehicles disabled.");
    }

    public void reload() {
        reloadConfig();
        config = ServerConfig.from(getConfig());
        if (vehicleWorld != null) vehicleWorld.applyConfig(config);
        getLogger().info("MoreVehicles config reloaded (/mv reload).");
    }

    public static MoreVehiclesPlugin get() { return instance; }
    public ServerConfig config() { return config; }
    public VehicleWorld world() { return vehicleWorld; }
    public MVProtocol protocol() { return protocol; }
}
