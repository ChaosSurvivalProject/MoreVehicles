package io.mv.client.hud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/**
 * Three instrument sets (MV-SPEC 7.3 / 11.11): flight (airspeed, alpha, beta, stall,
 * ground effect, ceiling), marine (depth, draft, GM, Fr, cavitation),
 * general (energy, attitude, consist, derail risk). Configurable / hideable (P8).
 */
public final class Hud {
    public boolean enabled = true;

    /** Called from a HUD render callback. Renders compact readouts from the latest telemetry. */
    public void render(DrawContext ctx, MinecraftClient mc) {
        if (!enabled || mc.player == null) return;
        Telemetry t = current();
        int y = 10;
        draw(ctx, mc, y, String.format("Airspeed %.1f m/s   AoA %.1f deg   Beta %.1f deg",
                t.airspeed(), Math.toDegrees(t.alpha()), Math.toDegrees(t.beta())));
        y += 12;
        draw(ctx, mc, y, String.format("GM %+.2f   Fr %.2f   sigma %.2f   L/V %.2f",
                t.gm(), t.froude(), t.sigma(), t.derailRisk()));
        y += 12;
        draw(ctx, mc, y, String.format("Throttle %.0f%%   Energy %.0f%%   Consist %d",
                t.throttle() * 100, t.energy() * 100, t.cars()));
    }

    private void draw(DrawContext ctx, MinecraftClient mc, int y, String text) {
        ctx.drawTextWithShadow(mc.textRenderer, text, 10, y, 0xE0E0E0);
    }

    /** Latest telemetry; populated by the network/prediction layer in P7/P8. */
    public Telemetry current() {
        return new Telemetry(0, 0, 0, 0.42, 0.28, 2.1, 0.0, 0, 1.0, 1);
    }

    /** Compact telemetry record decoupled from rendering (testable in common terms). */
    public record Telemetry(double airspeed, double alpha, double beta, double gm,
                            double froude, double sigma, double derailRisk,
                            double throttle, double energy, int cars) {}
}
