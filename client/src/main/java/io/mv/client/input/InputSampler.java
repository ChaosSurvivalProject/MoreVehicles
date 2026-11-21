package io.mv.client.input;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;

/** Samples WASD/mouse/keys into normalized [-1,1] controls at 20 Hz (MV-SPEC 7 / P7). */
public final class InputSampler {
    /** Normalized control frame with sequence number + client time for server dedup/replay. */
    public record Controls(float throttle, float pitch, float yaw, float roll,
                           boolean brake, int seq, long clientTime) {}

    private int sequence = 0;

    public Controls sample(MinecraftClient mc) {
        // P7: map KeyBinding states to normalized axes. Skeleton returns neutral;
        // keybindings and HUD are configurable via resource pack (MV-SPEC 7.3).
        return new Controls(0, 0, 0, 0, false, ++sequence, System.currentTimeMillis());
    }

    public static float axis(boolean positive, boolean negative) {
        return (positive ? 1f : 0f) - (negative ? 1f : 0f);
    }
}
