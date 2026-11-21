package io.mv.common.physics;

/**
 * The optimized power coefficient function kappa (MV-SPEC 5.3) — the core replacement
 * for vanilla's constant acceleration / constant friction model.
 *
 * <pre>
 *   F_t(v) = min( eta P / max(v, v_min), mu(v) m_drive g ) * (1 - (v/v_max)^n)_+
 *   kappa(v,theta,R,m,F_t) =
 *     [ Σ F_t - Σ R_i(v) - g sin(theta) Σ m_i - g k_curve R^-1 Σ m_i - Σ F_c^|| ]
 *     / ( g Σ m_i^eff )
 *   m_i^eff = m_i + J_i / r_w^2 ;  a_train = kappa g / (1 + lambda (N-1))
 * </pre>
 *
 * Six optimizations over vanilla: (1) differentiable function of v,theta,R,load,N,adhesion,P;
 * (2) low-speed smoothing removes the P/v singularity; (3) per-car resistance & shielding;
 * (4) rotational inertia folded into m_eff; (5) adhesion/power min (wet rail => kappa drop);
 * (6) config-driven, hot-reloadable, LUT-friendly. All pure functions.
 */
public final class PowerCurve {

    /**
     * Tractive force of one powered car, low-speed-singularity free.
     */
    public static double tractiveForce(double v, PoweredCar car, double g, boolean wetRail) {
        double powerTerm = car.efficiency * car.power / Math.max(Math.abs(v), car.vMin);
        double mu = DavisResistance.adhesion(v, wetRail);
        double adhesionCap = mu * car.driveMass * g;
        double cap = Math.min(powerTerm, adhesionCap);
        return cap * DavisResistance.softLimit(v, car.vMax, car.n);
    }

    /** Powered car parameters (config-driven). */
    public record PoweredCar(double power, double efficiency, double driveMass,
                             double vMin, double vMax, double n) {
        public static PoweredCar defaults() {
            return new PoweredCar(1200, 0.85, 10, 0.5, 30, 2);
        }
    }

    /** Aggregated train inputs for the kappa function. */
    public record TrainInputs(
            double[] mass,          // per car mass
            double[] rotationalJ,   // per car wheel rotational inertia J
            double wheelRadius,     // r_w
            DavisResistance.Coeffs[] davis,
            double[] shield,        // per-car shielding factor for C term
            double[] tractive,      // per car F_t (0 for unpowered)
            double couplingParallel,// Σ F_c^|| (signed along travel)
            double slopeSin,        // sin(theta)
            double curveInvR,       // 1/R (0 for straight)
            double curveK,          // k_curve
            double g,
            double lambda           // train resistance growth factor
    ) {}

    /**
     * Compute kappa with explicit speed for Davis resistance. Result is a dimensionless
     * acceleration ratio; a_train = kappa*g/(1+lambda(N-1)).
     */
    public static double kappaAtSpeed(TrainInputs in, double speed) {
        int n = in.mass.length;
        double sumTractive = 0, sumResistance = 0, sumMass = 0, sumMassEff = 0;
        for (int i = 0; i < n; i++) {
            sumTractive += in.tractive[i];
            sumResistance += DavisResistance.resistance(speed, in.davis[i], in.shield[i]);
            sumMass += in.mass[i];
            sumMassEff += in.mass[i] + in.rotationalJ[i] / (in.wheelRadius * in.wheelRadius);
        }
        double slope = in.g * in.slopeSin * sumMass;
        double curve = in.g * in.curveK * in.curveInvR * sumMass;
        double numerator = sumTractive - sumResistance - slope - curve - in.couplingParallel;
        return numerator / (in.g * sumMassEff);
    }

    /** Train acceleration from kappa. */
    public static double acceleration(double kappa, double g, int carCount, double lambda) {
        return kappa * g / (1 + lambda * (carCount - 1));
    }

    /** Equilibrium speed v* where kappa = 0, found by bisection on [lo, hi]. */
    public static double equilibriumSpeed(TrainInputs in, double lo, double hi, int iterations) {
        double flo = kappaAtSpeed(in, lo), fhi = kappaAtSpeed(in, hi);
        if (flo * fhi > 0) return Double.NaN; // no bracketed root
        for (int i = 0; i < iterations; i++) {
            double mid = (lo + hi) * 0.5;
            double fm = kappaAtSpeed(in, mid);
            if (flo * fm <= 0) { hi = mid; fhi = fm; }
            else { lo = mid; flo = fm; }
        }
        return (lo + hi) * 0.5;
    }
}
