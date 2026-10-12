package cz.cheatwatch;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.s2c.play.EntityS2CPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;

import java.util.*;

public class Detector {

    private static class Data {
        float lastYaw, lastPitch;
        boolean init;
        double lastX, lastZ;
        // aimbot
        int snapCount; long snapWindowStart;
        // fakelag
        long lastPacketMs; int streak; long burstStart; int burstCount; boolean afterGap;
        // scaffold / fast place
        boolean lastFeetSolid;
        final ArrayDeque<Long> placeTimes = new ArrayDeque<>();
        // cooldowny
        final Map<String, Long> cooldown = new HashMap<>();
    }

    private static final Map<UUID, Data> DATA = new HashMap<>();

    public static void tick(MinecraftClient mc) {
        if (mc.world == null || mc.player == null) { DATA.clear(); return; }
        Set<UUID> seen = new HashSet<>();

        for (AbstractClientPlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player) continue;
            double dist = p.distanceTo(mc.player);
            if (dist > CheatWatchClient.RADIUS) continue;
            // ignoruj NPC / falešné hráče (Hypixel lobby NPC apod.)
            if (p.getUuid().version() != 4) continue;

            seen.add(p.getUuid());
            Data d = DATA.computeIfAbsent(p.getUuid(), k -> new Data());
            String name = p.getName().getString();
            long now = System.currentTimeMillis();

            if (!d.init) {
                d.lastYaw = p.getYaw(); d.lastPitch = p.getPitch();
                d.lastX = p.getX(); d.lastZ = p.getZ();
                d.init = true;
                continue;
            }

            double move = Math.hypot(p.getX() - d.lastX, p.getZ() - d.lastZ);
            float dYaw = Math.abs(MathHelper.wrapDegrees(p.getYaw() - d.lastYaw));
            float dPitch = Math.abs(p.getPitch() - d.lastPitch);
            boolean swingStart = p.handSwinging && p.handSwingTicks == 0;

            checkAimbot(mc, d, p, name, dist, now, dYaw, dPitch, swingStart);
            checkPlacement(mc, d, p, name, dist, now, swingStart);

            d.lastYaw = p.getYaw(); d.lastPitch = p.getPitch();
            d.lastX = p.getX(); d.lastZ = p.getZ();
        }
        DATA.keySet().retainAll(seen);
    }

    /** Voláno z Mixinu při každém příchozím pohybovém paketu. Fakelag/backtrack: dlouhá pauza a pak dávka paketů. */
    public static void onMovePacket(EntityS2CPacket packet) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null || !packet.isPositionChanged()) return;
        Entity e = packet.getEntity(mc.world);
        if (!(e instanceof AbstractClientPlayerEntity p) || p == mc.player) return;
        double dist = p.distanceTo(mc.player);
        if (dist > CheatWatchClient.RADIUS || p.getUuid().version() != 4) return;

        Data d = DATA.computeIfAbsent(p.getUuid(), k -> new Data());
        long now = System.currentTimeMillis();
        long gap = now - d.lastPacketMs;
        d.lastPacketMs = now;

        if (gap >= 350 && d.streak >= 5) {          // plynulý pohyb, pak ticho
            d.afterGap = true; d.burstStart = now; d.burstCount = 1; d.streak = 0;
            return;
        }
        if (d.afterGap) {
            d.burstCount++;
            if (now - d.burstStart > 100) { d.afterGap = false; }
            else if (d.burstCount >= 4) {
                d.afterGap = false;
                flag(mc, d, p.getName().getString(), "Fakelag/Backtrack (dávka paketů po pauze)", dist, now);
            }
            return;
        }
        d.streak = gap < 120 ? d.streak + 1 : 0;
    }

    /** Aimbot / silent aim (viditelná část): ostrý snap rotace těsně u švihu, opakovaně. */
    private static void checkAimbot(MinecraftClient mc, Data d, AbstractClientPlayerEntity p,
                                    String name, double dist, long now,
                                    float dYaw, float dPitch, boolean swing) {
        boolean opponentNear = mc.world.getPlayers().stream()
                .anyMatch(o -> o != p && o.distanceTo(p) < 6.0);
        if (!opponentNear) return;

        if (dYaw > 45f && (swing || p.handSwinging)) {
            if (now - d.snapWindowStart > 3000) { d.snapWindowStart = now; d.snapCount = 0; }
            d.snapCount++;
            if (d.snapCount >= 3) { flag(mc, d, name, "Aimbot/Silent aim (rotation snaps)", dist, now); d.snapCount = 0; }
        }
        // robotická přesnost: velký pitch skok bez změny yaw při švihu
        if (dPitch > 40f && dYaw < 0.5f && swing) {
            flag(mc, d, name, "Aimbot (nepřirozený pitch snap)", dist, now);
        }
    }

    /** Scaffold / fast place: bloky se objevují pod nohama hráče po švihu příliš rychle. */
    private static void checkPlacement(MinecraftClient mc, Data d, AbstractClientPlayerEntity p,
                                       String name, double dist, long now, boolean swing) {
        BlockPos below = BlockPos.ofFloored(p.getX(), p.getY() - 0.5, p.getZ());
        boolean solid = !mc.world.getBlockState(below).isAir();

        if (solid && !d.lastFeetSolid && (swing || p.handSwinging)) {
            d.placeTimes.addLast(now);
        }
        d.lastFeetSolid = solid;

        while (!d.placeTimes.isEmpty() && now - d.placeTimes.peekFirst() > 1000) d.placeTimes.pollFirst();

        int n = d.placeTimes.size();
        if (n >= 6) {
            flag(mc, d, name, "Fast place (" + n + " bloků/s)", dist, now);
            d.placeTimes.clear();
        } else if (n >= 4 && p.isSprinting() && p.getPitch() > 60f) {
            flag(mc, d, name, "Scaffold (sprint bridging)", dist, now);
            d.placeTimes.clear();
        }
    }

    private static void flag(MinecraftClient mc, Data d, String name, String check, double dist, long now) {
        Long last = d.cooldown.get(check);
        if (last != null && now - last < 5000) return;
        d.cooldown.put(check, now);
        CheatWatchClient.alert(mc, name, check, dist);
    }
}
