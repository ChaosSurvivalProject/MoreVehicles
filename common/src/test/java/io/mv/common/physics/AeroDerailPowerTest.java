package io.mv.common.physics;

import io.mv.common.math.MathUtil;
import io.mv.common.math.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AeroDerailPowerTest {

    @Test
    void liftDecreasesPastStall() {
        var coeffs = AeroSolver.defaults();
        AeroSolver solver = new AeroSolver(coeffs);
        // Keep |V| fixed so qbar is identical; only change AoA via direction.
        double v = 20, rho = 1.2e-3;
        java.util.function.DoubleFunction<Double> clAt = deg -> {
            double a = MathUtil.toRadians(deg);
            Vec3 flow = new Vec3(v * Math.cos(a), -v * Math.sin(a), 0); // |V|=v, alpha=deg
            Forces f = new Forces();
            solver.accumulate(flow, Vec3.ZERO, AeroSolver.ControlInput.NEUTRAL, rho, 1.0, f);
            double qS = 0.5 * rho * v * v * coeffs.area();
            return -f.force.z() / qS; // CL = -Fz/(qbar S)
        };
        double clNearStall = clAt.apply(15);  // just below alpha_stall=16deg (peak)
        double clPostStall = clAt.apply(55);  // well past stall
        assertTrue(clPostStall < clNearStall,
                "CL must drop past stall: near=" + clNearStall + " post=" + clPostStall);
        // And it must be monotone decreasing across several post-stall angles.
        assertTrue(clAt.apply(55) > clAt.apply(80));
    }

    @Test
    void kappaEquilibriumMatchesBisection() {
        // Build a 1-car train: 1 powered car, flat track, find v* where kappa=0.
        PowerCurve.PoweredCar pc = PowerCurve.PoweredCar.defaults();
        double g = 9.81;
        int n = 1;
        double[] mass = {pc.driveMass()};
        double[] J = {2.0};
        double rw = 0.4;
        var davis = new DavisResistance.Coeffs[]{new DavisResistance.Coeffs(0.5, 0.02, 0.01)};
        double[] shield = {1.0};

        // Self-consistent kappa: tractive force MUST be recomputed at each speed,
        // otherwise the bisection root is inconsistent with the physics.
        java.util.function.DoubleFunction<Double> kappaAt = speed -> {
            double[] tractive = {PowerCurve.tractiveForce(speed, pc, g, false)};
            var in = new PowerCurve.TrainInputs(mass, J, rw, davis, shield, tractive,
                    0, 0, 0, 0, g, 0.1);
            return PowerCurve.kappaAtSpeed(in, speed);
        };

        // Bisect for kappa=0 (self-consistent equilibrium).
        double lo = 0.1, hi = 60.0;
        assertTrue(kappaAt.apply(lo) > 0 && kappaAt.apply(hi) < 0, "kappa must bracket zero");
        for (int i = 0; i < 80; i++) {
            double mid = (lo + hi) / 2;
            if (kappaAt.apply(mid) > 0) lo = mid; else hi = mid;
        }
        double vStar = (lo + hi) / 2;
        assertTrue(Math.abs(kappaAt.apply(vStar)) < 1e-3,
                "kappa at equilibrium not ~0: " + kappaAt.apply(vStar));
        assertTrue(vStar > 1 && vStar < 40, "unexpected v*=" + vStar);
    }

    @Test
    void wetRailReducesTraction() {
        PowerCurve.PoweredCar pc = PowerCurve.PoweredCar.defaults();
        double dry = PowerCurve.tractiveForce(2.0, pc, 9.81, false);
        double wet = PowerCurve.tractiveForce(2.0, pc, 9.81, true);
        assertTrue(wet < dry, "wet rail must reduce adhesion cap: wet=" + wet + " dry=" + dry);
    }

    @Test
    void hardcoreDerailsAtOrAboveLimit() {
        var cfg = new DerailmentModel.Config(DerailmentModel.Mode.HARDCORE,
                MathUtil.toRadians(70), 0.15, 0.8, 2.0, false);
        DerailmentModel m = new DerailmentModel(cfg);
        assertTrue(m.shouldDerail(1.0, 0, 42L));
        assertTrue(m.shouldDerail(1.2, 100, 7L));
        assertFalse(m.shouldDerail(0.9, 0, 99L));
    }

    @Test
    void probabilisticDerailmentIsReproducible() {
        var cfg = DerailmentModel.Config.defaults();
        DerailmentModel m = new DerailmentModel(cfg);
        long seed = 123456789L;
        boolean a = m.shouldDerail(1.05, 50, seed);
        boolean b = m.shouldDerail(1.05, 50, seed);
        assertEquals(a, b, "same seed must give same decision");
        assertFalse(m.shouldDerail(0.5, 0, seed), "r<1 must never derail in probabilistic mode");
    }

    @Test
    void couplingBreaksPastHookLimit() {
        Coupling c = new Coupling(100, 10, 2.0, 50);
        assertFalse(c.broken(0, 2.0, 5, 5));
        assertTrue(c.broken(0, 5.0, 5, 5), "large stretch must break hook");
        assertEquals(50, c.force(0, 10, 0, 0), 1e-9); // clamped
    }
}
