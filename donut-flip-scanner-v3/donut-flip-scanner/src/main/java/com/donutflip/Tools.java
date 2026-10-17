package com.donutflip;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/** Local helpers: session ledger (see Ledger) and countdown timers. Nothing here touches the server. */
final class Tools {
    private Tools() { }

    static final Ledger LEDGER = new Ledger(System.currentTimeMillis());

    static final class Timer {
        final String label;
        final long durationMs;
        long endAt;
        long pausedLeft = -1;

        Timer(String label, long durationMs, long now) { this.label = label; this.durationMs = durationMs; this.endAt = now + durationMs; }

        boolean paused() { return pausedLeft >= 0; }
        long left(long now) { return paused() ? pausedLeft : Math.max(0, endAt - now); }
    }

    static final List<Timer> timers = new ArrayList<>();

    /** Returns false (and adds nothing) if a timer with the same label already exists. */
    static boolean addTimer(String label, double minutes) {
        String l = label.isBlank() ? "timer" : label.trim();
        for (Timer t : timers) if (t.label.equalsIgnoreCase(l)) return false;
        long now = System.currentTimeMillis();
        timers.add(new Timer(l, (long) (minutes * 60_000), now));
        return true;
    }

    static void togglePause(Timer t) {
        long now = System.currentTimeMillis();
        if (t.paused()) { t.endAt = now + t.pausedLeft; t.pausedLeft = -1; }
        else t.pausedLeft = Math.max(0, t.endAt - now);
    }

    static void reset(Timer t) { t.pausedLeft = -1; t.endAt = System.currentTimeMillis() + t.durationMs; }

    static String clock(long ms) {
        long s = ms / 1000;
        return s >= 3600 ? String.format(Locale.ROOT, "%d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60)
                : String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60);
    }

    static void tick(Minecraft mc) {
        if (timers.isEmpty()) return;
        long n = System.currentTimeMillis();
        Iterator<Timer> it = timers.iterator();
        while (it.hasNext()) {
            Timer t = it.next();
            if (!t.paused() && n >= t.endAt) {
                it.remove();
                if (mc.player != null) mc.player.displayClientMessage(Component.literal("[DFS] Timer done: " + t.label), false);
            }
        }
    }
}
