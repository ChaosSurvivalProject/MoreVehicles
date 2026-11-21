package io.mv.server.command;

import io.mv.common.physics.PowerCurve;
import io.mv.server.MoreVehiclesPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.List;

/** /mv command set (MV-SPEC 5.5 / P9): version|couple|uncouple|rerail|dynacurve|reload|blueprint|spawn|debug|ballast. */
public final class MVCommand implements CommandExecutor, TabCompleter {
    private static final List<String> SUBS = List.of(
            "version", "couple", "uncouple", "rerail", "dynacurve", "reload",
            "blueprint", "spawn", "debug", "ballast");

    private final MoreVehiclesPlugin plugin;

    public MVCommand(MoreVehiclesPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("§eMoreVehicles §7- use /mv " + String.join("|", SUBS));
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "version" -> sender.sendMessage(
                    "§a[MV] §7server=Purpur-1.21.11 client=Fabric-1.21.11 protocol=v"
                            + io.mv.common.protocol.MVSpec.PROTOCOL_VERSION
                            + " java=" + System.getProperty("java.version")
                            + " enhanced=" + plugin.protocol().isCapable(sender instanceof org.bukkit.entity.Player
                            ? ((org.bukkit.entity.Player) sender).getUniqueId() : java.util.UUID.randomUUID()));
            case "reload" -> {
                if (!sender.hasPermission("mv.admin")) return noPerm(sender);
                plugin.reload();
                sender.sendMessage("§a[MV] config + physics coefficients reloaded.");
            }
            case "dynacurve" -> sender.sendMessage(
                    "§a[MV] dynacurve: v -> kappa table (computed by common PowerCurve; see docs/TUNING.md)."
                            + " example F_t(10m/s)=" + String.format("%.2f",
                            PowerCurve.tractiveForce(10, PowerCurve.PoweredCar.defaults(), 9.81, false)) + "N");
            case "couple", "uncouple", "rerail", "spawn", "blueprint", "ballast", "debug" ->
                    sender.sendMessage("§7[MV] /" + args[0] + " §e— handled by VehicleRig/TrainSet (P3-P7-F).");
            default -> sender.sendMessage("§c[MV] unknown subcommand: " + args[0]);
        }
        return true;
    }

    private boolean noPerm(CommandSender s) {
        s.sendMessage("§c[MV] no permission (need mv.admin).");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length == 1) {
            return SUBS.stream().filter(s -> s.startsWith(args[0].toLowerCase())).toList();
        }
        return List.of();
    }
}
