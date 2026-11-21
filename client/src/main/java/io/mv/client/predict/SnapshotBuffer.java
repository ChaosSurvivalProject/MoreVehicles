package io.mv.client.predict;

import io.mv.common.math.Quaternion;
import io.mv.common.math.Vec3;

import java.util.ArrayDeque;

/**
 * 100 ms snapshot buffer with lerp + slerp interpolation (MV-SPEC 3.5 / 7.2 / P8).
 * Exponential smoothing for误差>0.5 block correction (never teleport).
 */
public final class SnapshotBuffer {
    public record Sample(long timeMs, Vec3 pos, Quaternion quat) {}

    private final ArrayDeque<Sample> buffer = new ArrayDeque<>();
    private final long delayMs = 100;
    private final double correctionThreshold = 0.5;
    private final long correctionMs = 150;

    public void push(Sample s) {
        buffer.addLast(s);
        long cutoff = System.currentTimeMillis() - delayMs - 2000;
        while (!buffer.isEmpty() && buffer.peekFirst().timeMs() < cutoff) buffer.pollFirst();
    }

    /** Interpolated pose at (now - delayMs); lerp position, shortest-arc slerp attitude. */
    public Sample sample() {
        if (buffer.isEmpty()) return null;
        long target = System.currentTimeMillis() - delayMs;
        Sample prev = buffer.peekFirst(), next = buffer.peekLast();
        for (Sample s : buffer) {
            if (s.timeMs() >= target) { next = s; break; }
            prev = s;
        }
        if (prev == next) return prev;
        long span = next.timeMs() - prev.timeMs();
        double t = span <= 0 ? 0 : (double) (target - prev.timeMs()) / span;
        t = io.mv.common.math.MathUtil.clamp(t, 0, 1);
        return new Sample(target, Vec3.lerp(prev.pos(), next.pos(), t),
                Quaternion.slerp(prev.quat(), next.quat(), t));
    }

    /** Smoothly correct a predicted pose toward the authoritative sample (no瞬移). */
    public Vec3 correctPosition(Vec3 predicted, Vec3 authoritative, double dtSeconds) {
        double err = predicted.sub(authoritative).length();
        double rate = err > correctionThreshold ? 1.0 / (correctionMs / 1000.0) : 1.0 / 0.15;
        double t = io.mv.common.math.MathUtil.clamp(rate * dtSeconds, 0, 1);
        return Vec3.lerp(predicted, authoritative, t);
    }
}
