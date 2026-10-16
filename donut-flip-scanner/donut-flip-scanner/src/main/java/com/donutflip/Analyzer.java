package com.donutflip;

import java.util.*;

public final class Analyzer {
    public enum Rec {
        BUY_CANDIDATE("BUY CANDIDATE"), AVOID("AVOID"), INSUFFICIENT("INSUFFICIENT DATA");
        public final String label;
        Rec(String l) { label = l; }
    }

    public record Analysis(PriceRecord rec, double observedListingPrice, Double estimatedMarketPrice,
                           Double suggestedResalePrice, double estimatedFees, Double estimatedNetProfit,
                           Double roiPercent, int confidence, int sampleSize, long lastUpdated,
                           Rec recommendation, String reason) { }

    public record HistoryRow(String label, int count, double medianUnit, double minUnit, double maxUnit,
                             long lastUpdated, int confidence) { }

    private static final double DAY_MS = 86_400_000.0;

    /** Comparable-group key: same item, same attributes, similar durability and stack-size bucket. */
    static String key(PriceRecord r) {
        String q = r.quantity() == 1 ? "1" : r.quantity() <= 16 ? "2-16" : r.quantity() <= 64 ? "17-64" : "65+";
        String d = r.durabilityPct() < 0 ? "n/a" : (r.durabilityPct() / 10 * 10) + "";
        return r.item().toLowerCase(Locale.ROOT) + "|" + r.attrs() + "|d" + d + "|q" + q;
    }

    static String label(PriceRecord r) {
        String q = r.quantity() == 1 ? "x1" : r.quantity() <= 16 ? "x2-16" : r.quantity() <= 64 ? "x17-64" : "x65+";
        StringBuilder sb = new StringBuilder(r.item()).append(' ').append(q);
        if (!r.attrs().isEmpty()) sb.append(" [").append(r.attrs()).append(']');
        if (r.durabilityPct() >= 0) sb.append(" dur ").append(r.durabilityPct() / 10 * 10).append("%+");
        return sb.toString();
    }

    static double ageDays(PriceRecord r, long now, int maxAge) {
        return r.timestamp() <= 0 ? maxAge : Math.max(0, (now - r.timestamp()) / DAY_MS);
    }

    static double median(List<Double> v) {
        List<Double> s = new ArrayList<>(v);
        Collections.sort(s);
        int n = s.size();
        if (n == 0) return 0;
        return n % 2 == 1 ? s.get(n / 2) : (s.get(n / 2 - 1) + s.get(n / 2)) / 2.0;
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

    public static List<Analysis> analyze(List<PriceRecord> all, Config cfg, long now) {
        Map<String, List<PriceRecord>> groups = new HashMap<>();
        for (PriceRecord r : all) groups.computeIfAbsent(key(r), k -> new ArrayList<>()).add(r);

        List<Analysis> out = new ArrayList<>();
        for (PriceRecord r : all) {
            List<Double> units = new ArrayList<>(), ages = new ArrayList<>();
            long last = r.timestamp();
            for (PriceRecord o : groups.get(key(r))) {
                if (o == r) continue;
                double age = ageDays(o, now, cfg.maxAgeDays);
                if (age > cfg.maxAgeDays) continue;
                units.add(o.unitPrice());
                ages.add(age);
                last = Math.max(last, o.timestamp());
            }
            int n = units.size();
            if (n < cfg.minSamples) {
                out.add(new Analysis(r, r.price(), null, null, 0, null, null, 0, n, last, Rec.INSUFFICIENT,
                        "Only " + n + " comparable record(s); need " + cfg.minSamples));
                continue;
            }
            double market = median(units) * r.quantity();
            double resale = Math.floor(market * (1 - cfg.undercutPercent / 100.0));
            double fees = resale * cfg.effectiveFeeRate() / 100.0;
            double net = resale - fees - r.price();
            double roi = net / r.price() * 100.0;
            int conf = confidence(units, ages, cfg.maxAgeDays);

            Rec rec; String why;
            if (ageDays(r, now, cfg.maxAgeDays) >= cfg.maxAgeDays) { rec = Rec.AVOID; why = "Listing record is stale or undated"; }
            else if (net <= 0) { rec = Rec.AVOID; why = "Resale estimate is not above cost after fees"; }
            else if (conf < cfg.minConfidence) { rec = Rec.AVOID; why = "Confidence below your minimum"; }
            else if (net < cfg.minProfit) { rec = Rec.AVOID; why = "Profit below your minimum"; }
            else { rec = Rec.BUY_CANDIDATE; why = "Below comparable median"; }
            out.add(new Analysis(r, r.price(), market, resale, fees, net, roi, conf, n, last, rec, why));
        }
        return out;
    }

    public static List<HistoryRow> history(List<PriceRecord> all, Config cfg, long now) {
        Map<String, List<PriceRecord>> groups = new TreeMap<>();
        for (PriceRecord r : all) groups.computeIfAbsent(key(r), k -> new ArrayList<>()).add(r);
        List<HistoryRow> rows = new ArrayList<>();
        for (List<PriceRecord> g : groups.values()) {
            List<Double> u = new ArrayList<>(), a = new ArrayList<>();
            long last = 0;
            for (PriceRecord r : g) { u.add(r.unitPrice()); a.add(ageDays(r, now, cfg.maxAgeDays)); last = Math.max(last, r.timestamp()); }
            rows.add(new HistoryRow(label(g.get(0)), g.size(), median(u), Collections.min(u), Collections.max(u), last,
                    confidence(u, a, cfg.maxAgeDays)));
        }
        return rows;
    }
}
