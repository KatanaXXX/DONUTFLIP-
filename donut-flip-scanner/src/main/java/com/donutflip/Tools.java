package com.donutflip;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Small local helpers: session profit log and countdown timers. Nothing here touches the server. */
final class Tools {
    private Tools() { }

    record Timer(String label, long endAt) { }

    static final long SESSION_START = System.currentTimeMillis();
    static double sessionTotal = 0;
    static int sessionLogs = 0;
    static final List<Timer> timers = new ArrayList<>();

    static void tick(Minecraft mc) {
        if (timers.isEmpty()) return;
        long n = System.currentTimeMillis();
        Iterator<Timer> it = timers.iterator();
        while (it.hasNext()) {
            Timer t = it.next();
            if (n >= t.endAt()) {
                it.remove();
                if (mc.player != null) mc.player.displayClientMessage(Component.literal("[DFS] Timer done: " + t.label()), false);
            }
        }
    }
}
