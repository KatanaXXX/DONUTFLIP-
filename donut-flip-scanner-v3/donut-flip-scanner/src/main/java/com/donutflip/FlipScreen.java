package com.donutflip;

import com.donutflip.Analyzer.Analysis;
import com.donutflip.Analyzer.HistoryRow;
import com.donutflip.Analyzer.Rec;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.*;
import java.util.stream.Collectors;

/**
 * The F8 screen. All non-widget drawing happens in renderBackground (beneath the widgets).
 * Heavy work (analysis) lives in Market/Analyzer and is cached; this class only filters, sorts and draws.
 */
public class FlipScreen extends Screen {
    private enum Tab {
        DASHBOARD("Dashboard"), FLIPS("Flips"), AVOID("Avoid"), MARKET("Market"), WATCH("Watch"), TOOLS("Tools"),
        ALERTS("Alerts"), SETTINGS("Settings");
        final String title;
        Tab(String t) { title = t; }
    }

    private record Confirm(String title, String text, Runnable yes) { }

    static final String VERSION = FabricLoader.getInstance().getModContainer("donutflipscanner")
            .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("?");

    static float chromeAlpha = 1f;
    // ---- design tokens ----
    static final int BG = 0xF00B1220, ROW = 0xFF13203A, ROW2 = 0xFF16264A, FIELD = 0xFF0E1830, CARD = 0xFF111D36;
    static final int ACCENT = 0xFF3B9CFF, ACCENT_DIM = 0xFF1D4E8F;
    static final int TEXT = 0xFFEAF2FF, DIM = 0xFF7F93B5, EST = 0xFF7DD3FC;
    static final int GOOD = 0xFF4ADE80, BAD = 0xFFF87171, WARN = 0xFFFBBF24;

    private static final String[] SORTS = {"PROFIT", "ROI", "CONFIDENCE", "FRESH", "PAGE", "SCORE"};
    private static final String[] SORT_LABELS = {"Profit", "ROI", "Conf", "Fresh", "Page", "Score"};
    private static final String[] MARKET_SORTS = {"Name", "Records", "Change", "Latest"};
    private static final double[] PROFITS = {0, 500, 1_000, 5_000, 10_000, 50_000, 100_000, 500_000, 1_000_000};
    private static final double[] ROIS = {0, 5, 10, 20, 50, 100};
    private static final int[] CONFS = {0, 25, 40, 50, 60, 70, 80, 90};
    private static final int CARD_H = 46, MARKET_H = 38;

    // ---- state ----
    private Tab tab = Tab.DASHBOARD;
    private String query = "", pendingQuery = "";
    private long searchAt = 0;
    private int scroll = 0, rowH = CARD_H;
    private int px, py, pw, ph, listTop, listBottom, tabW;
    private float indX = -1;
    private final long openedAt = System.currentTimeMillis();
    private long contentAt = openedAt, lastSnapCheck = 0, now = openedAt;
    private boolean showHelp = false, showWatchLevel = false;
    private int catIdx = 0, marketSort = 0;
    private Confirm pending = null;

    private final List<int[]> fields = new ArrayList<>();
    private EditBox calcBuy, calcSell, calcQty, saleCost, saleSold, timerLabel, timerMin;
    private EditBox watchName, watchBuy, watchSell, watchMinProfit, kwBox, alertKw;

    private Market.Snapshot snap;
    private String visKey = "";
    private List<Analysis> visA = List.of(), dashList = List.of();
    private List<HistoryRow> visH = List.of();
    private List<String[]> watchRows = List.of();   // {line1, line2}
    private String calcKey = "";
    private String[] calcText = new String[0];
    private int[] calcColor = new int[0];

    public FlipScreen() { super(Component.literal("Donut Flip Scanner")); }

    private Config cfg() { return DonutFlipScannerClient.CONFIG; }
    private Store store() { return DonutFlipScannerClient.STORE; }

    @Override
    protected void init() {
        pw = Math.min(width - 16, 480);
        ph = Math.min(height - 16, 290);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        tabW = (pw - 16) / Tab.values().length;
        refreshSnap(true);
        rebuild();
    }

    @Override
    public void removed() {
        cfg().save();
        super.removed();
    }

    @Override
    public boolean isPauseScreen() { return false; }

    // =====================================================================
    // data
    // =====================================================================
    private void refreshSnap(boolean force) {
        now = System.currentTimeMillis();
        Market.Snapshot s = Market.get(store(), cfg(), now);
        if (force || s != snap) { snap = s; visKey = ""; }
        watchRows = buildWatchRows();
    }

    private void settingsChanged() {
        cfg().sanitize();
        cfg().save();
        Market.invalidate();
        refreshSnap(true);
        rebuild();
    }

    private void ensureLists() {
        String key = snap.id() + "|" + tab + "|" + query + "|" + cfg().sort + "|" + catIdx + "|" + showWatchLevel + "|" + marketSort;
        if (key.equals(visKey)) return;
        visKey = key;
        scroll = 0;
        String q = query.trim().toLowerCase(Locale.ROOT);
        String cat = Category.ALL[catIdx];
        if (tab == Tab.FLIPS || tab == Tab.AVOID) {
            Comparator<Analysis> cmp = Analyzer.comparator(cfg().sort);
            visA = snap.analyses().stream()
                    .filter(a -> tab == Tab.FLIPS ? (a.recommendation() == Rec.BUY_CANDIDATE || (showWatchLevel && a.recommendation() == Rec.WATCH))
                            : a.recommendation().isAvoid())
                    .filter(a -> q.isEmpty() || a.rec().item().toLowerCase(Locale.ROOT).contains(q))
                    .filter(a -> catIdx == 0 || Category.of(a.rec().item()).equals(cat))
                    .sorted(cmp)
                    .collect(Collectors.toList());
        } else if (tab == Tab.MARKET) {
            Comparator<HistoryRow> cmp = switch (marketSort) {
                case 1 -> Comparator.comparingInt(HistoryRow::count).reversed();
                case 2 -> Comparator.comparingDouble((HistoryRow h) -> h.trendPct() == null ? 0 : Math.abs(h.trendPct())).reversed();
                case 3 -> Comparator.comparingLong(HistoryRow::lastUpdated).reversed();
                default -> Comparator.comparing(HistoryRow::label);
            };
            visH = snap.history().stream().filter(h -> q.isEmpty() || h.label().toLowerCase(Locale.ROOT).contains(q)).sorted(cmp).collect(Collectors.toList());
        } else if (tab == Tab.DASHBOARD) {
            dashList = snap.analyses().stream().filter(a -> a.recommendation() == Rec.BUY_CANDIDATE)
                    .sorted(Comparator.comparingInt(Analysis::score).reversed().thenComparing(Analyzer.comparator("PROFIT")))
                    .limit(3).collect(Collectors.toList());
        }
    }

    private List<String[]> buildWatchRows() {
        List<String[]> rows = new ArrayList<>();
        for (Config.WatchItem w : cfg().watch) {
            String name = w.name.toLowerCase(Locale.ROOT).trim();
            List<PriceRecord> m = new ArrayList<>();
            for (PriceRecord r : store().records) {
                if (r.item().toLowerCase(Locale.ROOT).contains(name) && Analyzer.ageDays(r, now, cfg().maxAgeDays) <= cfg().maxAgeDays) m.add(r);
            }
            String l1 = w.name + "   buy <= " + (w.maxUnit > 0 ? Fmt.money(w.maxUnit) : "-") + "/u   sell "
                    + (w.targetSell > 0 ? Fmt.money(w.targetSell) : "auto") + (w.minProfit > 0 ? "   min profit " + Fmt.money(w.minProfit) : "");
            String l2;
            if (m.isEmpty()) {
                l2 = "No recent records.";
            } else {
                m.sort(Comparator.comparingLong(PriceRecord::timestamp).reversed());
                PriceRecord last = m.get(0);
                List<Double> units = m.stream().map(PriceRecord::unitPrice).collect(Collectors.toList());
                double med = Analyzer.median(units);
                String change = "";
                if (m.size() >= 2) {
                    List<Double> older = m.subList(1, Math.min(m.size(), 6)).stream().map(PriceRecord::unitPrice).collect(Collectors.toList());
                    double mo = Analyzer.median(older);
                    if (mo > 0) change = "  " + (last.unitPrice() >= mo ? "+" : "") + String.format(Locale.ROOT, "%.0f%%", (last.unitPrice() / mo - 1) * 100);
                }
                double sell = w.targetSell > 0 ? w.targetSell : med * (1 - cfg().undercutPercent / 100.0);
                String profit = w.maxUnit > 0 ? "  ~profit@target " + Fmt.signed(Calc.netProfit(sell, w.maxUnit, cfg().effectiveFeeRate())) + "/u" : "";
                l2 = "Last " + Fmt.money(last.unitPrice()) + "/u" + change + "  ~median " + Fmt.money(med) + "/u" + profit
                        + "  " + (last.timestamp() > 0 ? Fmt.age(now - last.timestamp()) + " ago" : "time unknown");
            }
            rows.add(new String[]{l1, l2});
        }
        return rows;
    }

    // =====================================================================
    // widgets
    // =====================================================================
    private UiButton btn(int x, int y, int w, int h, String label, Runnable r) {
        UiButton b = new UiButton(x, y, w, h, label, r, false, false);
        addRenderableWidget(b);
        return b;
    }

    private UiButton btn(int x, int y, int w, int h, String label, String tip, Runnable r) {
        UiButton b = btn(x, y, w, h, label, r);
        b.setTooltip(Tooltip.create(Component.literal(tip)));
        return b;
    }

    private EditBox box(int x, int y, int w, String hint, String val, String tip) {
        EditBox b = new EditBox(font, x + 5, y + 4, w - 10, 10, Component.literal(hint));
        b.setBordered(false);
        b.setMaxLength(48);
        b.setHint(Component.literal(hint));
        b.setValue(val);
        if (tip != null) b.setTooltip(Tooltip.create(Component.literal(tip)));
        fields.add(new int[]{x, y, w, 16});
        addRenderableWidget(b);
        return b;
    }

    private void switchTab(Tab t) {
        tab = t;
        scroll = 0;
        showHelp = false;
        contentAt = System.currentTimeMillis();
        visKey = "";
        if (t == Tab.ALERTS) Alerts.ENGINE.markRead();
        rebuild();
    }

    private void rebuild() {
        clearWidgets();
        fields.clear();
        if (pending != null) {
            int cx = px + pw / 2;
            btn(cx - 64, py + ph / 2 + 14, 60, 18, "Yes", () -> { Runnable r = pending.yes(); pending = null; r.run(); });
            btn(cx + 4, py + ph / 2 + 14, 60, 18, "Cancel", () -> { pending = null; rebuild(); });
            return;
        }
        int unread = Alerts.ENGINE.unread;
        for (int i = 0; i < Tab.values().length; i++) {
            Tab t = Tab.values()[i];
            String label = t == Tab.ALERTS && unread > 0 ? "Alerts " + unread : t.title;
            addRenderableWidget(new UiButton(px + 8 + i * tabW, py + 24, tabW - 2, 16, label, () -> switchTab(t), true, t == tab));
        }
        btn(px + pw - 24, py + 3, 16, 14, "?", "Explains every number on screen.", () -> { showHelp = !showHelp; rebuild(); });
        if (indX < 0) indX = px + 8 + tab.ordinal() * tabW;
        if (showHelp) return;

        switch (tab) {
            case DASHBOARD -> {
                listTop = py + 132;
                btn(px + pw - 78, py + 124, 70, 14, "All flips >", "Open the Flips tab.", () -> switchTab(Tab.FLIPS));
            }
            case FLIPS, AVOID -> buildListToolbar();
            case MARKET -> buildMarketToolbar();
            case WATCH -> buildWatch();
            case TOOLS -> buildTools();
            case ALERTS -> buildAlerts();
            case SETTINGS -> buildSettings();
        }
        listBottom = py + ph - 8;
    }

    private void buildListToolbar() {
        int x = px + 8, y = py + 46, avail = pw - 16;
        int wR = 50, wSort = 84, wS = avail - wR - wSort - 8;
        EditBox s = box(x, y, wS, "Search item...", pendingQuery, "Filter by item name.");
        s.setResponder(v -> { pendingQuery = v; searchAt = System.currentTimeMillis(); });
        int si = Math.max(0, Arrays.asList(SORTS).indexOf(cfg().sort));
        btn(x + wS + 4, y, wSort, 16, "Sort: " + SORT_LABELS[si],
                "Order the list. Profit/ROI/Conf/Fresh/Page/Score. Page lists the lowest AH page first; unknown pages go last.", () -> {
                    cfg().sort = SORTS[(Arrays.asList(SORTS).indexOf(cfg().sort) + 1) % SORTS.length];
                    cfg().save(); visKey = ""; rebuild();
                });
        btn(px + pw - 8 - wR, y, wR, 16, "Reload", "Re-read import.csv and captured.csv from disk.", () -> { store().load(); settingsChanged(); });

        int y2 = py + 66, n = tab == Tab.FLIPS ? 5 : 4, w = (avail - (n - 1) * 4) / n, x2 = px + 8;
        btn(x2, y2, w, 16, "Min $ " + Fmt.money(cfg().minProfit), "Minimum estimated net profit for a BUY CANDIDATE. Click to cycle.", () -> {
            cfg().minProfit = nextD(cfg().minProfit, PROFITS); settingsChanged();
        });
        x2 += w + 4;
        btn(x2, y2, w, 16, "Min ROI " + Fmt.pct(cfg().minRoi), "Minimum ROI (net profit / listing price). Click to cycle.", () -> {
            cfg().minRoi = nextD(cfg().minRoi, ROIS); settingsChanged();
        });
        x2 += w + 4;
        btn(x2, y2, w, 16, "Conf>=" + cfg().minConfidence, "Minimum confidence (0-100). Below it the estimate counts as weak.", () -> {
            cfg().minConfidence = nextI(cfg().minConfidence, CONFS); settingsChanged();
        });
        x2 += w + 4;
        btn(x2, y2, w, 16, Category.ALL[catIdx], "Rough item category filter, guessed from the item id.", () -> { catIdx = (catIdx + 1) % Category.ALL.length; visKey = ""; rebuild(); });
        if (tab == Tab.FLIPS) {
            x2 += w + 4;
            btn(x2, y2, w, 16, showWatchLevel ? "Buy+Watch" : "Buy only",
                    "WATCH = positive margin but below your minimum profit/ROI. Show them too?", () -> { showWatchLevel = !showWatchLevel; visKey = ""; rebuild(); });
        }
        listTop = py + 100;
        rowH = CARD_H;
    }

    private void buildMarketToolbar() {
        int x = px + 8, y = py + 46, avail = pw - 16;
        int wR = 50, wSort = 84, wRange = 66, wS = avail - wR - wSort - wRange - 12;
        EditBox s = box(x, y, wS, "Search item...", pendingQuery, "Filter by item name.");
        s.setResponder(v -> { pendingQuery = v; searchAt = System.currentTimeMillis(); });
        btn(x + wS + 4, y, wSort, 16, "Sort: " + MARKET_SORTS[marketSort], "Name, number of records, biggest price change, or latest observation.", () -> {
            marketSort = (marketSort + 1) % MARKET_SORTS.length; visKey = ""; rebuild();
        });
        btn(x + wS + wSort + 8, y, wRange, 16, cfg().marketRangeDays == 0 ? "Range: all" : "Range: " + cfg().marketRangeDays + "d",
                "Only use observations from the last N days.", () -> {
                    cfg().marketRangeDays = (int) nextD(cfg().marketRangeDays, new double[]{0, 3, 7, 14, 30}); settingsChanged();
                });
        btn(px + pw - 8 - wR, y, wR, 16, "Reload", "Re-read data files.", () -> { store().load(); settingsChanged(); });
        listTop = py + 88;
        rowH = MARKET_H;
    }

    private void buildWatch() {
        int y = py + 46, x = px + 8;
        watchName = box(x, y, 118, "Item (e.g. elytra)", "", "Matches any item id containing this text.");
        watchBuy = box(x + 122, y, 70, "Buy <= /u", "", "Target BUY price per unit, e.g. 5k.");
        watchSell = box(x + 196, y, 70, "Sell /u", "", "Target resale price per unit. Leave empty to use the market estimate.");
        watchMinProfit = box(x + 270, y, 70, "Min profit", "", "Optional minimum net profit (per listing) before alerting.");
        btn(x + 344, y, 60, 16, "+ Watch", "Add to the watchlist. Min ROI / min confidence can be set in config.json.", () -> {
            try {
                String n = watchName.getValue().trim();
                if (n.isEmpty()) return;
                Config.WatchItem w = new Config.WatchItem();
                w.name = n;
                if (!watchBuy.getValue().isBlank()) w.maxUnit = Store.parsePrice(watchBuy.getValue());
                if (!watchSell.getValue().isBlank()) w.targetSell = Store.parsePrice(watchSell.getValue());
                if (!watchMinProfit.getValue().isBlank()) w.minProfit = Store.parsePrice(watchMinProfit.getValue());
                if (w.maxUnit <= 0 && w.minProfit <= 0) return;
                cfg().watch.add(w);
                settingsChanged();
            } catch (Exception ignored) { }
        });
        for (int i = 0; i < Math.min(6, cfg().watch.size()); i++) {
            final int idx = i;
            btn(px + pw - 26, py + 72 + i * 34 + 8, 16, 14, "x", "Remove from watchlist.", () -> { cfg().watch.remove(idx); settingsChanged(); });
        }
    }

    private void buildTools() {
        int lx = px + 8, rx = px + pw / 2 + 4, w = pw / 2 - 12;
        calcBuy = box(lx, py + 50, w, "Buy price per unit (e.g. 12k)", calcBuy == null ? "" : calcBuy.getValue(), "What you would pay per unit.");
        calcSell = box(lx, py + 70, w, "Resale price per unit", calcSell == null ? "" : calcSell.getValue(), "What you expect to sell for, per unit.");
        calcQty = box(lx, py + 90, w / 2, "Quantity", calcQty == null ? "1" : calcQty.getValue(), "Stack size / number of units.");

        saleCost = box(rx, py + 50, w, "What you paid (total, optional)", "", "Total purchase cost of the flip. Leave empty if unknown.");
        saleSold = box(rx, py + 70, w, "What you sold for (total)", "", "Total money received. This is a RECORDED sale, so it counts as realized.");
        btn(rx, py + 90, 62, 16, "Log sale", "Record a completed sale. Fees are applied only when marked verified.", () -> {
            try {
                double sold = Store.parsePrice(saleSold.getValue());
                double cost = saleCost.getValue().isBlank() ? 0 : Store.parsePrice(saleCost.getValue());
                Tools.LEDGER.recordSale("sale", cost, sold, cfg().effectiveFeeRate(), System.currentTimeMillis());
                saleCost.setValue(""); saleSold.setValue("");
            } catch (Exception ignored) { }
        });
        btn(rx + 66, py + 90, 44, 16, "Undo", "Remove the last logged sale.", Tools.LEDGER::undoLast);

        int ty = py + 204;
        timerLabel = box(lx, ty, 110, "Timer label", "", "Name of the timer. Duplicate names are rejected.");
        timerMin = box(lx + 114, ty, 46, "min", "", "Minutes (decimals allowed).");
        btn(lx + 164, ty, 30, 16, "Go", "Start the timer.", () -> {
            try {
                double m = Double.parseDouble(timerMin.getValue().trim());
                if (m > 0 && Tools.addTimer(timerLabel.getValue(), m)) { timerLabel.setValue(""); timerMin.setValue(""); rebuild(); }
            } catch (Exception ignored) { }
        });
        for (int i = 0; i < Math.min(4, Tools.timers.size()); i++) {
            final Tools.Timer t = Tools.timers.get(i);
            int cx = lx + (i % 2) * (pw / 2 - 4), cy = ty + 22 + (i / 2) * 18;
            btn(cx + 96, cy, 16, 14, t.paused() ? ">" : "||", t.paused() ? "Resume" : "Pause", () -> { Tools.togglePause(t); rebuild(); });
            btn(cx + 114, cy, 16, 14, "R", "Reset to the full duration.", () -> { Tools.reset(t); rebuild(); });
            btn(cx + 132, cy, 16, 14, "x", "Delete.", () -> { Tools.timers.remove(t); rebuild(); });
        }
    }

    private void buildAlerts() {
        int lx = px + 10, rx = px + pw / 2 + 4, bw = 84, lw = 104, y0 = py + 46;
        Config c = cfg();
        btn(lx + lw, y0, bw, 16, c.toastAlerts ? "ON" : "OFF", "Show a small card in the corner of the screen.", () -> { c.toastAlerts = !c.toastAlerts; settingsChanged(); });
        btn(lx + lw, y0 + 22, bw, 16, c.soundAlerts ? "ON" : "OFF", "Play a short sound.", () -> { c.soundAlerts = !c.soundAlerts; settingsChanged(); });
        btn(lx + lw, y0 + 44, bw, 16, c.chatAlerts ? "ON" : "OFF", "Also write the alert into chat (only you can see it).", () -> { c.chatAlerts = !c.chatAlerts; settingsChanged(); });
        btn(lx + lw, y0 + 66, bw, 16, c.alertMinProfit < 0 ? "Same as Flips" : Fmt.money(c.alertMinProfit), "Minimum estimated net profit to alert.", () -> {
            c.alertMinProfit = nextD(c.alertMinProfit, new double[]{-1, 500, 1_000, 5_000, 10_000, 50_000, 100_000}); settingsChanged();
        });
        btn(lx + lw, y0 + 88, bw, 16, c.alertMinRoi < 0 ? "Same as Flips" : Fmt.pct(c.alertMinRoi), "Minimum ROI to alert.", () -> {
            c.alertMinRoi = nextD(c.alertMinRoi, new double[]{-1, 5, 10, 20, 50, 100}); settingsChanged();
        });
        btn(lx + lw, y0 + 110, bw, 16, c.alertMinConfidence < 0 ? "Same as Flips" : String.valueOf(c.alertMinConfidence), "Minimum confidence to alert.", () -> {
            c.alertMinConfidence = nextI(c.alertMinConfidence, new int[]{-1, 40, 50, 60, 70, 80, 90}); settingsChanged();
        });
        btn(rx + lw, y0, bw, 16, c.alertMinSamples < 0 ? "Same as Flips" : String.valueOf(c.alertMinSamples), "Minimum number of comparable listings.", () -> {
            c.alertMinSamples = nextI(c.alertMinSamples, new int[]{-1, 3, 5, 8, 12}); settingsChanged();
        });
        btn(rx + lw, y0 + 22, bw, 16, c.alertMaxAgeDays < 0 ? "Same as Flips" : c.alertMaxAgeDays + " days", "Ignore comparables whose typical age is above this.", () -> {
            c.alertMaxAgeDays = nextI(c.alertMaxAgeDays, new int[]{-1, 1, 3, 7, 14}); settingsChanged();
        });
        btn(rx + lw, y0 + 44, bw, 16, c.alertWatchedOnly ? "Watched only" : "All items", "Alert only for items on your watchlist?", () -> { c.alertWatchedOnly = !c.alertWatchedOnly; settingsChanged(); });
        alertKw = box(rx + lw, y0 + 66, bw, "keywords", c.alertKeywords, "Comma-separated words matched against item names or categories (e.g. elytra, armor). Empty = any.");
        alertKw.setResponder(v -> c.alertKeywords = v);
        btn(rx + lw, y0 + 88, bw, 16, c.alertCooldownSec + " s", "Minimum time before the same listing may alert again (and only if its price fell 10%+).", () -> {
            c.alertCooldownSec = nextI(c.alertCooldownSec, new int[]{10, 30, 60, 120, 300}); settingsChanged();
        });
        btn(rx + lw, y0 + 110, bw, 16, "Clear history", "Dismiss all recent alerts and forget what was already alerted.", () -> { Alerts.ENGINE.reset(); Toasts.clear(); rebuild(); });
    }

    private void buildSettings() {
        int lx = px + 10, rx = px + pw / 2 + 4, bw = 84, lw = 104, y0 = py + 46;
        Config c = cfg();
        btn(lx + lw, y0, bw, 16, c.liveCapture ? "ON" : "OFF",
                "Passively reads the Auction House screen YOU open. No clicks, packets or purchases.", () -> {
                    if (c.liveCapture) { c.liveCapture = false; settingsChanged(); }
                    else { pending = new Confirm("Turn on live capture?",
                            "It only reads the AH screen you open yourself. Check DonutSMP's rules first; no ban-safety is guaranteed.",
                            () -> { c.liveCapture = true; settingsChanged(); }); rebuild(); }
                });
        kwBox = box(lx + lw, y0 + 22, bw, "title keyword", c.captureKeyword, "Capture only runs on screens whose title contains this text.");
        kwBox.setResponder(v -> { if (!v.isBlank()) c.captureKeyword = v.trim(); });
        btn(lx + lw, y0 + 44, bw, 16, "every " + c.captureIntervalTicks + " ticks", "How often an open AH screen is read (20 ticks = 1 second).", () -> {
            c.captureIntervalTicks = nextI(c.captureIntervalTicks, new int[]{5, 10, 20, 40}); settingsChanged();
        });
        btn(lx + lw, y0 + 66, bw, 16, Fmt.pct(c.feeRatePercent), "Marketplace fee on a sale. Only used when Verified is YES.", () -> {
            c.feeRatePercent = nextD(c.feeRatePercent, new double[]{0, 1, 2, 3, 5, 8, 10}); settingsChanged();
        });
        btn(lx + lw, y0 + 88, bw, 16, c.feeVerified ? "YES" : "NO", "Set YES only after confirming the real fee. Unverified fees count as 0, which can overstate profit.", () -> {
            c.feeVerified = !c.feeVerified; settingsChanged();
        });
        btn(lx + lw, y0 + 110, bw, 16, Fmt.pct(c.undercutPercent), "Suggested resale = market estimate minus this %.", () -> {
            c.undercutPercent = nextD(c.undercutPercent, new double[]{0, 1, 2, 5, 10}); settingsChanged();
        });

        btn(rx + lw, y0, bw, 16, c.maxAgeDays + " days", "Records older than this are ignored.", () -> { c.maxAgeDays = nextI(c.maxAgeDays, new int[]{3, 7, 14, 30, 60}); settingsChanged(); });
        btn(rx + lw, y0 + 22, bw, 16, c.minSamples + " records", "Fewer comparable records than this = INSUFFICIENT DATA.", () -> { c.minSamples = nextI(c.minSamples, new int[]{2, 3, 5, 8}); settingsChanged(); });
        btn(rx + lw, y0 + 44, bw, 16, String.format(Locale.ROOT, "%.0f%%", c.maxVolatility * 100), "Comparable prices spreading more than this are flagged HIGH RISK.", () -> {
            c.maxVolatility = nextD(c.maxVolatility, new double[]{0.3, 0.5, 0.8, 1.2}); settingsChanged();
        });
        btn(rx + lw, y0 + 66, bw, 16, c.recentSeenMinutes + " min", "A listing last seen longer ago than this is shown as 'availability unknown'.", () -> {
            c.recentSeenMinutes = nextI(c.recentSeenMinutes, new int[]{5, 15, 30, 60, 180}); settingsChanged();
        });
        btn(rx + lw, y0 + 88, bw, 16, "Clear", "Delete all captured listings (imported records stay).", () ->
                { pending = new Confirm("Clear captured data?", "This deletes every captured listing and its history. It cannot be undone.",
                        () -> { store().clearCaptured(); Market.invalidate(); settingsChanged(); }); rebuild(); });
        btn(rx + lw, y0 + 110, bw, 16, "Dump", "Write the open AH screen's item names and lore to debug.txt (not chat). Do this once if prices are not detected.", () -> Capture.dumpRequested = true);
    }

    // =====================================================================
    // helpers
    // =====================================================================
    private static double nextD(double cur, double[] opts) {
        for (int i = 0; i < opts.length; i++) if (Math.abs(opts[i] - cur) < 1e-9) return opts[(i + 1) % opts.length];
        return opts[0];
    }
    private static int nextI(int cur, int[] opts) {
        for (int i = 0; i < opts.length; i++) if (opts[i] == cur) return opts[(i + 1) % opts.length];
        return opts[0];
    }

    private static String confLabel(Analyzer.Level l) { return l == Analyzer.Level.INSUFFICIENT ? "INSUFFICIENT" : l.name(); }
    private static int levelColor(Analyzer.Level l) { return Toasts.confColor(l); }

    private int t(GuiGraphics g, String s, int x, int y, int color, float a) {
        g.drawString(font, s, x, y, Draw.mul(color, a));
        return x + font.width(s);
    }

    /** Draws a segment only if it fits before {@code limit}; keeps rows tidy on narrow windows. */
    private int seg(GuiGraphics g, String s, int x, int y, int color, float a, int limit) {
        if (x + font.width(s) > limit) return x;
        return t(g, s, x, y, color, a);
    }

    private static String title(PriceRecord r) { return r.item().replace('_', ' ').toUpperCase(Locale.ROOT) + " x" + r.quantity(); }

    private static String traits(PriceRecord r) {
        StringBuilder sb = new StringBuilder();
        if (!r.attrs().isEmpty()) sb.append(r.attrs().replace(";", ", "));
        if (r.durabilityPct() >= 0) sb.append(sb.length() > 0 ? "  " : "").append("dur ").append(r.durabilityPct()).append('%');
        return sb.toString();
    }

    private String seenText(Analysis a) {
        long ts = a.rec().timestamp();
        if (ts <= 0) return "time unknown";
        String age = Fmt.age(now - ts);
        return a.recentlySeen() ? "seen " + age + " ago" : "last seen " + age + " ago - may be gone";
    }

    private void card(GuiGraphics g, int x, int y, int w, int h, String title, String value, int valueColor, String sub, int subColor, float a) {
        Draw.panel(g, x, y, x + w, y + h, 5, Draw.mul(ACCENT_DIM, a * 0.6f), Draw.mul(CARD, a));
        t(g, title, x + 8, y + 5, DIM, a);
        t(g, font.plainSubstrByWidth(value, w - 14), x + 8, y + 16, valueColor, a);
        t(g, font.plainSubstrByWidth(sub, w - 14), x + 8, y + 26, subColor, a);
    }

    private void emptyState(GuiGraphics g, String titleText, String hint, int color, float a) {
        int x1 = px + 20, x2 = px + pw - 20, y1 = listTop + 8;
        Draw.round(g, x1, y1, x2, y1 + 54, 6, Draw.mul(CARD, a));
        int w = font.width(titleText);
        t(g, titleText, px + (pw - w) / 2, y1 + 10, color, a);
        List<String> lines = wrap(hint, x2 - x1 - 20);
        for (int i = 0; i < Math.min(3, lines.size()); i++) t(g, lines.get(i), x1 + 10, y1 + 24 + i * 10, DIM, a);
    }

    private List<String> wrap(String s, int maxW) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String word : s.split(" ")) {
            if (cur.length() > 0 && font.width(cur + " " + word) > maxW) { out.add(cur.toString()); cur.setLength(0); }
            if (cur.length() > 0) cur.append(' ');
            cur.append(word);
        }
        if (cur.length() > 0) out.add(cur.toString());
        return out;
    }

    // =====================================================================
    // input
    // =====================================================================
    private int rowCount() {
        return switch (tab) { case MARKET -> visH.size(); case FLIPS, AVOID -> visA.size(); default -> 0; };
    }

    private void scrollBy(int delta) {
        int max = Math.max(0, rowCount() * rowH - (listBottom - listTop));
        scroll = Math.max(0, Math.min(max, scroll + delta));
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (pending == null && !showHelp && (tab == Tab.FLIPS || tab == Tab.AVOID || tab == Tab.MARKET)) {
            scrollBy((int) (-sy * 16));
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (DonutFlipScannerClient.OPEN_KEY.matches(event)) { onClose(); return true; }
        if (!(getFocused() instanceof EditBox) && pending == null) {
            int k = event.key();
            if (k >= GLFW.GLFW_KEY_1 && k < GLFW.GLFW_KEY_1 + Tab.values().length) { switchTab(Tab.values()[k - GLFW.GLFW_KEY_1]); return true; }
            if (k == GLFW.GLFW_KEY_DOWN) { scrollBy(rowH / 2); return true; }
            if (k == GLFW.GLFW_KEY_UP) { scrollBy(-rowH / 2); return true; }
            if (k == GLFW.GLFW_KEY_PAGE_DOWN) { scrollBy(listBottom - listTop); return true; }
            if (k == GLFW.GLFW_KEY_PAGE_UP) { scrollBy(-(listBottom - listTop)); return true; }
            if (k == GLFW.GLFW_KEY_HOME) { scroll = 0; return true; }
        }
        return super.keyPressed(event);
    }

    // =====================================================================
    // rendering (everything except widgets, drawn beneath them)
    // =====================================================================
    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float delta) {
        super.renderBackground(g, mx, my, delta); // vanilla blur (Options > Accessibility > Menu Background Blurriness)
        now = System.currentTimeMillis();
        float oa = Math.min(1f, (now - openedAt) / 300f);
        float ca = Math.min(1f, (now - contentAt) / 220f) * oa;
        chromeAlpha = oa;

        if (now - lastSnapCheck > 2000) { lastSnapCheck = now; refreshSnap(false); }
        if (!pendingQuery.equals(query) && now - searchAt >= 150) { query = pendingQuery; visKey = ""; }
        ensureLists();

        g.fill(0, 0, width, height, Draw.mul(0xFF040812, 0.40f * oa));
        Draw.panel(g, px, py, px + pw, py + ph, 8, Draw.mul(ACCENT_DIM, oa), Draw.mul(BG, oa));
        drawHeader(g, oa);

        if (pending != null) { drawDialog(g, oa); return; }

        float target = px + 8 + tab.ordinal() * tabW;
        indX += (target - indX) * 0.25f;
        Draw.round(g, (int) indX + 4, py + 41, (int) indX + tabW - 6, py + 43, 1, Draw.mul(ACCENT, oa));
        if (showHelp) { drawHelp(g, ca); return; }

        for (int[] f : fields) Draw.round(g, f[0], f[1], f[0] + f[2], f[1] + f[3], 5, Draw.mul(FIELD, oa));
        switch (tab) {
            case DASHBOARD -> drawDashboard(g, mx, my, ca);
            case FLIPS, AVOID -> { drawListStatus(g, ca); drawAnalyses(g, mx, my, ca); }
            case MARKET -> drawMarket(g, mx, my, ca);
            case WATCH -> drawWatch(g, ca);
            case TOOLS -> drawTools(g, ca);
            case ALERTS -> drawAlerts(g, ca);
            case SETTINGS -> drawSettings(g, ca);
        }
    }

    private void drawHeader(GuiGraphics g, float oa) {
        int x = t(g, "DONUT", px + 10, py + 8, ACCENT, oa);
        x = t(g, " FLIP SCANNER", x, py + 8, TEXT, oa);
        t(g, " v" + VERSION, x, py + 8, DIM, oa);
        String pill; int pc;
        if (!cfg().liveCapture) { pill = "OFFLINE"; pc = DIM; }
        else if (Capture.scanning) { pill = "LIVE: scanning" + (Capture.currentPage > 0 ? " p" + Capture.currentPage : ""); pc = GOOD; }
        else { pill = "LIVE: waiting"; pc = WARN; }
        int pwid = font.width(pill) + 12, right = px + pw - 30;
        Draw.round(g, right - pwid, py + 4, right, py + 17, 6, Draw.mul(ROW2, oa));
        t(g, pill, right - pwid + 6, py + 7, pc, oa);
        int unread = Alerts.ENGINE.unread;
        if (unread > 0) {
            String b = String.valueOf(unread);
            int bw = font.width(b) + 8, bx = right - pwid - 4 - bw;
            Draw.round(g, bx, py + 4, bx + bw, py + 17, 6, Draw.mul(ACCENT, oa));
            t(g, b, bx + 4, py + 7, 0xFF08101F, oa);
        }
    }

    private void drawDialog(GuiGraphics g, float oa) {
        int cx = px + pw / 2, cy = py + ph / 2;
        Draw.panel(g, cx - 150, cy - 40, cx + 150, cy + 40, 8, Draw.mul(ACCENT, oa), Draw.mul(CARD, oa));
        String ttl = pending.title();
        t(g, ttl, cx - font.width(ttl) / 2, cy - 32, WARN, oa);
        List<String> lines = wrap(pending.text(), 270);
        for (int i = 0; i < Math.min(3, lines.size()); i++) t(g, lines.get(i), cx - 135, cy - 18 + i * 10, TEXT, oa);
    }

    private void drawHelp(GuiGraphics g, float a) {
        String[] lines = {
                "HOW TO READ THIS",
                "LISTING: the asking price you saw. All prices are ASKING prices, never confirmed sales.",
                "MAX BUY: the highest price that still meets your minimum profit and ROI.",
                "   Hidden when the estimate is too shaky to support one.",
                "SELL ~: recency-weighted median of comparable listings, minus your undercut %.",
                "NET: SELL - verified fees - LISTING.   ROI: NET divided by LISTING.",
                "CONF: evidence quality (sample size, recency, consistency). Not a promise to sell.",
                "n: comparable listings used.   PAGE: AH page from the screen title, else unknown.",
                "SEEN: 'seen' = observed recently; 'may be gone' = old observation, availability unknown.",
                "~ marks an estimate. BAD MARGIN (EST.) rests on estimates; HIGH RISK = weak evidence.",
                "Keys: 1-8 switch tabs, Up/Down/PgUp/PgDn scroll, F8 closes."
        };
        int y = py + 52;
        for (int i = 0; i < lines.length; i++) { t(g, font.plainSubstrByWidth(lines[i], pw - 24), px + 12, y, i == 0 ? ACCENT : (lines[i].startsWith("   ") ? DIM : TEXT), a); y += i == 0 ? 14 : 12; }
    }

    // ---------------- dashboard ----------------
    private void drawDashboard(GuiGraphics g, int mx, int my, float a) {
        Market.Stats s = snap.stats();
        int cw = (pw - 16 - 8) / 3, x0 = px + 8, y0 = py + 46, ch = 34;
        String latest = s.latestCapture() > 0 ? "latest " + Fmt.age(now - s.latestCapture()) + " ago" : (cfg().liveCapture ? "none captured yet" : "live capture is off");
        card(g, x0, y0, cw, ch, "CAPTURED LISTINGS", String.valueOf(s.captured()), TEXT, latest, DIM, a);
        card(g, x0 + cw + 4, y0, cw, ch, "FLIP CANDIDATES", String.valueOf(s.candidates()), s.candidates() > 0 ? GOOD : DIM, s.watch() + " more at watch level", DIM, a);
        card(g, x0 + 2 * (cw + 4), y0, cw, ch, "MARKED AVOID", String.valueOf(s.avoid() + s.insufficient()), s.avoid() > 0 ? BAD : DIM, s.insufficient() + " lack data", DIM, a);
        int y1 = y0 + ch + 4;
        card(g, x0, y1, cw, ch, "NEW OPPORTUNITIES", String.valueOf(s.fresh()), s.fresh() > 0 ? ACCENT : DIM, "last 10 min, " + Alerts.ENGINE.unread + " unread alert(s)", DIM, a);
        double realized = Tools.LEDGER.realized();
        card(g, x0 + cw + 4, y1, cw, ch, "REALIZED PROFIT", Tools.LEDGER.count() == 0 ? "0" : Fmt.signed(realized), realized >= 0 ? GOOD : BAD, Tools.LEDGER.count() + " recorded sale(s)", DIM, a);
        card(g, x0 + 2 * (cw + 4), y1, cw, ch, "EST. POTENTIAL", "~" + Fmt.money(s.potential()), EST, "estimate - not earned", WARN, a);

        t(g, "BEST OPPORTUNITIES", px + 10, py + 128, ACCENT, a);
        int top = py + 140, bottom = py + ph - 22;
        if (snap.analyses().isEmpty()) {
            listTop = py + 126;
            emptyState(g, "No data yet", store().error ? store().status
                    : "Turn on Live capture in Settings and browse the Auction House, or add records to import.csv. Nothing is shown until real data exists.", store().error ? BAD : WARN, a);
        } else if (dashList.isEmpty()) {
            listTop = py + 126;
            emptyState(g, "No candidates right now", "Nothing currently meets your profit, ROI and confidence settings. Browse more AH pages or relax the Flips filters.", DIM, a);
        } else {
            int n = Math.max(1, Math.min(dashList.size(), (bottom - top) / CARD_H));
            for (int i = 0; i < n; i++) drawCard(g, dashList.get(i), top + i * CARD_H, i, mx, my, a);
        }
        String foot = "Analyzed " + snap.analyses().size() + " listing(s) in " + snap.computeMs() + " ms | data within " + cfg().maxAgeDays + "d: "
                + Fmt.pct(s.freshPct()) + " | " + (cfg().liveCapture ? Capture.detail : "offline mode");
        t(g, font.plainSubstrByWidth(foot, pw - 20), px + 10, py + ph - 14, DIM, a);
    }

    // ---------------- flips / avoid ----------------
    private void drawListStatus(GuiGraphics g, float a) {
        String s = (store().error ? store().status : cfg().liveCapture ? Capture.detail : store().status + " | live capture off");
        t(g, font.plainSubstrByWidth(s, pw - 150), px + 10, py + 86, store().error ? BAD : DIM, a);
        int lx = px + pw - 10 - font.width("Observed  ~Estimate");
        lx = t(g, "Observed  ", lx, py + 86, TEXT, a);
        t(g, "~Estimate", lx, py + 86, EST, a);
    }

    private void drawAnalyses(GuiGraphics g, int mx, int my, float a) {
        g.enableScissor(px + 6, listTop, px + pw - 6, listBottom);
        if (visA.isEmpty()) {
            if (store().records.isEmpty())
                emptyState(g, "No data yet", "Turn on Live capture in Settings and browse the Auction House, or add records to import.csv.", WARN, a);
            else if (tab == Tab.FLIPS)
                emptyState(g, "No flips meet your settings", "Lower Min $ / Min ROI / Conf, show Buy+Watch, or capture more listings.", DIM, a);
            else
                emptyState(g, "Nothing flagged", "No listings are currently marked as risky or unprofitable.", DIM, a);
        }
        int y = listTop - scroll;
        for (int i = 0; i < visA.size(); i++, y += CARD_H) {
            if (y + CARD_H < listTop || y > listBottom) continue;
            drawCard(g, visA.get(i), y, i, mx, my, a);
        }
        g.disableScissor();
        scrollbar(g, visA.size() * CARD_H, a);
    }

    /** One recommendation card: name/badge, page + availability, buy/sell/net/roi, confidence + reason. */
    private void drawCard(GuiGraphics g, Analysis r, int y, int i, int mx, int my, float a) {
        int x1 = px + 6, x2 = px + pw - 10, limit = x2 - 6;
        boolean hov = mx >= x1 && mx < x2 && my >= y && my < y + CARD_H - 3 && my >= listTop && my < listBottom;
        Draw.round(g, x1, y, x2, y + CARD_H - 3, 5, Draw.mul(hov ? 0xFF1B3260 : (i % 2 == 0 ? ROW : ROW2), a));
        PriceRecord rec = r.rec();
        int badge = switch (r.recommendation()) { case BUY_CANDIDATE -> GOOD; case WATCH -> WARN; case BAD_MARGIN -> BAD; case HIGH_RISK -> WARN; default -> DIM; };
        String b = r.recommendation().label;
        int bx = x2 - 6 - font.width(b);
        t(g, b, bx, y + 4, badge, a);
        if (rec.timestamp() > 0 && rec.firstSeen() >= now - Market.NEW_WINDOW_MS) { String nw = "NEW"; t(g, nw, bx - 8 - font.width(nw), y + 4, ACCENT, a); bx -= 8 + font.width(nw); }
        t(g, font.plainSubstrByWidth(title(rec), bx - x1 - 20), px + 12, y + 4, TEXT, a);

        int ty = y + 15, x = px + 12;
        x = rec.page() > 0 ? t(g, "AH PAGE " + rec.page(), x, ty, ACCENT, a) : t(g, "PAGE UNKNOWN", x, ty, DIM, a);
        x = t(g, "  |  " + seenText(r), x, ty, r.recentlySeen() ? GOOD : WARN, a);
        String tr = traits(rec);
        if (!tr.isEmpty()) seg(g, "  |  " + tr, x, ty, DIM, a, limit);

        ty = y + 25;
        x = px + 12;
        x = t(g, "LISTING " + Fmt.money(rec.price()), x, ty, TEXT, a);
        if (rec.quantity() > 1) x = seg(g, " (" + Fmt.money(rec.unitPrice()) + "/u)", x, ty, DIM, a, limit);
        if (r.estimatedMarketPrice() == null) {
            seg(g, "   no price estimate", x, ty, DIM, a, limit);
        } else {
            double net = r.estimatedNetProfit();
            x = seg(g, "   MAX BUY " + (r.recommendedMaxBuy() == null ? "-" : Fmt.money(r.recommendedMaxBuy())), x, ty, EST, a, limit);
            x = seg(g, "   SELL ~" + Fmt.money(r.suggestedResalePrice()), x, ty, EST, a, limit);
            x = seg(g, "   NET ~" + Fmt.signed(net), x, ty, net >= 0 ? GOOD : BAD, a, limit);
            seg(g, "   ROI ~" + Fmt.pct(r.roiPercent()), x, ty, r.roiPercent() >= 0 ? EST : BAD, a, limit);
        }

        ty = y + 35;
        x = px + 12;
        if (r.level() != Analyzer.Level.INSUFFICIENT) {
            x = t(g, "CONF " + confLabel(r.level()) + " " + r.confidence(), x, ty, levelColor(r.level()), a);
            x = seg(g, "  n=" + r.sampleSize(), x, ty, DIM, a, limit);
            x = seg(g, "  data " + Fmt.days(r.medianAgeDays()), x, ty, r.medianAgeDays() > cfg().maxAgeDays / 2.0 ? WARN : DIM, a, limit);
        } else {
            x = t(g, "n=" + r.sampleSize(), x, ty, DIM, a);
        }
        String why = tab == Tab.AVOID || r.recommendation() != Rec.BUY_CANDIDATE ? r.primaryReason() : r.why();
        int room = limit - x - 14;
        if (room > 40) t(g, "  " + font.plainSubstrByWidth(why, room), x, ty, DIM, a);
    }

    // ---------------- market ----------------
    private void drawMarket(GuiGraphics g, int mx, int my, float a) {
        t(g, font.plainSubstrByWidth("Observed ASKING prices and estimates. Not confirmed sales; a listing that vanishes is not assumed sold.", pw - 20), px + 10, py + 68, DIM, a);
        g.enableScissor(px + 6, listTop, px + pw - 6, listBottom);
        if (visH.isEmpty()) emptyState(g, "No price history", store().records.isEmpty() ? "Capture or import listings first." : "Nothing matches the search or date range.", DIM, a);
        int y = listTop - scroll;
        for (int i = 0; i < visH.size(); i++, y += MARKET_H) {
            if (y + MARKET_H < listTop || y > listBottom) continue;
            HistoryRow h = visH.get(i);
            int x1 = px + 6, x2 = px + pw - 10, limit = x2 - 70;
            boolean hov = mx >= x1 && mx < x2 && my >= y && my < y + MARKET_H - 3 && my >= listTop && my < listBottom;
            Draw.round(g, x1, y, x2, y + MARKET_H - 3, 5, Draw.mul(hov ? 0xFF1B3260 : (i % 2 == 0 ? ROW : ROW2), a));
            t(g, font.plainSubstrByWidth(h.label().replace('_', ' ').toUpperCase(Locale.ROOT), x2 - x1 - 90), px + 12, y + 4, TEXT, a);
            String cat = h.category();
            t(g, cat, x2 - 6 - font.width(cat), y + 4, DIM, a);
            int x = px + 12, ty = y + 15;
            x = t(g, "n=" + h.count(), x, ty, TEXT, a);
            x = seg(g, "  ~MEDIAN " + Fmt.money(h.medianUnit()) + "/u", x, ty, EST, a, limit);
            x = seg(g, "  RESALE ~" + Fmt.money(h.resaleUnit()) + "/u", x, ty, EST, a, limit);
            x = seg(g, "  LATEST " + Fmt.money(h.latestUnit()) + "/u", x, ty, TEXT, a, limit);
            if (!Double.isNaN(h.lowestRecentUnit())) seg(g, "  LOW " + Fmt.money(h.lowestRecentUnit()), x, ty, TEXT, a, limit);
            ty = y + 25;
            x = px + 12;
            if (h.trendPct() != null) x = t(g, "TREND " + (h.trendPct() >= 0 ? "+" : "") + Fmt.pct(h.trendPct()), x, ty, h.trendPct() >= 0 ? GOOD : BAD, a);
            else x = t(g, "TREND n/a", x, ty, DIM, a);
            x = seg(g, "  VOL " + Fmt.pct(h.volatility() * 100), x, ty, h.volatility() > cfg().maxVolatility ? WARN : DIM, a, limit);
            Analyzer.Level lv = Analyzer.levelOf(h.confidence(), false);
            x = seg(g, "  CONF " + lv.name(), x, ty, levelColor(lv), a, limit);
            seg(g, "  last " + (h.lastUpdated() > 0 ? Fmt.age(now - h.lastUpdated()) + " ago" : "unknown") + "  data ~" + Fmt.days(h.medianAgeDays()), x, ty, DIM, a, limit);
            sparkline(g, h.spark(), x2 - 62, y + 14, 54, 16, a);
        }
        g.disableScissor();
        scrollbar(g, visH.size() * MARKET_H, a);
    }

    /** Bars of median unit price per slice of the observation history (oldest left). Drawn only with real data. */
    private void sparkline(GuiGraphics g, double[] v, int x, int y, int w, int h, float a) {
        if (v.length < 3) { t(g, "no chart", x + 6, y + 4, DIM, a); return; }
        double lo = Double.MAX_VALUE, hi = 0;
        for (double d : v) { lo = Math.min(lo, d); hi = Math.max(hi, d); }
        int bw = Math.max(2, w / v.length - 1);
        for (int i = 0; i < v.length; i++) {
            int bh = 2 + (int) ((hi > lo ? (v[i] - lo) / (hi - lo) : 0.5) * (h - 2));
            int bxx = x + i * (bw + 1);
            g.fill(bxx, y + h - bh, bxx + bw, y + h, Draw.mul(i == v.length - 1 ? ACCENT : ACCENT_DIM, a));
        }
    }

    private void scrollbar(GuiGraphics g, int total, float a) {
        int view = listBottom - listTop;
        if (total <= view) return;
        int barH = Math.max(14, view * view / total);
        int barY = listTop + (int) ((view - barH) * (scroll / (double) (total - view)));
        Draw.round(g, px + pw - 8, barY, px + pw - 5, barY + barH, 1, Draw.mul(ACCENT, a));
    }

    // ---------------- watch ----------------
    private void drawWatch(GuiGraphics g, float a) {
        t(g, "WATCHLIST", px + 10, py + 68, ACCENT, a);
        if (cfg().watch.isEmpty()) {
            listTop = py + 76;
            emptyState(g, "Nothing watched", "Add an item name with a target buy price. While live capture is on you get an alert when a listing meets it.", DIM, a);
            return;
        }
        for (int i = 0; i < Math.min(6, cfg().watch.size()); i++) {
            int y = py + 72 + i * 34;
            Draw.round(g, px + 6, y, px + pw - 10, y + 31, 5, Draw.mul(i % 2 == 0 ? ROW : ROW2, a));
            t(g, font.plainSubstrByWidth(watchRows.get(i)[0], pw - 50), px + 12, y + 5, TEXT, a);
            t(g, font.plainSubstrByWidth(watchRows.get(i)[1], pw - 50), px + 12, y + 18, DIM, a);
        }
    }

    // ---------------- tools ----------------
    private double num(EditBox b) {
        try { return Store.parsePrice(b.getValue()); } catch (Exception e) { return Double.NaN; }
    }

    private void drawTools(GuiGraphics g, float a) {
        int lx = px + 10, rx = px + pw / 2 + 6;
        t(g, "FLIP CALCULATOR", lx, py + 38, ACCENT, a);
        String key = calcBuy.getValue() + "|" + calcSell.getValue() + "|" + calcQty.getValue() + "|" + cfg().effectiveFeeRate() + "|" + cfg().minRoi;
        if (!key.equals(calcKey)) { calcKey = key; computeCalc(); }
        for (int i = 0; i < calcText.length; i++) t(g, font.plainSubstrByWidth(calcText[i], pw / 2 - 16), lx, py + 112 + i * 11, calcColor[i], a);

        t(g, "SESSION PROFIT", rx, py + 38, ACCENT, a);
        Ledger l = Tools.LEDGER;
        t(g, "Realized " + (l.count() == 0 ? "0" : Fmt.signed(l.realized())) + "  (" + l.count() + " sales)", rx, py + 112, l.realized() >= 0 ? GOOD : BAD, a);
        t(g, "Costs " + Fmt.money(l.totalCost()) + "  fees " + Fmt.money(l.totalFees()), rx, py + 123, DIM, a);
        t(g, "~" + Fmt.money(l.perHour(now)) + " realized per hour", rx, py + 134, EST, a);
        t(g, "Est. potential ~" + Fmt.money(snap.stats().potential()) + " (unsold, not earned)", rx, py + 145, WARN, a);
        List<Ledger.Entry> es = l.entries();
        for (int i = 0; i < Math.min(3, es.size()); i++) {
            Ledger.Entry e = es.get(es.size() - 1 - i);
            t(g, "sold " + Fmt.money(e.soldFor()) + "  net " + Fmt.signed(e.net()) + "  " + Fmt.age(now - e.at()) + " ago", rx, py + 158 + i * 11, e.net() >= 0 ? GOOD : BAD, a);
        }

        t(g, "TIMERS", lx, py + 192, ACCENT, a);
        for (int i = 0; i < Math.min(4, Tools.timers.size()); i++) {
            Tools.Timer tm = Tools.timers.get(i);
            int cx = lx + (i % 2) * (pw / 2 - 4), cy = py + 204 + 22 + (i / 2) * 18;
            t(g, font.plainSubstrByWidth(tm.label, 52), cx, cy + 3, TEXT, a);
            t(g, Tools.clock(tm.left(now)) + (tm.paused() ? "*" : ""), cx + 54, cy + 3, tm.paused() ? WARN : EST, a);
        }
        if (Tools.timers.isEmpty()) t(g, "No active timers.", lx + 200, py + 208, DIM, a);
    }

    private void computeCalc() {
        List<String> txt = new ArrayList<>();
        List<Integer> col = new ArrayList<>();
        double buy = num(calcBuy), sell = num(calcSell), qty = 1;
        boolean bad = false;
        if (!calcQty.getValue().isBlank()) { try { qty = Double.parseDouble(calcQty.getValue().trim()); } catch (Exception e) { bad = true; } }
        if ((!calcBuy.getValue().isBlank() && Double.isNaN(buy)) || (!calcSell.getValue().isBlank() && Double.isNaN(sell)) || qty <= 0) bad = true;
        double fee = cfg().effectiveFeeRate();
        if (bad) { txt.add("Use numbers like 12k, 1.5m or 8500."); col.add(WARN); }
        else if (Double.isNaN(buy) || buy <= 0) { txt.add("Enter a buy price."); col.add(DIM); }
        else {
            double be = Calc.breakEvenSell(buy, fee);
            txt.add("Break-even sell " + Fmt.money(be) + "/u"); col.add(EST);
            double roiTarget = cfg().minRoi > 0 ? cfg().minRoi : 10;
            if (!Double.isNaN(sell) && sell > 0) {
                double net = Calc.netProfit(sell * qty, buy * qty, fee);
                txt.add("Net " + Fmt.signed(net) + " total (fee " + Fmt.money(Calc.fee(sell * qty, fee)) + ")"); col.add(net >= 0 ? GOOD : BAD);
                txt.add("ROI " + Fmt.pct(Calc.roiPercent(net, buy * qty))); col.add(net >= 0 ? GOOD : BAD);
                txt.add("Max buy for " + Fmt.pct(roiTarget) + " ROI: " + Fmt.money(Calc.maxBuyForRoi(sell, fee, roiTarget)) + "/u"); col.add(EST);
            }
        }
        txt.add("net = sell - fee - buy;  ROI = net / buy"); col.add(DIM);
        txt.add("Fee " + Fmt.pct(fee) + (cfg().feeVerified ? "" : " (unverified, counted as 0)")); col.add(DIM);
        calcText = txt.toArray(new String[0]);
        calcColor = col.stream().mapToInt(Integer::intValue).toArray();
    }

    // ---------------- alerts ----------------
    private void drawAlerts(GuiGraphics g, float a) {
        int lx = px + 10, rx = px + pw / 2 + 4, y0 = py + 46;
        String[] left = {"Toast card", "Sound", "Chat message", "Min net profit", "Min ROI", "Min confidence"};
        String[] right = {"Min comparables", "Max data age", "Scope", "Keywords", "Cooldown", "History"};
        for (int i = 0; i < 6; i++) { t(g, left[i], lx, y0 + 4 + i * 22, TEXT, a); t(g, right[i], rx, y0 + 4 + i * 22, TEXT, a); }
        t(g, "RECENT ALERTS", px + 10, py + 182, ACCENT, a);
        List<AlertEngine.AlertEntry> h = Alerts.ENGINE.history();
        if (h.isEmpty()) { t(g, "None yet. Alerts appear when newly captured listings meet your thresholds.", px + 10, py + 196, DIM, a); return; }
        for (int i = 0; i < Math.min(5, h.size()); i++) {
            AlertEngine.AlertEntry e = h.get(i);
            String s = Fmt.age(now - e.at()) + " ago  " + e.item().replace('_', ' ') + " x" + e.quantity() + "  " + Toasts.pageText(e.page())
                    + "  BUY " + Fmt.money(e.price()) + " > SELL ~" + Fmt.money(e.resale()) + "  NET ~" + Fmt.signed(e.net()) + "  " + e.level().name();
            t(g, font.plainSubstrByWidth(s, pw - 20), px + 10, py + 196 + i * 11, e.watch() ? WARN : TEXT, a);
        }
    }

    // ---------------- settings ----------------
    private void drawSettings(GuiGraphics g, float a) {
        int lx = px + 10, rx = px + pw / 2 + 4, y0 = py + 46;
        String[] left = {"Live AH capture", "AH title keyword", "Capture rate", "Fee rate", "Fee verified", "Resale undercut"};
        String[] right = {"Max record age", "Min comparables", "Volatility limit", "Recently seen", "Captured data", "Debug dump"};
        for (int i = 0; i < 6; i++) { t(g, left[i], lx, y0 + 4 + i * 22, TEXT, a); t(g, right[i], rx, y0 + 4 + i * 22, TEXT, a); }
        int y = y0 + 138;
        t(g, "Hover any control for an explanation. Chat/sound/toast alerts are on the Alerts tab.", lx, y, DIM, a);
        t(g, "Live capture only READS the AH screen you open: no clicks, packets, or buying.", lx, y + 11, DIM, a);
        t(g, "Not guaranteed ban-safe. Fees count only when 'Fee verified' is YES.", lx, y + 22, DIM, a);
        t(g, font.plainSubstrByWidth("Capture: " + Capture.detail, pw - 24), lx, y + 33, cfg().liveCapture ? EST : DIM, a);
        t(g, "Data: " + store().status, lx, y + 44, store().error ? BAD : DIM, a);
    }
}
