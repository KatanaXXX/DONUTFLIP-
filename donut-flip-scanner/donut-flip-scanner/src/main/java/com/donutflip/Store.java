package com.donutflip;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Loads user-provided records from config/donutflipscanner/import.csv. No network, no server access. */
public final class Store {
    public static final String HEADER_HELP = """
            # Donut Flip Scanner import file. One asking-price observation per line (NOT completed sales).
            # item,quantity,price,attributes,durability_percent,timestamp
            #  - price: total asking price for the stack; accepts 1500, 1,500, 1.5k, 2m, 3b
            #  - attributes: enchants/other traits joined with ';' e.g. sharpness5;unbreaking3 (blank = none)
            #  - durability_percent: 0-100, or blank if not applicable
            #  - timestamp: yyyy-MM-dd or epoch milliseconds. Blank = unknown (treated as stale).
            # Add only prices you actually observed. Lines starting with # are ignored.
            """;

    public List<PriceRecord> records = new ArrayList<>();
    public String status = "No import loaded yet.";
    public int skipped = 0;

    public static Path file() { return Config.dir().resolve("import.csv"); }

    public void load() {
        records = new ArrayList<>();
        skipped = 0;
        try {
            Files.createDirectories(Config.dir());
            if (!Files.exists(file())) {
                Files.writeString(file(), HEADER_HELP);
                status = "Created empty import.csv - add your recorded listings, then press Reload.";
                return;
            }
            for (String line : Files.readAllLines(file())) {
                String t = line.trim();
                if (t.isEmpty() || t.startsWith("#")) continue;
                PriceRecord r = parse(t);
                if (r == null) skipped++; else records.add(r);
            }
            status = records.size() + " record(s) loaded from import.csv" + (skipped > 0 ? " (" + skipped + " malformed line(s) skipped)" : "");
        } catch (Exception e) {
            status = "Could not read import.csv: " + e.getMessage();
        }
    }

    static PriceRecord parse(String line) {
        try {
            String[] c = line.split(",", -1);
            // price may contain thousands commas only if quoted; keep format simple: require 6 columns.
            if (c.length < 3) return null;
            String item = c[0].trim();
            int qty = Integer.parseInt(c[1].trim());
            double price = parsePrice(c[2]);
            String attrs = c.length > 3 ? c[3].trim().toLowerCase(Locale.ROOT) : "";
            int dur = (c.length > 4 && !c[4].isBlank()) ? Integer.parseInt(c[4].trim()) : -1;
            long ts = c.length > 5 ? parseTime(c[5].trim()) : 0;
            if (item.isEmpty() || qty < 1 || price <= 0) return null;
            return new PriceRecord(item, qty, price, attrs, dur, ts);
        } catch (Exception e) {
            return null;
        }
    }

    static double parsePrice(String s) {
        s = s.trim().toLowerCase(Locale.ROOT).replace("$", "").replace("_", "");
        double mult = 1;
        if (s.endsWith("k")) { mult = 1e3; s = s.substring(0, s.length() - 1); }
        else if (s.endsWith("m")) { mult = 1e6; s = s.substring(0, s.length() - 1); }
        else if (s.endsWith("b")) { mult = 1e9; s = s.substring(0, s.length() - 1); }
        return Double.parseDouble(s) * mult;
    }

    static long parseTime(String s) {
        if (s.isEmpty()) return 0;
        try { return Long.parseLong(s); } catch (NumberFormatException ignored) { }
        try { return LocalDate.parse(s).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(); } catch (Exception ignored) { }
        try { return Instant.parse(s).toEpochMilli(); } catch (Exception ignored) { }
        return 0;
    }
}
