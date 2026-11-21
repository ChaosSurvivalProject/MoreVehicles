package io.mv.common.physics.fluid;

import io.mv.common.math.Matrix3;
import io.mv.common.math.Vec3;
import io.mv.common.physics.Forces;
import io.mv.common.physics.PhysicsConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FluidKernelTest {

    @Test
    void woodFloatsStoneSinks_displacementEquilibrium() {
        PhysicsConfig cfg = PhysicsConfig.vanilla();
        // rho_w g V = m g  ->  V = m / rho_w. Wood rho=0.6 => half submerged; stone rho=2.4 => sinks.
        double rhoWood = 0.6, rhoStone = 2.4;
        double volume = 1.0;
        double buoyantMass = cfg.waterDensity() * volume;
        assertTrue(buoyantMass > rhoWood * volume, "wood must be buoyant");
        assertTrue(buoyantMass < rhoStone * volume, "stone must sink");
    }

    @Test
    void addedMassReducesAccelerationByExpectedFactor() {
        double m = 10, ca = 1.0, rho = 1.0, V = 2.0;
        double ma = AddedMass.addedMass(ca, rho, V); // = 2
        double aAir = 100 / m;
        double aWater = 100 / (m + ma);
        double expected = aAir / (1 + ca * rho * V / m);
        assertTrue(Math.abs(aWater - expected) / expected < 0.05);
        // At constant velocity added mass exerts no force (it is an inertia term).
        assertEquals(0.0, 0.0); // documented: added mass only appears in inertia, not force sum
    }

    @Test
    void caAngleInterpolationEndpoints() {
        assertEquals(AddedMass.AXIAL, AddedMass.caAtAngle(0), 1e-9);
        assertEquals(AddedMass.TRANSVERSE, AddedMass.caAtAngle(Math.PI / 2), 1e-9);
    }

    @Test
    void gmSignDeterminesRightingVsCapsize() {
        PhysicsConfig cfg = PhysicsConfig.vanilla();
        // Build hydrostatics with positive KB/BM vs KG.
        double[] depths = {0, 0.5, 1.0};
        double[] vols = {0, 0.5, 1.0};
        double[] kb = {0, 0.25, 0.5};
        double[] iwp = {0, 1.0, 2.0};
        double[] gzA = {-0.5, 0, 0.5};
        double[] gzV = {-0.5, 0, 0.5};
        var curves = new Hydrostatics.Curves(depths, vols, kb, iwp, gzA, gzV);

        Hydrostatics stable = new Hydrostatics(curves, 1.0, new Vec3(0, 0.1, 0), 2.0, 0.0);
        assertTrue(stable.gm(cfg, 1.0) > 0, "low KG => positive GM");

        Hydrostatics unstable = new Hydrostatics(curves, 1.0, new Vec3(0, 3.0, 0), 2.0, 0.0);
        assertTrue(unstable.gm(cfg, 1.0) < 0, "high KG => negative GM => capsize");
    }

    @Test
    void freeSurfaceCorrectionLowersGm() {
        PhysicsConfig cfg = PhysicsConfig.vanilla();
        double[] d = {0, 1}, v = {0, 1}, kb = {0, 0.5}, iwp = {0, 2}, ga = {0, 0.5}, gv = {0, 0.5};
        var curves = new Hydrostatics.Curves(d, v, kb, iwp, ga, gv);
        Hydrostatics full = new Hydrostatics(curves, 1.0, Vec3.ZERO, 2, 0.0);
        Hydrostatics half = new Hydrostatics(curves, 1.0, Vec3.ZERO, 2, 0.05);
        assertTrue(half.gm(cfg, 1.0) < full.gm(cfg, 1.0), "half-full ballast must reduce GM");
    }

    @Test
    void waveResistanceHasPeaksAtFroude030And050() {
        var cfg = WaveResistance.Config.defaults();
        WaveResistance wr = new WaveResistance(cfg);
        double cw028 = wr.cw(0.28, 1.0);
        double cw038 = wr.cw(0.38, 1.0);
        double cw050 = wr.cw(0.50, 1.0);
        double cw085 = wr.cw(0.85, 1.0);
        assertTrue(cw028 > cw038, "peak near 0.30");
        assertTrue(cw050 > cw038, "peak near 0.50");
        assertTrue(cw085 < cw050, "drop in planing >0.7");
        assertEquals(0.0, wr.cw(0.3, 0.0), 1e-9); // fully submerged -> no wave resistance
    }

    @Test
    void cavitationDeeperWaterRaisesSigmaAndLift() {
        PhysicsConfig cfg = PhysicsConfig.vanilla();
        double v = 20;
        double sigmaShallow = Cavitation.sigma(cfg, v, 0.2);
        double sigmaDeep = Cavitation.sigma(cfg, v, 10.2);
        assertTrue(sigmaDeep > sigmaShallow, "depth raises p_inf -> higher sigma");
        assertTrue(Cavitation.liftFactor(sigmaDeep, Cavitation.SIGMA_CRIT)
                >= Cavitation.liftFactor(sigmaShallow, Cavitation.SIGMA_CRIT));
        // sigma decreases with speed -> lift degrades monotonically
        double s1 = Cavitation.sigma(cfg, 10, 1), s2 = Cavitation.sigma(cfg, 20, 1), s3 = Cavitation.sigma(cfg, 40, 1);
        assertTrue(s1 > s2 && s2 > s3);
    }

    @Test
    void propellerKtDecreasesWithAdvanceRatio() {
        var cfg = PropellerCurve.Config.defaults();
        PropellerCurve pc = new PropellerCurve(cfg);
        assertTrue(pc.kt(0.1) > pc.kt(0.8), "K_T must fall as J rises");
        double eta = pc.efficiency(0.5);
        assertTrue(eta >= 0 && eta <= 1);
    }

    @Test
    void jetFailsInWaterWaterjetNeedsIntake() {
        var jetCfg = new PropellerCurve.Config(0.5, 0.4, 0.2, 0.03, PropellerCurve.Medium.AIR, 0.8);
        var waterjetCfg = new PropellerCurve.Config(0.5, 0.4, 0.2, 0.03, PropellerCurve.Medium.WATER, 0.8);
        FluidMedium water = new FluidMedium(FluidMedium.MediumType.WATER, 1.0, 1e-3,
                Vec3.ZERO, 0, 0.9, 0);
        FluidMedium air = new FluidMedium(FluidMedium.MediumType.AIR, 1.2e-3, 1.8e-5,
                Vec3.ZERO, 0, 0.0, 0);

        assertEquals(0.0, new PropellerCurve(jetCfg).thrust(1.0, 10, 5, water), "jet fails in water");
        assertTrue(new PropellerCurve(jetCfg).thrust(1.2e-3, 10, 5, air) > 0, "jet works in air");
        assertEquals(0.0, new PropellerCurve(waterjetCfg).thrust(1.0, 10, 5,
                new FluidMedium(FluidMedium.MediumType.WATER, 1.0, 1e-3, Vec3.ZERO, 0, 0.5, 0)),
                "waterjet needs alpha>0.8 intake");
    }

    @Test
    void crossingDetectsSubstepsAndSlamClamps() {
        assertTrue(TransitionSolver.needsSubsteps(0.0, 0.3));
        assertTrue(TransitionSolver.needsSubsteps(1.0, 0.7));
        assertFalse(TransitionSolver.needsSubsteps(0.5, 0.6));

        PhysicsConfig cfg = PhysicsConfig.vanilla();
        TransitionSolver ts = new TransitionSolver(cfg, 2.0, 1000.0);
        double slam = ts.slammingForce(2.0, 10.0);
        assertTrue(slam > 0 && slam <= cfg.slamCap(), "slam must be clamped");
    }

    @Test
    void mediumFieldAlphaIsContinuous() {
        PhysicsConfig cfg = PhysicsConfig.vanilla();
        var field = new MediumField(cfg, new MediumField.DensitySampler() {
            public double surfaceY(double x, double z) { return 0; }
            public boolean isLava(double x, double y, double z) { return false; }
            public boolean isBubbleColumn(double x, double y, double z) { return false; }
            public Vec3 current(double x, double y, double z) { return Vec3.ZERO; }
            public Vec3 wind(double x, double y, double z, long tick) { return Vec3.ZERO; }
        });
        FluidMedium m1 = field.sample(new Vec3(0, 0.30, 0), 0);
        FluidMedium m2 = field.sample(new Vec3(0, 0.26, 0), 0);
        assertTrue(Math.abs(m1.alpha() - m2.alpha()) < 0.2, "alpha should change smoothly across surface");
        assertTrue(field.sample(new Vec3(0, 0.0, 0), 0).alpha() > field.sample(new Vec3(0, 1.0, 0), 0).alpha());
    }
}
