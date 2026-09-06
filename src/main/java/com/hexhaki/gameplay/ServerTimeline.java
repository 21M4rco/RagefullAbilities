package com.hexhaki.gameplay;

import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import com.hexhaki.HexHaki;

import java.util.*;

@Mod.EventBusSubscriber(modid = HexHaki.MODID)
public final class ServerTimeline {
    private static final Map<ServerLevel, NavigableMap<Long, List<Runnable>>> QUEUES = new WeakHashMap<>();
    private ServerTimeline() {}

    public static void later(ServerLevel level, int ticks, Runnable action) {
        QUEUES.computeIfAbsent(level, k -> new TreeMap<>())
                .computeIfAbsent(level.getGameTime() + Math.max(1, ticks), k -> new ArrayList<>()).add(action);
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.level instanceof ServerLevel level)) return;
        NavigableMap<Long, List<Runnable>> q = QUEUES.get(level);
        if (q == null) return;
        long now = level.getGameTime();
        while (!q.isEmpty() && q.firstKey() <= now) {
            List<Runnable> work = q.pollFirstEntry().getValue();
            for (Runnable r : work) {
                try {
                    r.run();
                } catch (RuntimeException failure) {
                    HexHaki.LOGGER.error("A scheduled Haki action failed", failure);
                }
            }
        }
    }
}
