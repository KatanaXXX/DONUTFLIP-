package com.donutflip;

import com.donutflip.Analyzer.Analysis;
import com.donutflip.Analyzer.Rec;

import java.util.*;

/**
 * Decides WHEN to alert (pure logic, unit tested). Presentation (toast/sound/chat) lives in Alerts.
 *
 * Rules: only recently seen, fully calculated listings that meet every configured threshold alert.
 * A listing identity (item, stack, traits) alerts once; it may alert again only if its asking price falls
 * by at least 10% below the price it last alerted at AND the cooldown has passed. Unchanged listings never repeat.
 */
public final class AlertEngine {
    public record AlertEntry(String item, int quantity, int page, double price, double resale, Double maxBuy,
                             double net, double roi, Analyzer.Level level, long at, String reason, boolean watch) { }

    private static final int MAX_HISTORY = 50;
    private static final double DROP_FACTOR = 0.9;

    private final Map<String, double[]> last = new HashMap<>();   // fingerprint -> {price, atMs}
    private final Deque<AlertEntry> history = new ArrayDeque<>();
    public int unread = 0;

    public static String fingerprint(PriceRecord r) {
        return r.item() + "|" + r.quantity() + "|" + r.attrs() + "|" + r.durabilityPct();
    }

    public List<AlertEntry> history() { return new ArrayList<>(history); }
    public void clearHistory() { history.clear(); unread = 0; }
    public void markRead() { unread = 0; }
    public void reset() { last.clear(); clearHistory(); }

    /** Stateless threshold check for general flip alerts. */
    public static boolean qualifies(Analysis a, Config c, boolean watched) {
        if (a.recommendation() != Rec.BUY_CANDIDATE && a.recommendation() != Rec.WATCH) return false;
        if (a.estimatedNetProfit() == null || a.roiPercent() == null || a.suggestedResalePrice() == null) return false;
        if (!a.recentlySeen()) return false;
        if (a.estimatedNetProfit() < c.aMinProfit() || a.estimatedNetProfit() <= 0) return false;
        if (a.roiPercent() < c.aMinRoi()) return false;
        if (a.confidence() < c.aMinConf() || a.sampleSize() < c.aMinSamples()) return false;
        if (a.medianAgeDays() > c.aMaxAge()) return false;
        if (c.alertWatchedOnly && !watched) return false;
        if (!watched && !c.alertKeywords.isBlank()) {
            String name = a.rec().item().toLowerCase(Locale.ROOT), cat = Category.of(a.rec().item()).toLowerCase(Locale.ROOT);
            boolean hit = false;
            for (String k : c.alertKeywords.toLowerCase(Locale.ROOT).split(",")) {
                k = k.trim();
                if (!k.isEmpty() && (name.contains(k) || cat.contains(k))) { hit = true; break; }
            }
            if (!hit) return false;
        }
        return true;
    }

    /** True if this listing matches a watch entry's name and target buy price. */
    public static boolean watchMatches(PriceRecord r, Config.WatchItem w) {
        if (w.name.isBlank()) return false;
        return r.item().toLowerCase(Locale.ROOT).contains(w.name.toLowerCase(Locale.ROOT).trim());
    }

    public static Config.WatchItem findWatch(PriceRecord r, Config c) {
        for (Config.WatchItem w : c.watch) if (watchMatches(r, w)) return w;
        return null;
    }

    /** Watch rule: name match, target buy met, and the per-watch profit/ROI/confidence floors (when set and measurable). */
    public static boolean watchQualifies(Analysis a, Config.WatchItem w) {
        if (!a.recentlySeen()) return false;
        if (w.maxUnit > 0 && a.rec().unitPrice() > w.maxUnit) return false;
        boolean needsEstimate = w.minProfit > 0 || w.minRoi > 0 || w.minConfidence > 0;
        if (needsEstimate) {
            if (a.estimatedNetProfit() == null || a.roiPercent() == null) return false;
            if (a.estimatedNetProfit() < w.minProfit || a.roiPercent() < w.minRoi || a.confidence() < w.minConfidence) return false;
        }
        return w.maxUnit > 0 || needsEstimate;
    }

    /** Applies de-duplication, price-drop detection and cooldown, and records the alert when it passes. */
    public boolean consider(Analysis a, Config c, long now, boolean watched) {
        boolean ok = watched ? true : qualifies(a, c, false);
        if (!ok) return false;
        return gate(a, c, now, watched);
    }

    public boolean considerWatch(Analysis a, Config c, Config.WatchItem w, long now) {
        if (!watchQualifies(a, w)) return false;
        return gate(a, c, now, true);
    }

    private boolean gate(Analysis a, Config c, long now, boolean watch) {
        String fp = fingerprint(a.rec());
        double price = a.rec().price();
        double[] prev = last.get(fp);
        if (prev != null) {
            boolean cooled = now - (long) prev[1] >= c.alertCooldownSec * 1000L;
            boolean cheaper = price <= prev[0] * DROP_FACTOR;
            if (!(cheaper && cooled)) return false;
        }
        last.put(fp, new double[]{price, now});
        double net = a.estimatedNetProfit() == null ? 0 : a.estimatedNetProfit();
        double roi = a.roiPercent() == null || Double.isNaN(a.roiPercent()) ? 0 : a.roiPercent();
        history.addFirst(new AlertEntry(a.rec().item(), a.rec().quantity(), a.rec().page(), price,
                a.suggestedResalePrice() == null ? 0 : a.suggestedResalePrice(), a.recommendedMaxBuy(), net, roi,
                a.level(), now, watch ? "Watch target met." : a.why(), watch));
        while (history.size() > MAX_HISTORY) history.removeLast();
        unread++;
        return true;
    }
}
