package com.donutflip;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;

/** Records come from import.csv (manual) and captured.csv (passively read from the AH screen you open). */
public final class Store {
    public static final String HEADER_HELP = """
            # Donut Flip Scanner import file. One asking-price observation per line (NOT completed sales).
            # item,quantity,price,attributes,durability_percent,timestamp
            #  - price: total asking price for the stack; accepts 1500, 1,500, 1.5k, 2m, 3b
            #  - attributes: enchants/other traits joined with ';' e.g. sharpness5;unbreaking3 (blank = none)
            #  - durability_percent: 0-100, or blank if not applicable
            #  - timestamp: yyyy-MM-dd or epoch milliseconds. Blank = unknown (treated as stale).
            # Lines starting with # are ignored.
            """;
    private static final int MAX_CAPTURED = 20000;

    public List<PriceRecord> imported = new ArrayList<>();
    public List<PriceRecord> captured = new ArrayList<>();
    /** Combined view used by the analyzer. */
    public List<PriceRecord> records = new ArrayList<>();
    private final Set<String> sigs = new HashSet<>();
    public String status = "No import loaded yet.";
    public int skipped = 0;

    public static Path file() { return Config.dir().resolve("import.csv"); }
    public static Path capturedFile() { return Config.dir().resolve("captured.csv"); }

    public void load() {
        imported = new ArrayList<>();
        captured = new ArrayList<>();
        sigs.clear();
        skipped = 0;
        try {
            Files.createDirectories(Config.dir());
            if (!Files.exists(file())) Files.writeString(file(), HEADER_HELP);
            for (String line : Files.readAllLines(file())) {
                String t = line.trim();
                if (t.isEmpty() || t.startsWith("#")) continue;
                PriceRecord r = parse(t);
                if (r == null) skipped++; else imported.add(r);
            }
            if (Files.exists(capturedFile())) {
                for (String line : Files.readAllLines(capturedFile())) {
                    PriceRecord r = parse(line.trim());
                    if (r != null) { captured.add(r); sigs.add(sig(r)); }
                }
            }
            status = imported.size() + " imported, " + captured.size() + " captured"
                    + (skipped > 0 ? " (" + skipped + " bad line(s) skipped)" : "");
        } catch (Exception e) {
            status = "Could not read data files: " + e.getMessage();
        }
        combine();
    }

    private void combine() {
        records = new ArrayList<>(imported.size() + captured.size());
        records.addAll(imported);
        records.addAll(captured);
    }

    private static String sig(PriceRecord r) {
        return r.item() + "|" + r.quantity() + "|" + Math.round(r.price()) + "|" + r.attrs() + "|" + r.durabilityPct();
    }

    /** Returns true if this listing was not already known. Call flushCaptured() after a batch. */
    public boolean addCaptured(PriceRecord r) {
        if (!sigs.add(sig(r))) return false;
        captured.add(r);
        return true;
    }

    public void flushCaptured() {
        while (captured.size() > MAX_CAPTURED) captured.remove(0);
        combine();
        status = imported.size() + " imported, " + captured.size() + " captured";
        try {
            Files.createDirectories(Config.dir());
            StringBuilder sb = new StringBuilder();
            for (PriceRecord r : captured) {
                sb.append(r.item()).append(',').append(r.quantity()).append(',')
                  .append(String.format(Locale.ROOT, "%.2f", r.price())).append(',')
                  .append(r.attrs()).append(',').append(r.durabilityPct() < 0 ? "" : String.valueOf(r.durabilityPct()))
                  .append(',').append(r.timestamp()).append('\n');
            }
            Files.writeString(capturedFile(), sb.toString());
        } catch (Exception ignored) { }
    }

    public void clearCaptured() {
        captured.clear();
        sigs.clear();
        try { Files.deleteIfExists(capturedFile()); } catch (Exception ignored) { }
        combine();
        status = imported.size() + " imported, 0 captured";
    }

    static PriceRecord parse(String line) {
        try {
            if (line.isEmpty() || line.startsWith("#")) return null;
            String[] c = line.split(",", -1);
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
        s = s.trim().toLowerCase(Locale.ROOT).replace("$", "").replace("_", "").replace(",", "");
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
