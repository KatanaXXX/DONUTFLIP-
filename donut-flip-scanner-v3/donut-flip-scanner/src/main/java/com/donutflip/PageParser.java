package com.donutflip;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads an AH page number from a screen title. Returns -1 (unknown) unless the title explicitly states one. */
public final class PageParser {
    private PageParser() { }

    private static final Pattern P_PAGE = Pattern.compile("(?i)(?:page|pg)\\s*[:#]?\\s*(\\d{1,4})");
    private static final Pattern P_FRACTION = Pattern.compile("\\(\\s*(\\d{1,4})\\s*/\\s*\\d{1,4}\\s*\\)");

    public static int parse(String title) {
        if (title == null) return -1;
        Matcher m = P_PAGE.matcher(title);
        if (m.find()) return clamp(m.group(1));
        m = P_FRACTION.matcher(title);
        if (m.find()) return clamp(m.group(1));
        return -1;
    }

    private static int clamp(String s) {
        try { int p = Integer.parseInt(s); return p >= 1 && p <= 9999 ? p : -1; } catch (Exception e) { return -1; }
    }
}
