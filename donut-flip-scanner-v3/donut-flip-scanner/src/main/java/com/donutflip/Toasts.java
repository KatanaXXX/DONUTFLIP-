package com.donutflip;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/** Small self-drawn notification cards (top-right). Purely visual; clicking does nothing and never buys anything. */
final class Toasts {
    private Toasts() { }

    private record Toast(AlertEngine.AlertEntry e, long start) { }

    private static final List<Toast> active = new ArrayList<>();
    private static final long LIFE = 7000, FADE_IN = 200, FADE_OUT = 500;
    private static final int MAX_SHOWN = 3;

    static void push(AlertEngine.AlertEntry e) {
        active.add(new Toast(e, System.currentTimeMillis()));
        while (active.size() > MAX_SHOWN + 3) active.remove(0);
    }

    static void clear() { active.clear(); }

    static void render(GuiGraphics g, int screenW) {
        if (active.isEmpty()) return;
        long now = System.currentTimeMillis();
        Font f = Minecraft.getInstance().font;
        Iterator<Toast> it = active.iterator();
        while (it.hasNext()) if (now - it.next().start() > LIFE) it.remove();
        int shown = 0;
        for (int i = active.size() - 1; i >= 0 && shown < MAX_SHOWN; i--, shown++) {
            Toast t = active.get(i);
            long age = now - t.start();
            float a = Math.min(1f, age / (float) FADE_IN) * Math.min(1f, (LIFE - age) / (float) FADE_OUT);
            a = Math.max(0f, Math.min(1f, a));
            AlertEngine.AlertEntry e = t.e();
            int w = 178, h = 46, x = screenW - w - 8, y = 8 + shown * (h + 4);
            Draw.panel(g, x, y, x + w, y + h, 6, Draw.mul(FlipScreen.ACCENT, a), Draw.mul(0xF00B1220, a));
            g.drawString(f, e.watch() ? "WATCH HIT" : "NEW FLIP FOUND", x + 8, y + 5, Draw.mul(FlipScreen.ACCENT, a));
            String conf = e.level().name();
            g.drawString(f, conf, x + w - 8 - f.width(conf), y + 5, Draw.mul(confColor(e.level()), a));
            String name = e.item().replace('_', ' ') + " x" + e.quantity();
            g.drawString(f, f.plainSubstrByWidth(name, w - 16), x + 8, y + 16, Draw.mul(FlipScreen.TEXT, a));
            g.drawString(f, "BUY " + Fmt.money(e.price()) + " > SELL ~" + Fmt.money(e.resale()), x + 8, y + 26, Draw.mul(FlipScreen.EST, a));
            String line = "NET ~" + Fmt.signed(e.net()) + "  ROI ~" + Fmt.pct(e.roi()) + "  " + pageText(e.page());
            g.drawString(f, f.plainSubstrByWidth(line, w - 16), x + 8, y + 36, Draw.mul(e.net() >= 0 ? FlipScreen.GOOD : FlipScreen.BAD, a));
        }
    }

    static int confColor(Analyzer.Level l) {
        return l == Analyzer.Level.HIGH ? FlipScreen.GOOD : l == Analyzer.Level.MEDIUM ? FlipScreen.WARN : FlipScreen.BAD;
    }

    static String pageText(int page) { return page > 0 ? "AH PAGE " + page : "PAGE UNKNOWN"; }
}
