package com.donutflip;

import com.donutflip.Analyzer.Analysis;
import com.donutflip.Analyzer.HistoryRow;
import com.donutflip.Analyzer.Rec;
import java.util.*;
import java.util.stream.Collectors;
import net.minecraft.class_11908;
import net.minecraft.class_2561;
import net.minecraft.class_332;
import net.minecraft.class_342;
import net.minecraft.class_437;

public class FlipScreen extends class_437 {
    private enum Tab {
        FLIPS("Flips"), AVOID("Avoid"), MARKET("Market"), WATCH("Watch"), TOOLS("Tools"), SETTINGS("Settings");
        final String title;
        Tab(String t) { title = t; }
    }

    static float chromeAlpha = 1f;
    // ARGB palette (blue theme)
    static final int BG = 0xF00B1220, ROW = 0xFF14203A, ROW2 = 0xFF172646, FIELD = 0xFF0F1830;
    static final int ACCENT = 0xFF3B9CFF, ACCENT_DIM = 0xFF1D4E8F;
    static final int TEXT = 0xFFEAF2FF, DIM = 0xFF7F93B5, EST = 0xFF7DD3FC;
    static final int GOOD = 0xFF4ADE80, BAD = 0xFFF87171, WARN = 0xFFFBBF24;

    private static final double[] PROFITS = {0, 500, 1_000, 5_000, 10_000, 50_000, 100_000, 500_000, 1_000_000};
    private static final int[] CONFS = {0, 25, 40, 50, 60, 70, 80, 90};
    private static final int ROW_H = 44;

    private Tab tab = Tab.FLIPS;
    private String query = "";
    private int scroll = 0;
    private int px, py, pw, ph, listTop, listBottom, tabW;
    private float indX = -1;
    private final long openedAt = System.currentTimeMillis();
    private long contentAt = System.currentTimeMillis();
    private boolean confirmLive = false;

    private final List<int[]> fields = new ArrayList<>();
    private class_342 calcBuy, calcSell, logBox, timerMin, timerLabel, watchName, watchMax, kwBox;

    private List<Analysis> analyses = List.of();
    private List<HistoryRow> history = List.of();
    private List<String> watchInfo = List.of();
    private long now = System.currentTimeMillis();
    private int observedRecordCount = -1;

    public FlipScreen() { super(class_2561.method_43470("Donut Flip Scanner")); }

    private Config cfg() { return DonutFlipScannerClient.CONFIG; }
    private Store store() { return DonutFlipScannerClient.STORE; }

    @Override
    protected void method_25426() {
        pw = Math.min(field_22789 - 16, 430);
        ph = Math.min(field_22790 - 16, 272);
        px = (field_22789 - pw) / 2;
        py = (field_22790 - ph) / 2;
        tabW = (pw - 16) / Tab.values().length;
        recompute();
        rebuild();
    }

    @Override
    public void method_25432() {
        cfg().save();
        super.method_25432();
    }

    private void recompute() {
        now = System.currentTimeMillis();
        observedRecordCount = store().records.size();
        analyses = Analyzer.analyze(store().records, cfg(), now);
        history = Analyzer.history(store().records, cfg(), now);
        List<String> wi = new ArrayList<>();
        for (Config.WatchItem w : cfg().watch) {
            double best = Double.MAX_VALUE;
            int n = 0;
            for (PriceRecord r : store().records) {
                if (!r.item().toLowerCase(Locale.ROOT).contains(w.name.toLowerCase(Locale.ROOT))) continue;
                if (Analyzer.ageDays(r, now, cfg().maxAgeDays) > cfg().maxAgeDays) continue;
                n++;
                best = Math.min(best, r.unitPrice());
            }
            wi.add(n == 0 ? "no recent records" : "lowest seen ~" + money(best) + "/unit (" + n + " rec)");
        }
        watchInfo = wi;
        scroll = 0;
    }

    // ---------- widgets ----------
    private UiButton btn(int x, int y, int w, int h, String label, Runnable r) {
        UiButton b = new UiButton(x, y, w, h, label, r, false, false);
        method_37063(b);
        return b;
    }

    private class_342 box(int x, int y, int w, String hint, String val) {
        class_342 b = new class_342(field_22793, x + 5, y + 4, w - 10, 10, class_2561.method_43470(hint));
        b.method_1858(false);
        b.method_1880(48);
        b.method_47404(class_2561.method_43470(hint));
        b.method_1852(val);
        fields.add(new int[]{x, y, w, 16});
        method_37063(b);
        return b;
    }

    private void switchTab(Tab t) {
        tab = t;
        scroll = 0;
        contentAt = System.currentTimeMillis();
        rebuild();
    }

    private void rebuild() {
        method_37067();
        fields.clear();
        for (int i = 0; i < Tab.values().length; i++) {
            Tab t = Tab.values()[i];
            method_37063(new UiButton(px + 8 + i * tabW, py + 24, tabW - 2, 16, t.title, () -> switchTab(t), true, t == tab));
        }
        if (indX < 0) indX = px + 8 + tab.ordinal() * tabW;
        listTop = py + (tab == Tab.FLIPS ? 100 : 82);
        listBottom = py + ph - 8;

        switch (tab) {
            case FLIPS, AVOID -> buildToolbar(true);
            case MARKET -> buildToolbar(false);
            case WATCH -> buildWatch();
            case TOOLS -> buildTools();
            case SETTINGS -> buildSettings();
        }
    }

    private void buildToolbar(boolean full) {
        int x = px + 8, y = py + 46, avail = pw - 16;
        int wReload = 52;
        if (!full) {
            class_342 s = box(x, y, avail - wReload - 4, "Search item...", query);
            s.method_1863(v -> { query = v; scroll = 0; });
            btn(px + pw - 8 - wReload, y, wReload, 16, "Reload", this::reloadAll);
            return;
        }
        int wS = (int) (avail * 0.24), wSort = (int) (avail * 0.17), wP = (int) (avail * 0.21), wC = avail - wS - wSort - wP - wReload - 16;
        class_342 s = box(x, y, wS, "Search item...", query);
        s.method_1863(v -> { query = v; scroll = 0; });
        x += wS + 4;
        btn(x, y, wSort, 16, "Sort: " + (cfg().sort.equals("CONFIDENCE") ? "Conf" : cfg().sort.equals("ROI") ? "ROI" : "Profit"), () -> {
            cfg().sort = switch (cfg().sort) { case "PROFIT" -> "ROI"; case "ROI" -> "CONFIDENCE"; default -> "PROFIT"; };
            cfg().save(); scroll = 0; rebuild();
        });
        x += wSort + 4;
        btn(x, y, wP, 16, "Min $ " + money(cfg().minProfit), () -> {
            cfg().minProfit = nextD(cfg().minProfit, PROFITS); cfg().save(); recompute(); rebuild();
        });
        x += wP + 4;
        btn(x, y, wC, 16, "Conf>=" + cfg().minConfidence, () -> {
            cfg().minConfidence = nextI(cfg().minConfidence, CONFS); cfg().save(); recompute(); rebuild();
        });
        btn(px + pw - 8 - wReload, y, wReload, 16, "Reload", this::reloadAll);
    }

    private void reloadAll() { store().load(); recompute(); rebuild(); }

    private void buildWatch() {
        int y = py + 46, x = px + 8;
        watchName = box(x, y, 180, "Item name (e.g. elytra)", "");
        watchMax = box(x + 184, y, 100, "Max /unit (e.g. 5k)", "");
        btn(x + 288, y, 60, 16, "+ Watch", () -> {
            try {
                String n = watchName.method_1882().trim();
                double m = Store.parsePrice(watchMax.method_1882());
                if (n.isEmpty() || m <= 0) return;
                Config.WatchItem w = new Config.WatchItem();
                w.name = n; w.maxUnit = m;
                cfg().watch.add(w);
                cfg().save(); recompute(); rebuild();
            } catch (Exception ignored) { }
        });
        for (int i = 0; i < Math.min(8, cfg().watch.size()); i++) {
            final int idx = i;
            btn(px + pw - 26, py + 68 + i * 22 + 3, 16, 14, "x", () -> {
                cfg().watch.remove(idx); cfg().save(); recompute(); rebuild();
            });
        }
    }

    private void buildTools() {
        int lx = px + 8, rx = px + pw / 2 + 4, w = pw / 2 - 12;
        calcBuy = box(lx, py + 52, w, "Buy price (e.g. 12k)", calcBuy == null ? "" : calcBuy.method_1882());
        calcSell = box(lx, py + 72, w, "Sell price (e.g. 15k)", calcSell == null ? "" : calcSell.method_1882());

        logBox = box(rx, py + 52, w - 44, "Profit made (e.g. 5k)", "");
        btn(rx + w - 40, py + 52, 40, 16, "Log", () -> {
            try {
                double v = Store.parsePrice(logBox.method_1882());
                Tools.sessionTotal += v; Tools.sessionLogs++;
                logBox.method_1852("");
            } catch (Exception ignored) { }
        });

        timerLabel = box(lx, py + 154, w - 74, "Timer label", "");
        timerMin = box(lx + w - 70, py + 154, 40, "min", "");
        btn(lx + w - 26, py + 154, 26, 16, "Go", () -> {
            try {
                double m = Double.parseDouble(timerMin.method_1882().trim());
                if (m <= 0) return;
                String l = timerLabel.method_1882().trim();
                Tools.timers.add(new Tools.Timer(l.isEmpty() ? "timer" : l, System.currentTimeMillis() + (long) (m * 60_000)));
                timerLabel.method_1852(""); timerMin.method_1852("");
            } catch (Exception ignored) { }
        });
    }

    private void buildSettings() {
        int lx = px + 10, rx = px + pw / 2 + 4;
        int bw = 80, y0 = py + 36;
        // left column
        btn(lx + 112, y0, bw, 16, cfg().liveCapture ? "ON" : confirmLive ? "CONFIRM" : "OFF", () -> {
            if (cfg().liveCapture) { cfg().liveCapture = false; confirmLive = false; }
            else if (!confirmLive) { confirmLive = true; }
            else { cfg().liveCapture = true; confirmLive = false; }
            cfg().save(); rebuild();
        });
        btn(lx + 112, y0 + 22, bw, 16, cfg().chatAlerts ? "ON" : "OFF", () -> { cfg().chatAlerts = !cfg().chatAlerts; cfg().save(); rebuild(); });
        kwBox = box(lx + 112, y0 + 44, bw, "keyword", cfg().captureKeyword);
        kwBox.method_1863(v -> { if (!v.isBlank()) cfg().captureKeyword = v.trim(); });
        btn(lx + 112, y0 + 66, bw, 16, fmtPct(cfg().feeRatePercent), () -> {
            cfg().feeRatePercent = nextD(cfg().feeRatePercent, new double[]{0, 1, 2, 3, 5, 8, 10}); cfg().save(); recompute(); rebuild();
        });
        btn(lx + 112, y0 + 88, bw, 16, cfg().feeVerified ? "YES" : "NO", () -> { cfg().feeVerified = !cfg().feeVerified; cfg().save(); recompute(); rebuild(); });
        // right column
        btn(rx + 112, y0, bw, 16, cfg().maxAgeDays + " days", () -> {
            cfg().maxAgeDays = nextI(cfg().maxAgeDays, new int[]{3, 7, 14, 30, 60}); cfg().save(); recompute(); rebuild();
        });
        btn(rx + 112, y0 + 22, bw, 16, fmtPct(cfg().undercutPercent), () -> {
            cfg().undercutPercent = nextD(cfg().undercutPercent, new double[]{0, 1, 2, 5, 10}); cfg().save(); recompute(); rebuild();
        });
        btn(rx + 112, y0 + 44, bw, 16, cfg().minSamples + " records", () -> {
            cfg().minSamples = nextI(cfg().minSamples, new int[]{2, 3, 5, 8}); cfg().save(); recompute(); rebuild();
        });
        btn(rx + 112, y0 + 66, bw, 16, "Clear", () -> { store().clearCaptured(); recompute(); rebuild(); });
        btn(rx + 112, y0 + 88, bw, 16, "Dump", () -> { Capture.dumpRequested = true; });
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

    private static String confLabel(int c) { return c >= 70 ? "High" : c >= 40 ? "Med" : "Low"; }
    private static int confColor(int c) { return c >= 70 ? GOOD : c >= 40 ? WARN : BAD; }

    private int t(class_332 g, String s, int x, int y, int color, float a) {
        g.method_25303(field_22793, s, x, y, Draw.mul(color, a));
        return x + field_22793.method_1727(s);
    }

    private List<Analysis> visibleAnalyses() {
        String q = query.trim().toLowerCase(Locale.ROOT);
        Comparator<Analysis> cmp = switch (cfg().sort) {
            case "ROI" -> Comparator.comparing((Analysis a) -> a.roiPercent() == null ? -1e18 : a.roiPercent());
            case "CONFIDENCE" -> Comparator.comparingInt(Analysis::confidence);
            default -> Comparator.comparing((Analysis a) -> a.estimatedNetProfit() == null ? -1e18 : a.estimatedNetProfit());
        };
        return analyses.stream()
                .filter(a -> tab == Tab.FLIPS ? a.recommendation() == Rec.BUY_CANDIDATE : a.recommendation() != Rec.BUY_CANDIDATE)
                .filter(a -> q.isEmpty() || a.rec().item().toLowerCase(Locale.ROOT).contains(q))
                .sorted(cmp.reversed())
                .collect(Collectors.toList());
    }

    private List<HistoryRow> visibleHistory() {
        String q = query.trim().toLowerCase(Locale.ROOT);
        return history.stream().filter(h -> q.isEmpty() || h.label().toLowerCase(Locale.ROOT).contains(q)).collect(Collectors.toList());
    }

    private int rowCount() {
        return switch (tab) {
            case MARKET -> visibleHistory().size();
            case FLIPS, AVOID -> visibleAnalyses().size();
            default -> 0;
        };
    }

    // ---------- input ----------
    @Override
    public boolean method_25401(double mx, double my, double sx, double sy) {
        if (tab == Tab.FLIPS || tab == Tab.AVOID || tab == Tab.MARKET) {
            int max = Math.max(0, rowCount() * ROW_H - (listBottom - listTop));
            scroll = (int) Math.max(0, Math.min(max, scroll - sy * 16));
            return true;
        }
        return super.method_25401(mx, my, sx, sy);
    }

    @Override
    public boolean method_25404(class_11908 event) {
        if (DonutFlipScannerClient.OPEN_KEY.method_1417(event)) { method_25419(); return true; }
        return super.method_25404(event);
    }

    @Override
    public boolean method_25421() { return false; }

    // ---------- rendering (everything except widgets is drawn here, beneath the widgets) ----------
    @Override
    public void method_25420(class_332 g, int mx, int my, float delta) {
        super.method_25420(g, mx, my, delta); // vanilla blur (see Options > Accessibility > Menu Background Blurriness)
        long n = System.currentTimeMillis();
        if (observedRecordCount != store().records.size()) recompute();
        float oa = Math.min(1f, (n - openedAt) / 300f);
        float ca = Math.min(1f, (n - contentAt) / 220f) * oa;
        chromeAlpha = oa;

        g.method_25294(0, 0, field_22789, field_22790, Draw.mul(0xFF040812, 0.40f * oa));
        Draw.panel(g, px, py, px + pw, py + ph, 8, Draw.mul(ACCENT_DIM, oa), Draw.mul(BG, oa));

        // header
        int tx = t(g, "DONUT", px + 10, py + 8, ACCENT, oa);
        t(g, " FLIP SCANNER", tx, py + 8, TEXT, oa);
        String pill = cfg().liveCapture ? (Capture.scanning ? "LIVE: scanning" : "LIVE: waiting") : "OFFLINE";
        int pillColor = cfg().liveCapture ? (Capture.scanning ? GOOD : WARN) : DIM;
        int pwid = field_22793.method_1727(pill) + 12;
        Draw.round(g, px + pw - 10 - pwid, py + 5, px + pw - 10, py + 18, 6, Draw.mul(0xFF16264A, oa));
        t(g, pill, px + pw - 10 - pwid + 6, py + 8, pillColor, oa);

        // animated tab underline
        float target = px + 8 + tab.ordinal() * tabW;
        indX += (target - indX) * 0.25f;
        Draw.round(g, (int) indX + 4, py + 41, (int) indX + tabW - 6, py + 43, 1, Draw.mul(ACCENT, oa));

        for (int[] f : fields) Draw.round(g, f[0], f[1], f[0] + f[2], f[1] + f[3], 5, Draw.mul(FIELD, oa));

        switch (tab) {
            case FLIPS -> { statusLine(g, ca); drawDashboardMetrics(g, ca); drawAnalyses(g, ca); }
            case AVOID -> { statusLine(g, ca); drawAnalyses(g, ca); }
            case MARKET -> { statusLine(g, ca); drawHistory(g, ca); }
            case WATCH -> drawWatch(g, ca);
            case TOOLS -> drawTools(g, ca);
            case SETTINGS -> drawSettings(g, ca);
        }
    }

    private void drawDashboardMetrics(class_332 g, float a) {
        int x = px + 8, y = py + 78, gap = 4;
        int cardW = (pw - 16 - gap * 4) / 5;
        long candidates = analyses.stream().filter(r -> r.recommendation() == Rec.BUY_CANDIDATE).count();
        long avoid = analyses.stream().filter(r -> r.recommendation() == Rec.AVOID).count();
        double potential = analyses.stream().filter(r -> r.recommendation() == Rec.BUY_CANDIDATE)
                .mapToDouble(r -> r.estimatedNetProfit() == null ? 0 : r.estimatedNetProfit()).sum();
        String[] labels = {"CAPTURED", "FLIPS", "AVOID", "POTENTIAL", "SESSION"};
        String[] values = {String.valueOf(store().captured.size()), String.valueOf(candidates), String.valueOf(avoid),
                money(potential), money(Tools.sessionTotal)};
        int[] colors = {DIM, GOOD, BAD, EST, Tools.sessionTotal >= 0 ? GOOD : BAD};
        for (int i = 0; i < labels.length; i++) {
            int cx = x + i * (cardW + gap);
            Draw.round(g, cx, y, cx + cardW, y + 18, 4, Draw.mul(ROW, a));
            t(g, labels[i], cx + 5, y + 2, DIM, a);
            String val = field_22793.method_27523(values[i], cardW - 10);
            t(g, val, cx + 5, y + 9, colors[i], a);
        }
    }

    private void statusLine(class_332 g, float a) {
        String s = cfg().liveCapture ? Capture.detail : store().status + " | live capture off";
        t(g, field_22793.method_27523(s, pw - 130), px + 10, py + 68, store().records.isEmpty() ? WARN : DIM, a);
        int lx = px + pw - 10 - field_22793.method_1727("Observed  ~Estimate");
        lx = t(g, "Observed  ", lx, py + 68, TEXT, a);
        t(g, "~Estimate", lx, py + 68, EST, a);
    }

    private void drawAnalyses(class_332 g, float a) {
        List<Analysis> rows = visibleAnalyses();
        g.method_44379(px + 6, listTop, px + pw - 6, listBottom);
        if (rows.isEmpty()) {
            String msg = store().records.isEmpty()
                    ? "No data yet. Turn on Live capture in Settings and browse the AH, or fill import.csv."
                    : (tab == Tab.FLIPS ? "No flips meet your thresholds." : "Nothing to show here.");
            t(g, field_22793.method_27523(msg, pw - 24), px + 12, listTop + 8, DIM, a);
        }
        int y = listTop - scroll;
        for (int i = 0; i < rows.size(); i++, y += ROW_H) {
            if (y + ROW_H < listTop || y > listBottom) continue;
            Analysis r = rows.get(i);
            Draw.round(g, px + 6, y, px + pw - 10, y + ROW_H - 3, 5, Draw.mul(i % 2 == 0 ? ROW : ROW2, a));
            int badgeColor = r.recommendation() == Rec.BUY_CANDIDATE ? GOOD : r.recommendation() == Rec.AVOID ? BAD : WARN;
            String badge = r.recommendation().label;
            t(g, badge, px + pw - 16 - field_22793.method_1727(badge), y + 3, badgeColor, a);
            String page = r.rec().page() > 0 ? "PAGE " + r.rec().page() : "PAGE UNKNOWN";
            int pageX = px + pw - 16 - field_22793.method_1727(badge) - field_22793.method_1727(page) - 14;
            t(g, page, pageX, y + 3, r.rec().page() > 0 ? EST : DIM, a);
            t(g, field_22793.method_27523(Analyzer.label(r.rec()), pageX - 8 - px - 12), px + 12, y + 3, TEXT, a);

            int x = px + 12, ty = y + 14;
            x = t(g, "LIST " + money(r.observedListingPrice()), x, ty, TEXT, a);
            if (r.estimatedMarketPrice() == null) {
                t(g, "  " + r.reason(), x, ty, WARN, a);
            } else {
                double resale = r.suggestedResalePrice();
                double buyMax = Math.max(0, resale * (1 - cfg().effectiveFeeRate() / 100.0) - cfg().minProfit);
                x = t(g, "  MAX BUY " + money(buyMax), x, ty, EST, a);
                t(g, "  SELL " + money(resale), x, ty, EST, a);
                double net = r.estimatedNetProfit();
                x = t(g, "NET " + (net >= 0 ? "+" : "") + money(net), px + 12, y + 25, net >= 0 ? GOOD : BAD, a);
                x = t(g, "  ROI " + String.format(Locale.ROOT, "%.0f%%", r.roiPercent()), x, y + 25, EST, a);
                x = t(g, "  " + confLabel(r.confidence()) + " " + r.confidence() + "%", x, y + 25, confColor(r.confidence()), a);
                t(g, "  n=" + r.sampleSize() + "  " + ageLabel(r.rec().timestamp(), now), x, y + 25, DIM, a);
            }
            t(g, field_22793.method_27523("WHY: " + r.reason(), pw - 32), px + 12, y + 35, DIM, a);
        }
        g.method_44380();
        scrollbar(g, rows.size(), a);
    }

    private static String ageLabel(long timestamp, long now) {
        if (timestamp <= 0) return "time unknown";
        long minutes = Math.max(0, now - timestamp) / 60_000;
        return minutes < 60 ? minutes + "m ago" : (minutes / 60) + "h ago";
    }

    private void drawHistory(class_332 g, float a) {
        List<HistoryRow> rows = visibleHistory();
        g.method_44379(px + 6, listTop, px + pw - 6, listBottom);
        if (rows.isEmpty()) t(g, "No records.", px + 12, listTop + 8, DIM, a);
        int y = listTop - scroll;
        for (int i = 0; i < rows.size(); i++, y += ROW_H) {
            if (y + ROW_H < listTop || y > listBottom) continue;
            HistoryRow h = rows.get(i);
            Draw.round(g, px + 6, y, px + pw - 10, y + ROW_H - 3, 5, Draw.mul(i % 2 == 0 ? ROW : ROW2, a));
            t(g, field_22793.method_27523(h.label(), pw - 30), px + 12, y + 3, TEXT, a);
            int x = px + 12, ty = y + 13;
            x = t(g, "n=" + h.count(), x, ty, TEXT, a);
            x = t(g, "  ~Median " + money(h.medianUnit()) + "/u", x, ty, EST, a);
            x = t(g, "  Range " + money(h.minUnit()) + "-" + money(h.maxUnit()), x, ty, TEXT, a);
            t(g, "  " + confLabel(h.confidence()), x, ty, confColor(h.confidence()), a);
        }
        g.method_44380();
        scrollbar(g, rows.size(), a);
    }

    private void scrollbar(class_332 g, int rows, float a) {
        int total = rows * ROW_H, view = listBottom - listTop;
        if (total <= view) return;
        int barH = Math.max(14, view * view / total);
        int barY = listTop + (int) ((view - barH) * (scroll / (double) (total - view)));
        Draw.round(g, px + pw - 8, barY, px + pw - 5, barY + barH, 1, Draw.mul(ACCENT, a));
    }

    private void drawWatch(class_332 g, float a) {
        List<Config.WatchItem> w = cfg().watch;
        if (w.isEmpty()) {
            t(g, "Add items to get a chat alert when a captured listing is at or below your max price.", px + 12, py + 74, DIM, a);
            t(g, "Alerts need Live capture ON (Settings) and Chat alerts ON.", px + 12, py + 86, DIM, a);
            return;
        }
        for (int i = 0; i < Math.min(8, w.size()); i++) {
            int y = py + 68 + i * 22;
            Draw.round(g, px + 6, y, px + pw - 10, y + 19, 5, Draw.mul(i % 2 == 0 ? ROW : ROW2, a));
            int x = t(g, w.get(i).name, px + 12, y + 5, TEXT, a);
            x = t(g, "  max " + money(w.get(i).maxUnit) + "/u", x, y + 5, EST, a);
            t(g, "  " + watchInfo.get(i), x, y + 5, DIM, a);
        }
    }

    private double num(class_342 b) {
        try { return Store.parsePrice(b.method_1882()); } catch (Exception e) { return Double.NaN; }
    }

    private void drawTools(class_332 g, float a) {
        int lx = px + 10, rx = px + pw / 2 + 6;
        t(g, "FLIP CALCULATOR", lx, py + 40, ACCENT, a);
        double buy = num(calcBuy), sell = num(calcSell), fee = cfg().effectiveFeeRate();
        int y = py + 94;
        if (!Double.isNaN(buy) && buy > 0) {
            t(g, "Break-even sell ~" + money(buy / (1 - fee / 100.0)), lx, y, EST, a);
            if (!Double.isNaN(sell)) {
                double net = sell - sell * fee / 100.0 - buy;
                t(g, "Net ~" + (net >= 0 ? "+" : "") + money(net) + "   ROI ~" + String.format(Locale.ROOT, "%.1f%%", net / buy * 100), lx, y + 12, net >= 0 ? GOOD : BAD, a);
            }
        } else {
            t(g, "Enter a buy and sell price.", lx, y, DIM, a);
        }
        t(g, "Fee used: " + fmtPct(fee) + (cfg().feeVerified ? "" : " (unverified)"), lx, y + 24, DIM, a);

        t(g, "TIMERS", lx, py + 140, ACCENT, a);
        long n = System.currentTimeMillis();
        int ty = py + 176;
        if (Tools.timers.isEmpty()) t(g, "No active timers.", lx, ty, DIM, a);
        for (int i = 0; i < Math.min(5, Tools.timers.size()); i++) {
            long left = Math.max(0, Tools.timers.get(i).endAt() - n) / 1000;
            t(g, String.format(Locale.ROOT, "%s  %d:%02d", Tools.timers.get(i).label(), left / 60, left % 60), lx, ty + i * 11, TEXT, a);
        }

        t(g, "SESSION PROFIT (manual log)", rx, py + 40, ACCENT, a);
        double hrs = Math.max(1.0 / 60, (n - Tools.SESSION_START) / 3_600_000.0);
        t(g, "Total " + (Tools.sessionTotal >= 0 ? "+" : "") + money(Tools.sessionTotal) + "  (" + Tools.sessionLogs + " logs)", rx, py + 76, Tools.sessionTotal >= 0 ? GOOD : BAD, a);
        t(g, "~" + money(Tools.sessionTotal / hrs) + " per hour this session", rx, py + 88, EST, a);

        t(g, "DATA", rx, py + 140, ACCENT, a);
        t(g, "Captured listings: " + store().captured.size(), rx, py + 154, TEXT, a);
        t(g, "Imported records: " + store().imported.size(), rx, py + 166, TEXT, a);
        t(g, "Comparable groups: " + history.size(), rx, py + 178, TEXT, a);
    }

    private void drawSettings(class_332 g, float a) {
        int lx = px + 10, rx = px + pw / 2 + 4, y0 = py + 36;
        String[] left = {"Live AH capture", "Chat alerts", "AH title keyword", "Fee rate", "Fee verified"};
        String[] right = {"Max record age", "Resale undercut", "Min comparables", "Captured data", "Debug dump"};
        for (int i = 0; i < 5; i++) {
            t(g, left[i], lx, y0 + 4 + i * 22, TEXT, a);
            t(g, right[i], rx, y0 + 4 + i * 22, TEXT, a);
        }
        int y = y0 + 118;
        if (confirmLive) t(g, "Click CONFIRM only after you checked DonutSMP's rules.", lx, y, WARN, a);
        t(g, "Live capture only READS the AH screen you open. No clicks, packets, buys.", lx, y + 12, DIM, a);
        t(g, "Not guaranteed safe. Fees count only when 'Fee verified' is YES.", lx, y + 24, DIM, a);
        t(g, field_22793.method_27523("Capture: " + Capture.detail, pw - 24), lx, y + 36, DIM, a);
        t(g, "Dump writes debug.txt (config/donutflipscanner) to help fix price parsing.", lx, y + 48, DIM, a);
    }
}
