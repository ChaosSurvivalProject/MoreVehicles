package io.mv.common.physics;

import io.mv.common.math.MathUtil;
import io.mv.common.math.Vec3;

/**
 * Aerodynamic force/moment solver (MV-SPEC 4.2). qbar = 0.5 rho V^2.
 * Coefficients come from a config object (data-driven, no magic numbers).
 * One solver instance per vehicle; can be called each tick with per-part immersion alpha.
 */
public final class AeroSolver {

    /** Aerodynamic derivative set. All from vehicle_configs/*.json. */
    public record Coeffs(
            double area,        // S reference area
            double span,        // b wingspan
            double chord,       // cbar mean aerodynamic chord
            double AR,          // aspect ratio = b^2/S
            double oswald,      // e
            double CL0, double CLalpha, double CLq, double CLde,
            double CD0, double kDelta,
            double CYbeta, double CYdr,
            double Clbeta, double Clp, double Cla,
            double Cm0, double Cmalpha, double Cmq, double Cmde,
            double Cnbeta, double Cnr, double Cndr,
            double alphaStall   // radians
    ) {}

    private final Coeffs c;

    public AeroSolver(Coeffs c) {
        this.c = c;
    }

    /**
     * Compute aero force + moment in BODY frame.
     *
     * @param vRel   relative airflow velocity in BODY frame (v_b - R^T u_flow)
     * @param omega  angular velocity (body frame): p,q,r
     * @param controls normalized [-1,1]: elevator(de), aileron(da), rudder(dr)
     * @param rho    effective air density (already alpha-interpolated by TransitionSolver)
     * @param alphaImmersion immersion ratio for continuous force blending [0,1]
     * @param out    accumulator
     */
    public void accumulate(Vec3 vRel, Vec3 omega, ControlInput controls,
                           double rho, double alphaImmersion, Forces out) {
        if (c.area() <= 0 || rho <= 0) return;

        double V = vRel.length();
        if (V < 1e-6) return;

        double qbar = 0.5 * rho * V * V;

        // Attack & sideslip angles from relative airflow (body axis: +X fwd, +Y up, +Z right).
        double alpha = Math.atan2(-vRel.y(), vRel.x());   // pitch-plane AoA
        double beta = Math.asin(MathUtil.clamp(vRel.z() / V, -1, 1));

        // Non-dimensional rates: phat = p b /2V, qhat = q cbar /2V, rhat = r b /2V.
        double phat = omega.x() * c.span() / (2 * V);
        double qhat = omega.y() * c.chord() / (2 * V);
        double rhat = omega.z() * c.span() / (2 * V);

        double de = controls.elevator, da = controls.aileron, dr = controls.rudder;

        // Lift coefficient with stall: freeze the linear CL at alpha_stall (the peak),
        // then decay monotonically toward a residual. Using the unbounded linear CL times
        // a decay factor would keep growing past stall — physically wrong.
        double CL = c.CL0() + c.CLalpha() * alpha + c.CLq() * qhat + c.CLde() * de;
        double aAbs = Math.abs(alpha);
        if (aAbs > c.alphaStall()) {
            double clAtStall = c.CL0()
                    + c.CLalpha() * Math.copySign(c.alphaStall(), alpha)
                    + c.CLq() * qhat + c.CLde() * de;
            CL = clAtStall * stallDecay(aAbs);
        }

        double CLsq = CL * CL;
        double CD = c.CD0() + CLsq / (Math.PI * c.AR() * c.oswald()) + c.kDelta() * Math.abs(da + de + dr);
        double CY = c.CYbeta() * beta + c.CYdr() * dr;

        double Cl = c.Clbeta() * beta + c.Clp() * phat + c.Cla() * da;
        double Cm = c.Cm0() + c.Cmalpha() * alpha + c.Cmq() * qhat + c.Cmde() * de;
        double Cn = c.Cnbeta() * beta + c.Cnr() * rhat + c.Cndr() * dr;

        // F_aero = qbar S [-CD, CY, -CL]; M_aero = qbar S [b Cl, cbar Cm, b Cn]
        double k = qbar * c.area() * alphaImmersion;
        Vec3 F = new Vec3(-CD * k, CY * k, -CL * k);
        Vec3 M = new Vec3(c.span() * Cl * k, c.chord() * Cm * k, c.span() * Cn * k);
        out.add(F, M);
    }

    /** Continuous post-stall lift decay factor: 1 at alpha_stall -> ~0.15 at 90deg. */
    private double stallDecay(double aAbs) {
        if (aAbs <= c.alphaStall()) return 1.0;
        double excess = aAbs - c.alphaStall();
        double span = Math.PI / 2 - c.alphaStall();
        double t = MathUtil.clamp(excess / span, 0, 1);
        return 1.0 - 0.85 * MathUtil.smoothstep(0, 1, t); // 1 -> 0.15, monotone decreasing
    }

    /** Normalized control surface deflections [-1,1]. */
    public record ControlInput(double elevator, double aileron, double rudder) {
        public static final ControlInput NEUTRAL = new ControlInput(0, 0, 0);
        public ControlInput clamped() {
            return new ControlInput(
                    MathUtil.clamp(elevator, -1, 1),
                    MathUtil.clamp(aileron, -1, 1),
                    MathUtil.clamp(rudder, -1, 1));
        }
    }

    /** Standard light-aircraft-like defaults; override per blueprint. */
    public static Coeffs defaults() {
        return new Coeffs(
                2.0, 4.0, 0.5, 8.0, 0.8,
                0.10, 4.5, 0.0, 0.30,
                0.02, 0.01,
                -0.10, 0.08,
                0.08, 0.02, 0.05,
                -0.02, -0.5, -2.0, -0.6,
                0.06, -0.4, 0.12,
                MathUtil.toRadians(16));
    }
}
