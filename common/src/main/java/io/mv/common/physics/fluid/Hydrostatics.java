package io.mv.common.physics.fluid;

import io.mv.common.math.MathUtil;
import io.mv.common.math.Vec3;
import io.mv.common.physics.Forces;
import io.mv.common.physics.PhysicsConfig;

/**
 * Buoyancy, center of buoyancy and initial stability (MV-SPEC 11.3).
 *
 * <pre>
 *   F_b = rho_w g V_sub y_hat, applied at B
 *   M_b = (B - G) x F_b
 *   BM = I_wp / V;  GM = KB + BM - KG
 *   M_right = rho_w g V GM sin(phi)   (|phi| <= ~10deg)
 *   GM_corr = GM - sum(FSM)/Delta     (free-surface correction)
 * </pre>
 *
 * Static-hydraulic curves (V(h), B(h), A_wp(h), I_wp(h), GZ(phi)) are precomputed
 * at assembly time as LUTs (0.05 step) and only looked up at runtime — zero per-tick recompute.
 */
public final class Hydrostatics {

    /** Precomputed hydrostatic curves, keyed by draft/depth. */
    public record Curves(double[] depths, double[] volumes, double[] kb,
                         double[] waterplaneI, double[] gzAngles, double[] gzValues) {
        public double volumeAt(double depth) { return MathUtil.lookup(depths, volumes, depth); }
        public double kbAt(double depth) { return MathUtil.lookup(depths, kb, depth); }
        public double iwpAt(double depth) { return MathUtil.lookup(depths, waterplaneI, depth); }
        public double gzAt(double angle) { return MathUtil.lookup(gzAngles, gzValues, angle); }
    }

    private final Curves curves;
    private final double mass;
    private final Vec3 centerOfGravity; // body frame relative to COM reference (we use COM as origin => ~0)
    private final double waterlineArea; // A_wp
    private final double freeSurfaceMoment; // sum FSM

    public Hydrostatics(Curves curves, double mass, Vec3 centerOfGravity,
                        double waterlineArea, double freeSurfaceMoment) {
        this.curves = curves;
        this.mass = mass;
        this.centerOfGravity = centerOfGravity;
        this.waterlineArea = waterlineArea;
        this.freeSurfaceMoment = freeSurfaceMoment;
    }

    /** Displacement = mass / rho_water (from equilibrium rho_w g V = m g at neutral float). */
    public double displacement(PhysicsConfig cfg) {
        return mass / cfg.waterDensity();
    }

    /** Metacentric height GM = KB + BM - KG, with free-surface correction. */
    public double gm(PhysicsConfig cfg, double depth) {
        double V = Math.max(1e-6, curves.volumeAt(depth));
        double kb = curves.kbAt(depth);
        double bm = curves.iwpAt(depth) / V;
        double kg = centerOfGravity.y();
        double gm = kb + bm - kg;
        double delta = mass; // displacement weight in mass units
        return gm - freeSurfaceMoment / Math.max(1e-6, delta);
    }

    /**
     * Accumulate buoyancy force at B and righting moment. Positive GM => self-righting;
     * negative GM => capsize (moment flips sign naturally through sin(phi)).
     *
     * @param depth      submerged depth used to look up V and B
     * @param rollAngle  heel angle phi (radians)
     * @param alpha      immersion ratio; scales buoyancy continuously
     * @param medium     sampled medium (uses rho)
     */
    public void accumulate(PhysicsConfig cfg, double depth, double rollAngle, double alpha,
                           FluidMedium medium, Forces out) {
        if (alpha <= 0) return;
        double V = curves.volumeAt(depth) * alpha;
        double rho = Math.max(cfg.waterDensity() * alpha, medium.rho());

        // Buoyancy acts in WORLD +Y. The vehicle rig rotates world->body before handing
        // forces to the body-frame integrator; here we expose world-frame F and moment.
        double FbMag = rho * cfg.gravityScale() * V;
        Vec3 Fworld = new Vec3(0, FbMag, 0);

        Vec3 centerB = new Vec3(0, curves.kbAt(depth), 0); // B in body frame (relative to COM)
        Vec3 G = centerOfGravity;
        Vec3 rArm = centerB.sub(G);

        // Righting moment (Bouguer small-angle, GZ table for large angles).
        double gm = gm(cfg, depth);
        double Mright = rho * cfg.gravityScale() * V * gm * Math.sin(rollAngle);
        double gz = curves.gzAt(rollAngle);
        if (Math.abs(rollAngle) > MathUtil.toRadians(10) && gz != 0) {
            Mright = rho * cfg.gravityScale() * V * gz;
        }

        // Moment from buoyancy acting at B: (B-G) x F_b (world-frame cross, approximate y-up).
        Vec3 buoyMomentWorld = rArm.cross(Fworld);
        // Righting moment acts about body +X (roll axis).
        Vec3 rightingMoment = new Vec3(Mright, 0, 0);

        // Expose via out; caller rotates world->body if needed. We store world-frame
        // buoyancy into force and sum moments (documented convention).
        out.addForce(Fworld);
        out.addMoment(buoyMomentWorld.add(rightingMoment));
    }

    public Curves curves() { return curves; }
}
