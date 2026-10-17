package com.donutflip;

import java.util.ArrayList;
import java.util.List;

/** Session accounting. Only recorded sales count as realized profit; estimates never enter here. */
public final class Ledger {
    public record Entry(String label, double cost, double soldFor, double fee, double net, long at) { }

    private final List<Entry> entries = new ArrayList<>();
    private final long startedAt;

    public Ledger(long startedAt) { this.startedAt = startedAt; }

    /** Records a completed sale. {@code cost} may be 0 when only revenue is known. */
    public Entry recordSale(String label, double cost, double soldFor, double feePct, long now) {
        if (soldFor < 0 || cost < 0 || Double.isNaN(soldFor) || Double.isNaN(cost)) throw new IllegalArgumentException("bad amounts");
        double fee = Calc.fee(soldFor, feePct);
        Entry e = new Entry(label, cost, soldFor, fee, soldFor - fee - cost, now);
        entries.add(e);
        return e;
    }

    public double realized() { double t = 0; for (Entry e : entries) t += e.net(); return t; }
    public double totalCost() { double t = 0; for (Entry e : entries) t += e.cost(); return t; }
    public double totalFees() { double t = 0; for (Entry e : entries) t += e.fee(); return t; }
    public int count() { return entries.size(); }
    public List<Entry> entries() { return entries; }

    public double perHour(long now) {
        double hrs = Math.max(1.0 / 60, (now - startedAt) / 3_600_000.0);
        return realized() / hrs;
    }

    public void clear() { entries.clear(); }

    public void undoLast() { if (!entries.isEmpty()) entries.remove(entries.size() - 1); }
}
