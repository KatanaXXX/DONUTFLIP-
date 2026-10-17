package com.donutflip;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Records come from import.csv (manual) and captured.csv (passively read from the AH screen you open). */
public final class Store {
    public static final String HEADER_HELP = """
            # Donut Flip Scanner import file. One asking-price observation per line (NOT completed sales).
            # item,quantity,price,attributes,durability_percent,timestamp,page
            #  - price: total asking price for the stack; accepts 1500, 1,500, 1.5k, 2m, 3b
            #  - attributes: enchants/other traits joined with ';' e.g. sharpness5;unbreaking3 (blank = none)
            #  - durability_percent: 0-100, or blank if not applicable
            #  - timestamp: yyyy-MM-dd or epoch milliseconds. Blank = unknown (treated as stale).
            #  - page: AH page number if you know it (optional)
            # Lines starting with # are ignored.
            """;
    private static final int MAX_CAPTURED = 20000;
    private static final long REFRESH_MS = 60_000;

    public enum Result { NEW, UPDATED, SAME }

    public List<PriceRecord> imported = new ArrayList<>();
    public List<PriceRecord> captured = new ArrayList<>();
    /** Combined, de-duplicated view used by the analyzer. */
    public List<PriceRecord> records = new ArrayList<>();
    private final Map<String, Integer> sigIndex = new HashMap<>();
    /** Bumped whenever the data changes so caches know to recompute. */
    public int version = 0;
    public String status = "No import loaded yet.";
    public boolean error = false;
    public int skipped = 0;

    private static ExecutorService io;
    private static synchronized ExecutorService io() {
        if (io == null) io = Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, "DFS-io"); t.setDaemon(true); return t; });
        return io;
    }

    public static Path file() { return Config.dir().resolve("import.csv"); }
    public static Path capturedFile() { return Config.dir().resolve("captured.csv"); }

    public void load() {
        imported = new ArrayList<>();
        captured = new ArrayList<>();
        sigIndex.clear();
        skipped = 0;
        error = false;
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
                    if (r != null && !sigIndex.containsKey(r.signature())) { sigIndex.put(r.signature(), captured.size()); captured.add(r); }
                }
            }
            status = imported.size() + " imported, " + captured.size() + " captured"
                    + (skipped > 0 ? " (" + skipped + " bad line(s) skipped)" : "");
        } catch (Exception e) {
            status = "Could not read data files: " + e.getMessage();
            error = true;
            System.err.println("[DonutFlipScanner] " + status);
        }
        combine();
    }

    private void combine() {
        List<PriceRecord> all = new ArrayList<>(imported.size() + captured.size());
        all.addAll(imported);
        all.addAll(captured);
        records = dedupe(all);
        version++;
    }

    /** Drops exact duplicate observations (same listing AND same timestamp). Different timestamps are kept as history. */
    public static List<PriceRecord> dedupe(List<PriceRecord> in) {
        Set<String> seen = new HashSet<>();
        List<PriceRecord> out = new ArrayList<>(in.size());
        for (PriceRecord r : in) if (seen.add(r.signature() + "@" + r.timestamp())) out.add(r);
        return out;
    }

    /**
     * Adds a captured listing. NEW = never seen. UPDATED = same listing seen again (refreshes last-seen / page,
     * at most once a minute so recapturing a page doesn't thrash the caches). SAME = nothing changed.
     */
    public Result addCaptured(PriceRecord r) {
        Integer idx = sigIndex.get(r.signature());
        if (idx == null) {
            sigIndex.put(r.signature(), captured.size());
            captured.add(r);
            return Result.NEW;
        }
        PriceRecord old = captured.get(idx);
        boolean pageChanged = r.page() > 0 && r.page() != old.page();
        if (r.timestamp() - old.timestamp() < REFRESH_MS && !pageChanged) return Result.SAME;
        captured.set(idx, old.seenAgain(r.timestamp(), r.page()));
        return Result.UPDATED;
    }

    /** Rebuilds the combined view and writes captured.csv on a background thread. */
    public void flushCaptured() {
        while (captured.size() > MAX_CAPTURED) captured.remove(0);
        if (captured.size() == MAX_CAPTURED) { sigIndex.clear(); for (int i = 0; i < captured.size(); i++) sigIndex.put(captured.get(i).signature(), i); }
        combine();
        status = imported.size() + " imported, " + captured.size() + " captured";
        StringBuilder sb = new StringBuilder();
        for (PriceRecord r : captured) sb.append(toCsv(r)).append('\n');
        final String data = sb.toString();
        io().submit(() -> {
            try {
                Files.createDirectories(Config.dir());
                Files.writeString(capturedFile(), data);
            } catch (Exception e) {
                System.err.println("[DonutFlipScanner] Could not write captured.csv: " + e);
            }
        });
    }

    public void clearCaptured() {
        captured.clear();
        sigIndex.clear();
        io().submit(() -> { try { Files.deleteIfExists(capturedFile()); } catch (Exception ignored) { } });
        combine();
        status = imported.size() + " imported, 0 captured";
    }

    static String toCsv(PriceRecord r) {
        return r.item() + "," + r.quantity() + "," + String.format(Locale.ROOT, "%.2f", r.price()) + "," + r.attrs() + ","
                + (r.durabilityPct() < 0 ? "" : String.valueOf(r.durabilityPct())) + "," + r.timestamp() + ","
                + (r.page() > 0 ? String.valueOf(r.page()) : "") + "," + r.firstSeen();
    }

    /** Never throws; returns null for anything malformed. Columns 6-8 (timestamp, page, firstSeen) are optional. */
    static PriceRecord parse(String line) {
        try {
            if (line.isEmpty() || line.startsWith("#")) return null;
            String[] c = line.split(",", -1);
            if (c.length < 3) return null;
            String item = c[0].trim().toLowerCase(Locale.ROOT);
            int qty = Integer.parseInt(c[1].trim());
            double price = parsePrice(c[2]);
            String attrs = c.length > 3 ? c[3].trim().toLowerCase(Locale.ROOT) : "";
            int dur = (c.length > 4 && !c[4].isBlank()) ? Integer.parseInt(c[4].trim()) : -1;
            long ts = c.length > 5 ? parseTime(c[5].trim()) : 0;
            int page = (c.length > 6 && !c[6].isBlank()) ? Integer.parseInt(c[6].trim()) : -1;
            long first = (c.length > 7 && !c[7].isBlank()) ? Long.parseLong(c[7].trim()) : ts;
            if (item.isEmpty() || qty < 1 || qty > 100000 || !(price > 0) || Double.isInfinite(price)) return null;
            if (dur > 100) return null;
            return new PriceRecord(item, qty, price, attrs, dur, ts, page > 0 ? page : -1, first);
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
