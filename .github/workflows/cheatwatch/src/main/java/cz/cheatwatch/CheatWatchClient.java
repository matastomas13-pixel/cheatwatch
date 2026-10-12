package cz.cheatwatch;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class CheatWatchClient implements ClientModInitializer {
    public static final double RADIUS = 20.0;
    private static final long FLASH_MS = 700;
    private static long flashStart = 0;

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(Detector::tick);

        // Červený záblesk přes celou obrazovku (fade-out)
        HudRenderCallback.EVENT.register((ctx, tickCounter) -> {
            long elapsed = System.currentTimeMillis() - flashStart;
            if (elapsed < 0 || elapsed > FLASH_MS) return;
            float t = 1.0f - (elapsed / (float) FLASH_MS);
            int alpha = (int) (170 * t);
            MinecraftClient mc = MinecraftClient.getInstance();
            int w = mc.getWindow().getScaledWidth();
            int h = mc.getWindow().getScaledHeight();
            ctx.fill(0, 0, w, h, (alpha << 24) | 0xFF0000);
        });
    }

    public static void alert(MinecraftClient mc, String player, String check, double dist) {
        if (mc.player == null) return;
        mc.player.sendMessage(Text.literal("[CheatWatch] ").formatted(Formatting.DARK_RED, Formatting.BOLD)
                .append(Text.literal(player + " může cheatovat: " + check
                        + String.format(" (%.1f bloků)", dist)).formatted(Formatting.RED)), false);
        flashStart = System.currentTimeMillis();
    }
}
