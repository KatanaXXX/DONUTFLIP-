package com.donutflip;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.class_2561;
import net.minecraft.class_310;

/** Small local helpers: session profit log and countdown timers. Nothing here touches the server. */
final class Tools {
    private Tools() { }

    record Timer(String label, long endAt) { }

    static final long SESSION_START = System.currentTimeMillis();
    static double sessionTotal = 0;
    static int sessionLogs = 0;
    static final List<Timer> timers = new ArrayList<>();

    static void tick(class_310 mc) {
        if (timers.isEmpty()) return;
        long n = System.currentTimeMillis();
        Iterator<Timer> it = timers.iterator();
        while (it.hasNext()) {
            Timer t = it.next();
            if (n >= t.endAt()) {
                it.remove();
                if (mc.field_1724 != null) mc.field_1724.method_7353(class_2561.method_43470("[DFS] Timer done: " + t.label()), false);
            }
        }
    }
}
