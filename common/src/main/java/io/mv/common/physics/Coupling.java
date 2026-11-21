package io.mv.common.physics;

import io.mv.common.math.MathUtil;

/**
 * Spring-damper coupling between adjacent cars (MV-SPEC 5.2):
 *   F_c = k((s_{i+1}-s_i) - L0) + c(v_{i+1}-v_i),  |F_c| <= F_hook_max
 * Exceeding the limit breaks the hook (caller splits the train + broadcasts event).
 */
public final class Coupling {
    private final double k;          // spring stiffness
    private final double c;          // damping
    private final double restLength; // L0
    private final double hookMax;    // F_hook_max

    public Coupling(double k, double c, double restLength, double hookMax) {
        this.k = k;
        this.c = c;
        this.restLength = restLength;
        this.hookMax = hookMax;
    }

    /** Force from the perspective of car i (positive = pull forward toward car i+1). */
    public double force(double sI, double sNext, double vI, double vNext) {
        double raw = k * ((sNext - sI) - restLength) + c * (vNext - vI);
        return MathUtil.clamp(raw, -hookMax, hookMax);
    }

    public boolean broken(double sI, double sNext, double vI, double vNext) {
        double raw = k * ((sNext - sI) - restLength) + c * (vNext - vI);
        return Math.abs(raw) > hookMax;
    }

    public double hookMax() { return hookMax; }
}
