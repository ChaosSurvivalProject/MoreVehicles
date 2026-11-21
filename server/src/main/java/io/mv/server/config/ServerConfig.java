package io.mv.server.config;

import io.mv.common.physics.DerailmentModel;
import io.mv.common.physics.PhysicsConfig;
import org.bukkit.configuration.file.FileConfiguration;

/** Immutable snapshot of config.yml, hot-reloadable via /mv reload. No magic numbers in logic. */
public record ServerConfig(
        PhysicsConfig physics,
        DerailmentModel.Config derailment,
        double kCurve,
        double trainLambda,
        double wheelRadius,
        double couplingK, double couplingC, double couplingRest, double hookMax,
        int snapshotHz,
        int maxVoxels,
        boolean fallbackMixin,
        boolean proxyAware
) {
    public double dt() { return physics.dt(); }

    public static ServerConfig from(FileConfiguration c) {
        boolean hardcore = c.getBoolean("physics.hardcore", false);
        PhysicsConfig pc = hardcore ? PhysicsConfig.hardcore() : new PhysicsConfig(
                c.getDouble("physics.gravity_scale", 20.0),
                c.getDouble("physics.air_density_sea", 1.2e-3),
                c.getDouble("physics.scale_height", 900.0),
                c.getDouble("physics.water_density", 1.0),
                c.getDouble("physics.lava_density", 2.7),
                c.getDouble("physics.vapor_pressure", 2.34e-3),
                c.getDouble("physics.p_static", 101.325),
                c.getDouble("physics.surface_epsilon", 0.25),
                c.getDouble("physics.slam_cap", 40.0),
                c.getInt("physics.substeps_on_entry", 4),
                c.getDouble("physics.linear_damping", 0.0),
                c.getDouble("physics.angular_damping", 0.0),
                c.getString("physics.integrator", "semi_implicit"),
                0.05);

        String mode = c.getString("derailment.mode", "probabilistic");
        DerailmentModel.Mode dm = switch (mode.toLowerCase()) {
            case "off" -> DerailmentModel.Mode.OFF;
            case "hardcore" -> DerailmentModel.Mode.HARDCORE;
            default -> DerailmentModel.Mode.PROBABILISTIC;
        };

        return new ServerConfig(
                pc,
                new DerailmentModel.Config(dm,
                        Math.toRadians(c.getDouble("derailment.flange_angle", 70)),
                        c.getDouble("derailment.friction", 0.15),
                        c.getDouble("derailment.nadal_limit", 0.8),
                        c.getDouble("derailment.gamma_cant", 2.0),
                        c.getBoolean("derailment.wagner", false)),
                c.getDouble("train.curve_k", 0.5),
                c.getDouble("train.lambda", 0.1),
                c.getDouble("train.wheel_radius", 0.4),
                c.getDouble("coupling.k", 100), c.getDouble("coupling.c", 10),
                c.getDouble("coupling.rest_length", 2.0), c.getDouble("coupling.hook_max", 50),
                c.getInt("network.snapshot_hz", 15),
                c.getInt("assembly.max_voxels", 512),
                c.getBoolean("network.fallback_mixin", false),
                c.getBoolean("network.proxy_aware", false));
    }
}
