package com.donutflip;

import java.util.*;

/**
 * Market analysis (no Minecraft dependencies, unit tested).
 *
 * Method, in short:
 *  1. Group records into comparables: same item, same enchant/attribute string, same 10% durability band, same stack-size band.
 *  2. For each listing, take the OTHER recent records in its group (older than maxAgeDays are ignored).
 *  3. Drop outliers (further than 3.5 scaled-MADs from the median) when at least minSamples records remain.
 *  4. Estimate market price = recency-weighted median unit price x quantity (weight halves every maxAge/2 days).
 *  5. Suggested resale = estimate minus the undercut %; fees apply only when marked verified.
 *  6. Confidence (0-100) = sample size (50) + recency (25) + price consistency (25).
 *  7. Recommended max buy = the highest price that still satisfies BOTH your minimum profit and minimum ROI,
 *     so "price <= max buy" is exactly the profit/ROI part of a BUY CANDIDATE.
 * Everything is derived from asking prices. Nothing here knows whether anything actually sold.
 */
public final class Analyzer {
    private Analyzer() { }

    public enum Rec {
        BUY_CANDIDATE("BUY CANDIDATE"), WATCH("WATCH"), BAD_MARGIN("BAD MARGIN (EST.)"),
        HIGH_RISK("HIGH RISK"), INSUFFICIENT("INSUFFICIENT DATA");
        public final String label;
        Rec(String l) { label = l; }
        public boolean isAvoid() { return this == BAD_MARGIN || this == HIGH_RISK || this == INSUFFICIENT; }
    }

    public enum Level { HIGH, MEDIUM, LOW, INSUFFICIENT }

    public record Analysis(PriceRecord rec, double observedListingPrice, Double estimatedMarketPrice,
                           Double suggestedResalePrice, Double recommendedMaxBuy, double estimatedFees,
                           Double estimatedNetProfit, Double roiPercent, int confidence, Level level,
                           int sampleSize, long lastUpdated, double medianAgeDays, double volatility,
                           int score, Rec recommendation, List<String> reasons, String why, String evidence,
                           boolean outlierListing, boolean recentlySeen) {
        public String primaryReason() { return reasons.isEmpty() ? "" : reasons.get(0); }
    }

    public record HistoryRow(String label, String category, int count, double medianUnit, double minUnit, double maxUnit,
                             long lastUpdated, double latestUnit, double lowestRecentUnit, double resaleUnit,
                             int confidence, double volatility, double medianAgeDays, Double trendPct, double[] spark) { }

    private static final double DAY_MS = 86_400_000.0;
    private static final int MAX_GROUP = 300;

    // ---------- grouping ----------
    static String key(PriceRecord r) {
        String d = r.durabilityPct() < 0 ? "n/a" : String.valueOf(r.durabilityPct() / 10 * 10);
        return r.item().toLowerCase(Locale.ROOT) + "|" + r.attrs() + "|d" + d + "|q" + qtyBand(r.quantity());
    }

    static String qtyBand(int q) { return q == 1 ? "1" : q <= 16 ? "2-16" : q <= 64 ? "17-64" : "65+"; }

    public static String label(PriceRecord r) {
        StringBuilder sb = new StringBuilder(r.item()).append(" x").append(r.quantity());
        if (!r.attrs().isEmpty()) sb.append(" [").append(r.attrs()).append(']');
        if (r.durabilityPct() >= 0) sb.append(" dur ").append(r.durabilityPct()).append('%');
        return sb.toString();
    }

    static String groupLabel(PriceRecord r) {
        StringBuilder sb = new StringBuilder(r.item()).append(" x").append(qtyBand(r.quantity()));
        if (!r.attrs().isEmpty()) sb.append(" [").append(r.attrs()).append(']');
        if (r.durabilityPct() >= 0) sb.append(" dur ").append(r.durabilityPct() / 10 * 10).append("%+");
        return sb.toString();
    }

    static double ageDays(PriceRecord r, long now, int maxAge) {
        return r.timestamp() <= 0 ? maxAge : Math.max(0, (now - r.timestamp()) / DAY_MS);
    }

    // ---------- statistics ----------
    static double median(List<Double> v) {
        List<Double> s = new ArrayList<>(v);
        Collections.sort(s);
        int n = s.size();
        if (n == 0) return 0;
        return n % 2 == 1 ? s.get(n / 2) : (s.get(n / 2 - 1) + s.get(n / 2)) / 2.0;
    }

    /** Weighted median; with equal weights this equals the ordinary median. */
    static double weightedMedian(double[] v, double[] w) {
        int n = v.length;
        if (n == 0) return 0;
        Integer[] idx = new Integer[n];
        for (int i = 0; i < n; i++) idx[i] = i;
        Arrays.sort(idx, Comparator.comparingDouble(i -> v[i]));
        double total = 0;
        for (double x : w) total += x;
        double half = total / 2.0, cum = 0;
        for (int k = 0; k < n; k++) {
            cum += w[idx[k]];
            if (Math.abs(cum - half) < 1e-12 && k + 1 < n) return (v[idx[k]] + v[idx[k + 1]]) / 2.0;
            if (cum > half) return v[idx[k]];
        }
        return v[idx[n - 1]];
    }

    static int confidence(List<Double> units, List<Double> ages, int maxAge) {
        int n = units.size();
        double sample = Math.min(1.0, n / 8.0) * 50;
        double recency = Math.max(0, 1 - median(ages) / Math.max(1, maxAge)) * 25;
        double med = median(units);
        List<Double> dev = new ArrayList<>();
        for (double u : units) dev.add(Math.abs(u - med));
        double rel = med > 0 ? median(dev) / med : 1;
        double consistency = Math.max(0, 1 - rel / 0.5) * 25;
        return (int) Math.round(Math.min(100, sample + recency + consistency));
    }

    public static Level levelOf(int conf, boolean insufficient) {
        if (insufficient) return Level.INSUFFICIENT;
        return conf >= 70 ? Level.HIGH : conf >= 40 ? Level.MEDIUM : Level.LOW;
    }

    private record Comp(double unit, double age) { }

    // ---------- main analysis ----------
    public static List<Analysis> analyze(List<PriceRecord> all, Config cfg, long now) {
        int maxAge = cfg.maxAgeDays;
        Map<String, List<PriceRecord>> pools = new HashMap<>();
        for (PriceRecord r : all) {
            if (ageDays(r, now, maxAge) <= maxAge) pools.computeIfAbsent(key(r), k -> new ArrayList<>()).add(r);
        }
        for (List<PriceRecord> l : pools.values()) {
            l.sort((a, b) -> Long.compare(b.timestamp(), a.timestamp()));
            if (l.size() > MAX_GROUP) l.subList(MAX_GROUP, l.size()).clear();
        }
        List<Analysis> out = new ArrayList<>(all.size());
        for (PriceRecord r : all) out.add(analyzeOne(r, pools.getOrDefault(key(r), List.of()), cfg, now));
        return out;
    }

    public static Analysis analyzeOne(PriceRecord r, List<PriceRecord> pool, Config cfg, long now) {
        int maxAge = cfg.maxAgeDays;
        List<Comp> comps = new ArrayList<>();
        for (PriceRecord o : pool) {
            if (o == r) continue;
            comps.add(new Comp(o.unitPrice(), ageDays(o, now, maxAge)));
        }
        boolean recent = r.timestamp() > 0 && (now - r.timestamp()) <= cfg.recentSeenMinutes * 60_000L;
        long last = r.timestamp();
        for (PriceRecord o : pool) last = Math.max(last, o.timestamp());

        if (comps.size() < cfg.minSamples) {
            String why = "Only " + comps.size() + " comparable record(s) in the last " + maxAge + " days; need " + cfg.minSamples + ".";
            return new Analysis(r, r.price(), null, null, null, 0, null, null, 0, Level.INSUFFICIENT, comps.size(), last,
                    0, 0, 0, Rec.INSUFFICIENT, List.of(why), why, "", false, recent);
        }

        // outlier handling: robust (median/MAD) filter, only if enough points survive
        List<Double> allUnits = new ArrayList<>();
        for (Comp c : comps) allUnits.add(c.unit());
        double med0 = median(allUnits);
        List<Double> dev0 = new ArrayList<>();
        for (double u : allUnits) dev0.add(Math.abs(u - med0));
        double mad0 = median(dev0);
        List<Comp> keep = new ArrayList<>();
        for (Comp c : comps) if (mad0 <= 0 || Math.abs(c.unit() - med0) <= 3.5 * 1.4826 * mad0) keep.add(c);
        if (keep.size() < cfg.minSamples) keep = comps;
        int outliers = comps.size() - keep.size();

        int n = keep.size();
        double[] v = new double[n], w = new double[n];
        List<Double> units = new ArrayList<>(), ages = new ArrayList<>();
        double half = Math.max(1.0, maxAge / 2.0);
        double minU = Double.MAX_VALUE, maxU = 0;
        for (int i = 0; i < n; i++) {
            Comp c = keep.get(i);
            v[i] = c.unit();
            w[i] = Math.pow(0.5, c.age() / half);
            units.add(c.unit());
            ages.add(c.age());
            minU = Math.min(minU, c.unit());
            maxU = Math.max(maxU, c.unit());
        }
        double marketUnit = weightedMedian(v, w);
        double market = marketUnit * r.quantity();
        double resale = Math.floor(market * (1 - cfg.undercutPercent / 100.0));
        double feePct = cfg.effectiveFeeRate();
        double fees = Calc.fee(resale, feePct);
        double net = Calc.netProfit(resale, r.price(), feePct);
        double roi = Calc.roiPercent(net, r.price());
        double medU = median(units);
        List<Double> dev = new ArrayList<>();
        for (double u : units) dev.add(Math.abs(u - medU));
        double vol = medU > 0 ? median(dev) / medU : 1;
        double medAge = median(ages);
        int conf = confidence(units, ages, maxAge);
        Level level = levelOf(conf, false);
        boolean selfStale = r.timestamp() <= 0 || ageDays(r, now, maxAge) >= maxAge;
        boolean outlierListing = r.unitPrice() < medU * cfg.outlierRatio;
        boolean volatileMkt = vol > cfg.maxVolatility;
        boolean weak = conf < cfg.minConfidence;

        Double maxBuy = null;
        if (!weak && !volatileMkt) {
            double byProfit = resale - fees - cfg.minProfit;
            double byRoi = Calc.maxBuyForRoi(resale, feePct, cfg.minRoi);
            double mb = Math.floor(Math.min(byProfit, byRoi));
            if (mb > 0) maxBuy = mb;
        }

        List<String> reasons = new ArrayList<>();
        if (selfStale) reasons.add("This listing's record is stale or undated, so it may no longer exist.");
        if (net <= 0) reasons.add("Estimated resale ~" + Fmt.money(resale) + " does not exceed the cost " + Fmt.money(r.price())
                + " after fees (net ~" + Fmt.signed(net) + ").");
        if (volatileMkt) reasons.add("Comparable prices are volatile (typical spread " + Fmt.pct(vol * 100) + " of the median).");
        if (outlierListing) reasons.add("Listing is far below comparable prices; it may be a different item or bad data.");
        if (weak) reasons.add("Weak estimate: confidence " + conf + "% is below your minimum " + cfg.minConfidence + "%.");
        boolean thin = net > 0 && (net < cfg.minProfit || (!Double.isNaN(roi) && roi < cfg.minRoi));
        if (thin) reasons.add("Margin is thin: ~" + Fmt.signed(net) + " (" + Fmt.pct(roi) + ") is below your minimum profit/ROI.");

        Rec rec;
        if (selfStale || volatileMkt || outlierListing || weak) rec = Rec.HIGH_RISK;
        else if (net <= 0) rec = Rec.BAD_MARGIN;
        else if (thin) rec = Rec.WATCH;
        else rec = Rec.BUY_CANDIDATE;
        // a negative margin on a solid estimate is a firm "bad margin"; with a shaky one it is only "high risk"
        if (net <= 0 && !(selfStale || volatileMkt || outlierListing || weak)) rec = Rec.BAD_MARGIN;

        String why;
        if (rec == Rec.BUY_CANDIDATE) {
            double below = (1 - r.unitPrice() / marketUnit) * 100;
            reasons.add("Listed " + Fmt.pct(below) + " below the recency-weighted comparable median.");
            why = "Listed " + Fmt.pct(below) + " below the comparable median; " + n + " recent observation(s) support a positive margin"
                    + (feePct > 0 ? " after fees." : " (no verified fees applied).");
        } else {
            why = reasons.get(0);
        }
        String evidence = "median " + Fmt.money(marketUnit) + "/unit of n=" + n + " (range " + Fmt.money(minU) + "-" + Fmt.money(maxU)
                + "), data ~" + Fmt.days(medAge) + " old" + (outliers > 0 ? ", " + outliers + " outlier(s) ignored" : "");
        int score = score(net, roi, conf, medAge, n, vol, cfg, maxAge);
        if (rec.isAvoid()) score = Math.min(score, 39);
        return new Analysis(r, r.price(), market, resale, maxBuy, fees, net, roi, conf, level, n, last, medAge, vol, score,
                rec, reasons, why, evidence, outlierListing, recent);
    }

    /**
     * Configurable 0-100 score: weighted blend of profit, ROI, confidence, freshness, sample size and calm prices
     * (weights in Config). It is capped by confidence when confidence is below your minimum, so a big profit on
     * thin evidence can never look like a top deal.
     */
    static int score(double net, double roi, int conf, double medAge, int n, double vol, Config c, int maxAge) {
        double sum = c.wProfit + c.wRoi + c.wConf + c.wFresh + c.wSample + c.wVol;
        if (sum <= 0) return 0;
        double base = Math.max(50, c.minProfit);
        double pc = clamp01(Math.log10(1 + Math.max(0, net)) / Math.log10(1 + base * 20));
        double rc = Double.isNaN(roi) ? 0 : clamp01(roi / 100.0);
        double cc = clamp01(conf / 100.0);
        double fc = clamp01(1 - medAge / Math.max(1, maxAge));
        double sc = clamp01(n / 8.0);
        double vc = clamp01(1 - vol / Math.max(0.01, c.maxVolatility));
        int s = (int) Math.round((c.wProfit * pc + c.wRoi * rc + c.wConf * cc + c.wFresh * fc + c.wSample * sc + c.wVol * vc) / sum * 100);
        if (conf < c.minConfidence) s = Math.min(s, conf);
        return Math.max(0, Math.min(100, s));
    }

    private static double clamp01(double x) { return Double.isNaN(x) ? 0 : Math.max(0, Math.min(1, x)); }

    // ---------- sorting ----------
    /** Best-first ordering. Keys: PROFIT, ROI, CONFIDENCE, FRESH, PAGE (lowest first, unknown last), SCORE. */
    public static Comparator<Analysis> comparator(String key) {
        Comparator<Analysis> byNet = Comparator.comparingDouble((Analysis a) -> a.estimatedNetProfit() == null ? -1e18 : a.estimatedNetProfit());
        Comparator<Analysis> c;
        switch (key) {
            case "ROI" -> c = Comparator.comparingDouble((Analysis a) -> a.roiPercent() == null || Double.isNaN(a.roiPercent()) ? -1e18 : a.roiPercent());
            case "CONFIDENCE" -> c = Comparator.comparingInt(Analysis::confidence);
            case "FRESH" -> c = Comparator.comparingLong((Analysis a) -> a.rec().timestamp());
            case "SCORE" -> c = Comparator.comparingInt(Analysis::score);
            case "PAGE" -> {
                return Comparator.comparingInt((Analysis a) -> a.rec().page() > 0 ? a.rec().page() : Integer.MAX_VALUE)
                        .thenComparing(byNet.reversed());
            }
            default -> c = byNet;
        }
        return c.reversed().thenComparing(byNet.reversed());
    }

    // ---------- market history ----------
    public static List<HistoryRow> history(List<PriceRecord> all, Config cfg, long now) {
        int maxAge = cfg.maxAgeDays;
        long cutoff = cfg.marketRangeDays > 0 ? now - (long) (cfg.marketRangeDays * DAY_MS) : Long.MIN_VALUE;
        Map<String, List<PriceRecord>> groups = new TreeMap<>();
        for (PriceRecord r : all) {
            if (cfg.marketRangeDays > 0 && (r.timestamp() <= 0 || r.timestamp() < cutoff)) continue;
            groups.computeIfAbsent(key(r), k -> new ArrayList<>()).add(r);
        }
        List<HistoryRow> rows = new ArrayList<>();
        for (List<PriceRecord> g : groups.values()) {
            int n = g.size();
            double[] v = new double[n], w = new double[n];
            List<Double> u = new ArrayList<>(), a = new ArrayList<>();
            double half = Math.max(1.0, maxAge / 2.0);
            long last = 0;
            PriceRecord latest = g.get(0);
            double lowest = Double.MAX_VALUE, hi = 0;
            for (int i = 0; i < n; i++) {
                PriceRecord r = g.get(i);
                double age = ageDays(r, now, maxAge);
                v[i] = r.unitPrice(); w[i] = Math.pow(0.5, age / half);
                u.add(r.unitPrice()); a.add(age);
                if (r.timestamp() >= latest.timestamp()) latest = r;
                last = Math.max(last, r.timestamp());
                hi = Math.max(hi, r.unitPrice());
                if (age <= maxAge) lowest = Math.min(lowest, r.unitPrice());
            }
            double med = weightedMedian(v, w);
            double plain = median(u);
            List<Double> dev = new ArrayList<>();
            for (double x : u) dev.add(Math.abs(x - plain));
            double vol = plain > 0 ? median(dev) / plain : 1;

            List<PriceRecord> timed = new ArrayList<>();
            for (PriceRecord r : g) if (r.timestamp() > 0) timed.add(r);
            timed.sort(Comparator.comparingLong(PriceRecord::timestamp));
            Double trend = null;
            if (timed.size() >= 4) {
                int h = timed.size() / 2;
                List<Double> older = new ArrayList<>(), newer = new ArrayList<>();
                for (int i = 0; i < timed.size(); i++) (i < h ? older : newer).add(timed.get(i).unitPrice());
                double mo = median(older);
                if (mo > 0) trend = (median(newer) / mo - 1) * 100;
            }
            double[] spark = new double[0];
            if (timed.size() >= 3) {
                int bins = Math.min(8, timed.size());
                spark = new double[bins];
                for (int b = 0; b < bins; b++) {
                    List<Double> chunk = new ArrayList<>();
                    for (int i = b * timed.size() / bins; i < (b + 1) * timed.size() / bins; i++) chunk.add(timed.get(i).unitPrice());
                    spark[b] = median(chunk);
                }
            }
            rows.add(new HistoryRow(groupLabel(g.get(0)), Category.of(g.get(0).item()), n, med, Collections.min(u), hi, last,
                    latest.unitPrice(), lowest == Double.MAX_VALUE ? Double.NaN : lowest, med * (1 - cfg.undercutPercent / 100.0),
                    confidence(u, a, maxAge), vol, median(a), trend, spark));
        }
        return rows;
    }
}
