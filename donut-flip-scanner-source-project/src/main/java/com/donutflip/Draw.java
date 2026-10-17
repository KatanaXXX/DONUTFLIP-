package com.donutflip;

import net.minecraft.class_332;

final class Draw {
    private Draw() { }

    static int mul(int c, float a) {
        int al = Math.round(((c >>> 24) & 0xFF) * Math.max(0f, Math.min(1f, a)));
        return (al << 24) | (c & 0xFFFFFF);
    }

    /** Filled rounded rectangle built from 1px scanlines. */
    static void round(class_332 g, int x1, int y1, int x2, int y2, int r, int color) {
        r = Math.min(r, Math.min((x2 - x1) / 2, (y2 - y1) / 2));
        if (r <= 0) { g.method_25294(x1, y1, x2, y2, color); return; }
        for (int i = 0; i < r; i++) {
            double dy = r - i - 0.5;
            int inset = r - (int) Math.round(Math.sqrt(r * r - dy * dy));
            g.method_25294(x1 + inset, y1 + i, x2 - inset, y1 + i + 1, color);
            g.method_25294(x1 + inset, y2 - i - 1, x2 - inset, y2 - i, color);
        }
        g.method_25294(x1, y1 + r, x2, y2 - r, color);
    }

    static void panel(class_332 g, int x1, int y1, int x2, int y2, int r, int border, int fill) {
        round(g, x1, y1, x2, y2, r, border);
        round(g, x1 + 1, y1 + 1, x2 - 1, y2 - 1, Math.max(0, r - 1), fill);
    }
}
