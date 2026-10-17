package com.donutflip;

import java.util.Locale;

/** Number/time formatting shared by the GUI, alerts and tests. */
public final class Fmt {
    private Fmt() { }

    public static String money(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return "-";
        double a = Math.abs(v);
        if (a >= 1e9) return String.format(Locale.ROOT, "%.2fB", v / 1e9);
        if (a >= 1e6) return String.format(Locale.ROOT, "%.2fM", v / 1e6);
        if (a >= 1e4) return String.format(Locale.ROOT, "%.1fK", v / 1e3);
        if (a >= 1000) return String.format(Locale.ROOT, "%,.0f", v);
        if (a >= 100) return String.format(Locale.ROOT, "%.0f", v);
        String s = String.format(Locale.ROOT, "%.2f", v);
        if (s.contains(".")) s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
        return s;
    }

    public static String signed(double v) { return (v >= 0 ? "+" : "-") + money(Math.abs(v)); }
    public static String pct(double v) { return Double.isNaN(v) ? "-" : String.format(Locale.ROOT, "%.0f%%", v); }

    public static String age(long ms) {
        long s = Math.max(0, ms) / 1000;
        if (s < 90) return s + "s";
        long m = s / 60;
        if (m < 90) return m + "m";
        long h = m / 60;
        if (h < 48) return h + "h";
        return (h / 24) + "d";
    }

    public static String days(double d) { return d < 1 ? String.format(Locale.ROOT, "%.0fh", Math.max(0, d * 24)) : String.format(Locale.ROOT, "%.0fd", d); }
}
