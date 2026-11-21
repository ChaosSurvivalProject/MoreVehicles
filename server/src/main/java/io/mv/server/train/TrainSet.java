package io.mv.server.train;

import io.mv.common.physics.Coupling;
import io.mv.common.physics.DavisResistance;
import io.mv.common.physics.PowerCurve;

import java.util.ArrayList;
import java.util.List;

/**
 * A coupled consist (MV-SPEC 5.2/5.3): cars with arc-length s, v, mass, Davis coeffs.
 * Coupling force propagates from the powered car outward (not averaged over the train).
 * Changing coupling beyond hookMax splits the consist and the caller broadcasts an event.
 */
public final class TrainSet {
    public static final class Car {
        public int id;
        public double s, v, mass;
        public DavisResistance.Coeffs davis;
        public double shield = 1.0;
        public PowerCurve.PoweredCar powered; // null for unpowered cars
        public double rotationalJ = 2.0;

        public Car(int id, double s, double mass, DavisResistance.Coeffs davis) {
            this.id = id; this.s = s; this.mass = mass; this.davis = davis;
        }
    }

    private final List<Car> cars = new ArrayList<>();
    private final Coupling coupling;
    private final double g;

    public TrainSet(Coupling coupling, double g) {
        this.coupling = coupling;
        this.g = g;
    }

    public void add(Car car) { cars.add(car); }
    public List<Car> cars() { return List.copyOf(cars); }
    public int size() { return cars.size(); }

    /**
     * Compute kappa for the whole consist at current speed (MV-SPEC 5.3, six optimizations).
     * Coupling forces are accumulated along the chain from powered cars to both ends.
     */
    public double kappa(double slopeSin, double curveInvR, double curveK, boolean wetRail) {
        int n = cars.size();
        if (n == 0) return 0;
        double speed = Math.abs(cars.get(0).v);

        double[] mass = new double[n], J = new double[n], shield = new double[n], tractive = new double[n];
        DavisResistance.Coeffs[] davis = new DavisResistance.Coeffs[n];
        double couplingParallel = 0;

        for (int i = 0; i < n; i++) {
            Car c = cars.get(i);
            mass[i] = c.mass; J[i] = c.rotationalJ; shield[i] = c.shield; davis[i] = c.davis;
            tractive[i] = c.powered != null
                    ? PowerCurve.tractiveForce(speed, c.powered, g, wetRail) : 0;
            if (i > 0) {
                Car prev = cars.get(i - 1);
                couplingParallel += coupling.force(prev.s, c.s, prev.v, c.v);
            }
        }

        var in = new PowerCurve.TrainInputs(mass, J, 0.4, davis, shield, tractive,
                couplingParallel, slopeSin, curveInvR, curveK, g, 0.1);
        return PowerCurve.kappaAtSpeed(in, speed);
    }

    /** Detect a broken hook anywhere in the chain; returns index of the broken link or -1. */
    public int findBrokenLink() {
        for (int i = 0; i < cars.size() - 1; i++) {
            Car a = cars.get(i), b = cars.get(i + 1);
            if (coupling.broken(a.s, b.s, a.v, b.v)) return i;
        }
        return -1;
    }

    /** Split at a broken link; returns the second consist (this keeps the first). */
    public TrainSet splitAt(int link, double g) {
        TrainSet tail = new TrainSet(coupling, g);
        for (int i = link + 1; i < cars.size(); i++) tail.add(cars.get(i));
        cars.subList(link + 1, cars.size()).clear();
        return tail;
    }
}
