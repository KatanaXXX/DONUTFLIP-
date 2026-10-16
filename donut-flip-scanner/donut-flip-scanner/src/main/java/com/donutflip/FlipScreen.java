package com.donutflip;

import com.donutflip.Analyzer.Analysis;
import com.donutflip.Analyzer.HistoryRow;
import com.donutflip.Analyzer.Rec;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

public class FlipScreen extends Screen {
    private enum Tab {
        BEST("Best Flips"), AVOID("Avoid"), HISTORY("Market History"), SETTINGS("Settings");
        final String title;
        Tab(String t) { title = t; }
    }

    // ARGB colors
    private static final int C_PANEL = 0xF0121418, C_HEAD = 0xFF1B1F27, C_ROW = 0xFF181B21, C_ROW_ALT = 0xFF1D2128;
    private static final int C_TEXT = 0xFFFFFFFF, C_DIM = 0xFF8B93A1, C_EST = 0xFF6FC3FF;
    private static final int C_GOOD = 0xFF4ADE80, C_BAD = 0xFFF87171, C_WARN = 0xFFFBBF24, C_ACCENT = 0xFFF59E0B;

    private static final double[] PROFITS = {0, 500, 1_000, 5_000, 10_000, 50_000, 100_000, 500_000, 1_000_000};
    private static final int[] CONFS = {0, 25, 40, 50, 60, 70, 80, 90};

    private Tab tab = Tab.BEST;
    private String query = "";
    private EditBox search;
    private int scroll = 0;
    private int px, py, pw, ph, listTop, listBottom;

    private List<Analysis> analyses = List.of();
    private List<HistoryRow> history = List.of();
    private long now = System.currentTimeMillis();

    public FlipScreen() { super(Component.literal("Donut Flip Scanner")); }

    private Config cfg() { return DonutFlipScannerClient.CONFIG; }
    private Store store() { return DonutFlipScannerClient.STORE; }

    @Override
    protected void init() {
        pw = Math.min(width - 16, 680);
        ph = height - 16;
        px = (width - pw) / 2;
        py = 8;
        recompute();
        rebuild();
    }

    private void recompute() {
        now = System.currentTimeMillis();
        analyses = Analyzer.analyze(store().records, cfg(), now);
        history = Analyzer.history(store().records, cfg(), now);
        scroll = 0;
    }

    private void rebuild() {
        clearWidgets();
        int tabW = (pw - 16 - 12) / 4;
        for (int i = 0; i < Tab.values().length; i++) {
            Tab t = Tab.values()[i];
            Button b = Button.builder(Component.literal((t == tab ? "> " : "") + t.title), btn -> { tab = t; scroll = 0; rebuild(); })
                    .bounds(px + 8 + i * (tabW + 4), py + 24, tabW, 18).build();
            addRenderableWidget(b);
        }
        listTop = py + 88;
        listBottom = py + ph - 8;

        if (tab == Tab.SETTINGS) { buildSettings(); return; }

        int avail = pw - 16 - 16;
        int y = py + 48;
        int x = px + 8;
        int wSearch = (int) (avail * 0.30), wSort = (int) (avail * 0.19), wProfit = (int) (avail * 0.21),
                wConf = (int) (avail * 0.18), wReload = avail - wSearch - wSort - wProfit - wConf;

        search = new EditBox(font, x, y, wSearch, 16, Component.literal("Search"));
        search.setHint(Component.literal("Search item..."));
        search.setValue(query);
        search.setResponder(s -> { query = s; scroll = 0; });
        addRenderableWidget(search);
        x += wSearch + 4;

        addRenderableWidget(Button.builder(Component.literal("Sort: " + cfg().sort), b -> {
            cfg().sort = switch (cfg().sort) { case "PROFIT" -> "ROI"; case "ROI" -> "CONFIDENCE"; default -> "PROFIT"; };
            cfg().save(); scroll = 0; rebuild();
        }).bounds(x, y - 1, wSort, 18).build());
        x += wSort + 4;

        addRenderableWidget(Button.builder(Component.literal("Min profit: " + money(cfg().minProfit)), b -> {
            cfg().minProfit = nextD(cfg().minProfit, PROFITS); cfg().save(); recompute(); rebuild();
        }).bounds(x, y - 1, wProfit, 18).build());
        x += wProfit + 4;

        addRenderableWidget(Button.builder(Component.literal("Min conf: " + cfg().minConfidence + "%"), b -> {
            cfg().minConfidence = nextI(cfg().minConfidence, CONFS); cfg().save(); recompute(); rebuild();
        }).bounds(x, y - 1, wConf, 18).build());
        x += wConf + 4;

        addRenderableWidget(Button.builder(Component.literal("Reload"), b -> { store().load(); recompute(); rebuild(); })
                .bounds(x, y - 1, wReload, 18).build());
    }

    private void buildSettings() {
        int bx = px + pw - 8 - 110;
        int y = py + 54;
        addRenderableWidget(Button.builder(Component.literal(fmtPct(cfg().feeRatePercent)), b -> {
            cfg().feeRatePercent = nextD(cfg().feeRatePercent, new double[]{0, 1, 2, 3, 5, 8, 10}); cfg().save(); recompute(); rebuild();
        }).bounds(bx, y, 110, 18).build());
        y += 26;
        addRenderableWidget(Button.builder(Component.literal(cfg().feeVerified ? "Verified: YES" : "Verified: NO"), b -> {
            cfg().feeVerified = !cfg().feeVerified; cfg().save(); recompute(); rebuild();
        }).bounds(bx, y, 110, 18).build());
        y += 26;
        addRenderableWidget(Button.builder(Component.literal(cfg().maxAgeDays + " days"), b -> {
            cfg().maxAgeDays = nextI(cfg().maxAgeDays, new int[]{3, 7, 14, 30, 60}); cfg().save(); recompute(); rebuild();
        }).bounds(bx, y, 110, 18).build());
        y += 26;
        addRenderableWidget(Button.builder(Component.literal(fmtPct(cfg().undercutPercent)), b -> {
            cfg().undercutPercent = nextD(cfg().undercutPercent, new double[]{0, 1, 2, 5, 10}); cfg().save(); recompute(); rebuild();
        }).bounds(bx, y, 110, 18).build());
        y += 26;
        addRenderableWidget(Button.builder(Component.literal(cfg().minSamples + " records"), b -> {
            cfg().minSamples = nextI(cfg().minSamples, new int[]{2, 3, 5, 8}); cfg().save(); recompute(); rebuild();
        }).bounds(bx, y, 110, 18).build());
    }

    // ---------- helpers ----------
    private static double nextD(double cur, double[] opts) {
        for (int i = 0; i < opts.length; i++) if (Math.abs(opts[i] - cur) < 1e-9) return opts[(i + 1) % opts.length];
        return opts[0];
    }
    private static int nextI(int cur, int[] opts) {
        for (int i = 0; i < opts.length; i++) if (opts[i] == cur) return opts[(i + 1) % opts.length];
        return opts[0];
    }
    private static String fmtPct(double v) { return String.format(Locale.ROOT, "%.0f%%", v); }

    static String money(double v) {
        double a = Math.abs(v);
        if (a >= 1e9) return String.format(Locale.ROOT, "%.2fB", v / 1e9);
        if (a >= 1e6) return String.format(Locale.ROOT, "%.2fM", v / 1e6);
        if (a >= 1e4) return String.format(Locale.ROOT, "%.1fK", v / 1e3);
        return String.format(Locale.ROOT, "%,.0f", v);
    }

    private String age(long ts) {
        if (ts <= 0) return "unknown time";
        double d = (now - ts) / 86_400_000.0;
        if (d < 1) return String.format(Locale.ROOT, "%.0fh ago", Math.max(0, d * 24));
        return String.format(Locale.ROOT, "%.0fd ago", d);
    }

    private static String confLabel(int c) { return c >= 70 ? "High" : c >= 40 ? "Med" : "Low"; }
    private static int confColor(int c) { return c >= 70 ? C_GOOD : c >= 40 ? C_WARN : C_BAD; }

    private int seg(GuiGraphics g, String s, int x, int y, int color) {
        g.drawString(font, s, x, y, color);
        return x + font.width(s);
    }

    private List<Analysis> visibleAnalyses() {
        String q = query.trim().toLowerCase(Locale.ROOT);
        Comparator<Analysis> cmp = switch (cfg().sort) {
            case "ROI" -> Comparator.comparing((Analysis a) -> a.roiPercent() == null ? -1e18 : a.roiPercent());
            case "CONFIDENCE" -> Comparator.comparingInt(Analysis::confidence);
            default -> Comparator.comparing((Analysis a) -> a.estimatedNetProfit() == null ? -1e18 : a.estimatedNetProfit());
        };
        return analyses.stream()
                .filter(a -> tab == Tab.BEST ? a.recommendation() == Rec.BUY_CANDIDATE : a.recommendation() != Rec.BUY_CANDIDATE)
                .filter(a -> q.isEmpty() || a.rec().item().toLowerCase(Locale.ROOT).contains(q))
                .sorted(cmp.reversed())
                .collect(Collectors.toList());
    }

    private List<HistoryRow> visibleHistory() {
        String q = query.trim().toLowerCase(Locale.ROOT);
        return history.stream().filter(h -> q.isEmpty() || h.label().toLowerCase(Locale.ROOT).contains(q)).collect(Collectors.toList());
    }

    private static final int ROW_H = 30;

    private int rowCount() {
        return switch (tab) { case HISTORY -> visibleHistory().size(); case SETTINGS -> 0; default -> visibleAnalyses().size(); };
    }

    // ---------- input ----------
    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (tab != Tab.SETTINGS) {
            int max = Math.max(0, rowCount() * ROW_H - (listBottom - listTop));
            scroll = (int) Math.max(0, Math.min(max, scroll - sy * 16));
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (DonutFlipScannerClient.OPEN_KEY.matches(event)) { onClose(); return true; }
        return super.keyPressed(event);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    // ---------- rendering ----------
    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float delta) {
        super.renderBackground(g, mx, my, delta);
        g.fill(px, py, px + pw, py + ph, C_PANEL);
        g.fill(px, py, px + pw, py + 20, C_HEAD);
        g.fill(px, py + 20, px + pw, py + 21, C_ACCENT);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float delta) {
        super.render(g, mx, my, delta);
        g.drawString(font, "DONUT FLIP SCANNER", px + 8, py + 6, C_ACCENT);
        String mode = "OFFLINE MODE | live scan: DISABLED (no staff approval)";
        g.drawString(font, mode, px + pw - 8 - font.width(mode), py + 6, C_DIM);

        if (tab == Tab.SETTINGS) { renderSettings(g); return; }

        // status + legend
        g.drawString(font, store().status, px + 8, py + 70, store().records.isEmpty() ? C_WARN : C_DIM);
        int lx = px + pw - 8 - font.width("Observed  ~Estimate");
        lx = seg(g, "Observed  ", lx, py + 70, C_TEXT);
        seg(g, "~Estimate", lx, py + 70, C_EST);

        g.enableScissor(px + 4, listTop, px + pw - 4, listBottom);
        if (tab == Tab.HISTORY) renderHistory(g); else renderAnalyses(g);
        g.disableScissor();
    }

    private void renderAnalyses(GuiGraphics g) {
        List<Analysis> rows = visibleAnalyses();
        if (rows.isEmpty()) {
            String msg = store().records.isEmpty()
                    ? "No data. Add recorded listings to config/donutflipscanner/import.csv and press Reload."
                    : (tab == Tab.BEST ? "No flips meet your thresholds." : "Nothing to show here.");
            g.drawString(font, msg, px + 12, listTop + 8, C_DIM);
            return;
        }
        int y = listTop - scroll;
        for (int i = 0; i < rows.size(); i++, y += ROW_H) {
            if (y + ROW_H < listTop || y > listBottom) continue;
            Analysis a = rows.get(i);
            g.fill(px + 4, y, px + pw - 4, y + ROW_H - 2, i % 2 == 0 ? C_ROW : C_ROW_ALT);

            String name = Analyzer.label(a.rec());
            g.drawString(font, name, px + 10, y + 4, C_TEXT);
            int badgeColor = a.recommendation() == Rec.BUY_CANDIDATE ? C_GOOD : a.recommendation() == Rec.AVOID ? C_BAD : C_WARN;
            String badge = a.recommendation().label;
            g.drawString(font, badge, px + pw - 10 - font.width(badge), y + 4, badgeColor);

            int x = px + 10, ty = y + 16;
            x = seg(g, "Listed " + money(a.observedListingPrice()), x, ty, C_TEXT);
            if (a.estimatedMarketPrice() == null) {
                seg(g, "   " + a.reason(), x, ty, C_DIM);
            } else {
                x = seg(g, "  Resale ~" + money(a.suggestedResalePrice()), x, ty, C_EST);
                double net = a.estimatedNetProfit();
                x = seg(g, "  Net ~" + (net >= 0 ? "+" : "") + money(net), x, ty, net >= 0 ? C_GOOD : C_BAD);
                x = seg(g, "  ROI ~" + String.format(Locale.ROOT, "%.1f%%", a.roiPercent()), x, ty, C_EST);
                x = seg(g, "  Conf " + confLabel(a.confidence()) + " " + a.confidence() + "%", x, ty, confColor(a.confidence()));
                seg(g, "  n=" + a.sampleSize() + " " + age(a.lastUpdated()), x, ty, C_DIM);
            }
        }
    }

    private void renderHistory(GuiGraphics g) {
        List<HistoryRow> rows = visibleHistory();
        if (rows.isEmpty()) { g.drawString(font, "No records.", px + 12, listTop + 8, C_DIM); return; }
        int y = listTop - scroll;
        for (int i = 0; i < rows.size(); i++, y += ROW_H) {
            if (y + ROW_H < listTop || y > listBottom) continue;
            HistoryRow h = rows.get(i);
            g.fill(px + 4, y, px + pw - 4, y + ROW_H - 2, i % 2 == 0 ? C_ROW : C_ROW_ALT);
            g.drawString(font, h.label(), px + 10, y + 4, C_TEXT);
            int x = px + 10, ty = y + 16;
            x = seg(g, "Records " + h.count(), x, ty, C_TEXT);
            x = seg(g, "  Median/unit ~" + money(h.medianUnit()), x, ty, C_EST);
            x = seg(g, "  Range " + money(h.minUnit()) + " - " + money(h.maxUnit()), x, ty, C_TEXT);
            x = seg(g, "  Conf " + confLabel(h.confidence()), x, ty, confColor(h.confidence()));
            seg(g, "  Last " + (h.lastUpdated() <= 0 ? "unknown" : Instant.ofEpochMilli(h.lastUpdated()).atZone(ZoneOffset.UTC).toLocalDate().toString()), x, ty, C_DIM);
        }
    }

    private void renderSettings(GuiGraphics g) {
        int x = px + 12, y = py + 58;
        String[] labels = {"Marketplace fee rate", "Fee rate verified by me", "Max record age", "Resale undercut", "Min comparable records"};
        for (String l : labels) { g.drawString(font, l, x, y, C_TEXT); y += 26; }
        y += 6;
        String[] notes = {
                "Fees are only subtracted when 'Verified' is YES. Unverified fees are treated as 0 (profit may be overstated).",
                "Prices are recorded ASKING prices, not completed sales. Resale = median of comparable records minus undercut.",
                "Comparable = same item, attributes, durability band and stack-size band. Dissimilar items are never merged.",
                "Live Auction House scanning is disabled: it requires explicit DonutSMP staff approval of the exact method.",
                "This mod sends no packets/commands, automates no clicks, and cannot buy or sell anything.",
                "Import file: config/donutflipscanner/import.csv"
        };
        for (String n : notes) { g.drawString(font, n, x, y, C_DIM); y += 12; }
    }
}
