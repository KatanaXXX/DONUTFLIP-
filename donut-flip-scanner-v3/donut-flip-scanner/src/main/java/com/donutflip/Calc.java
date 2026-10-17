package com.donutflip;

/** Pure money math (no Minecraft dependencies, unit tested). All amounts are totals unless stated. */
public final class Calc {
    private Calc() { }

    /** Fee charged on a sale, given a fee percentage (0-100). */
    public static double fee(double sell, double feePct) { return sell * feePct / 100.0; }

    /** Money kept after selling for {@code sell}, paying the fee, and having bought for {@code buy}. */
    public static double netProfit(double sell, double buy, double feePct) { return sell - fee(sell, feePct) - buy; }

    /** Net profit divided by the purchase cost, as a percentage. Returns NaN if buy <= 0. */
    public static double roiPercent(double net, double buy) { return buy > 0 ? net / buy * 100.0 : Double.NaN; }

    /** The lowest sell price that recovers the purchase cost after fees. Infinity if fee >= 100%. */
    public static double breakEvenSell(double buy, double feePct) {
        double keep = 1 - feePct / 100.0;
        return keep > 0 ? buy / keep : Double.POSITIVE_INFINITY;
    }

    /** The highest buy price that still reaches {@code targetRoiPct} when selling for {@code sell}. */
    public static double maxBuyForRoi(double sell, double feePct, double targetRoiPct) {
        return (sell - fee(sell, feePct)) / (1 + targetRoiPct / 100.0);
    }
}
