package com.donutflip;

import com.donutflip.Analyzer.Analysis;
import com.donutflip.Analyzer.HistoryRow;
import com.donutflip.Analyzer.Rec;

import java.util.*;

/** Cached market snapshot shared by the GUI and alerts. Recomputed only when data/settings change (or every minute). */
public final class Market {
    private Market() { }

    public record Stats(int captured, int imported, int candidates, int watch, int avoid, int insufficient, int fresh,
                        double potential, Analysis top, long latestCapture, double freshPct, double medianAgeDays) { }

    public record Snapshot(List<Analysis> analyses, List<HistoryRow> history, Stats stats,
                           Map<PriceRecord, Analysis> byRecord, long builtAt, long computeMs, int id) { }

    private static Snapshot cache;
    private static String cacheKey = "";
    private static int counter = 0;
    public static final long NEW_WINDOW_MS = 10 * 60_000L;

    /** Call on the client thread. */
    public static Snapshot get(Store s, Config c, long now) {
        return get(s.version, s.records, new ArrayList<>(s.captured), s.imported.size(), c, now);
    }

    /** Takes already-copied inputs, so it may also run on a worker thread. */
    public static synchronized Snapshot get(int version, List<PriceRecord> records, List<PriceRecord> captured,
                                            int importedCount, Config c, long now) {
        String k = version + "|" + c.analysisKey() + "|" + (now / 60_000L);
        if (cache != null && k.equals(cacheKey)) return cache;
        cache = build(records, c, now, captured, importedCount);
        cacheKey = k;
        return cache;
    }

    public static synchronized void invalidate() { cacheKey = ""; }

    static Snapshot build(List<PriceRecord> records, Config c, long now, List<PriceRecord> captured, int importedCount) {
        long t0 = System.nanoTime();
        List<Analysis> an = Analyzer.analyze(records, c, now);
        List<HistoryRow> hist = Analyzer.history(records, c, now);
        Map<PriceRecord, Analysis> map = new IdentityHashMap<>();
        int buy = 0, watch = 0, avoid = 0, insuf = 0, fresh = 0, inAge = 0;
        double potential = 0;
        Analysis top = null;
        List<Double> ages = new ArrayList<>();
        for (Analysis a : an) {
            map.put(a.rec(), a);
            switch (a.recommendation()) {
                case BUY_CANDIDATE -> {
                    buy++;
                    potential += a.estimatedNetProfit() == null ? 0 : a.estimatedNetProfit();
                    if (a.rec().timestamp() > 0 && a.rec().firstSeen() >= now - NEW_WINDOW_MS) fresh++;
                    if (top == null || a.score() > top.score()
                            || (a.score() == top.score() && a.estimatedNetProfit() > top.estimatedNetProfit())) top = a;
                }
                case WATCH -> watch++;
                case INSUFFICIENT -> insuf++;
                default -> avoid++;
            }
            double age = Analyzer.ageDays(a.rec(), now, c.maxAgeDays);
            if (age <= c.maxAgeDays && a.rec().timestamp() > 0) { inAge++; ages.add(age); }
        }
        long latest = 0;
        for (PriceRecord r : captured) latest = Math.max(latest, r.timestamp());
        double freshPct = records.isEmpty() ? 0 : inAge * 100.0 / records.size();
        Stats st = new Stats(captured.size(), importedCount, buy, watch, avoid, insuf, fresh, potential, top, latest,
                freshPct, ages.isEmpty() ? 0 : Analyzer.median(ages));
        return new Snapshot(an, hist, st, map, now, (System.nanoTime() - t0) / 1_000_000L, ++counter);
    }
}
