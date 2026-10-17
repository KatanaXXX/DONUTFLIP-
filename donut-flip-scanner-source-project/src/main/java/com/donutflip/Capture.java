package com.donutflip;

import com.donutflip.Analyzer.Analysis;
import java.nio.file.Files;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.class_1703;
import net.minecraft.class_1799;
import net.minecraft.class_2561;
import net.minecraft.class_310;
import net.minecraft.class_465;
import net.minecraft.class_7923;
import net.minecraft.class_9290;
import net.minecraft.class_9304;
import net.minecraft.class_9334;

/**
 * PASSIVE capture: reads the item stacks and lore of an auction screen that the PLAYER opened.
 * It never sends packets or commands, never clicks, never changes pages, never buys or sells.
 */
final class Capture {
    private Capture() { }

    static String detail = "Off";
    static boolean scanning = false;
    static boolean dumpRequested = false;
    private static int tick = 0;

    private static final Pattern P_DOLLAR = Pattern.compile("\\$\\s*([\\d,]*\\.?\\d+)\\s*([kKmMbB])?");
    private static final Pattern P_PRICE = Pattern.compile("(?i)price\\D*([\\d,]*\\.?\\d+)\\s*([kKmMbB])?");

    static void tick(class_310 mc) {
        Config cfg = DonutFlipScannerClient.CONFIG;
        scanning = false;
        if (!cfg.liveCapture) { detail = "Off"; return; }
        if (!(mc.field_1755 instanceof class_465<?> cs)) {
            detail = "Waiting - open the Auction House yourself";
            return;
        }
        String title = cs.method_25440().getString();
        if (!title.toLowerCase(Locale.ROOT).contains(cfg.captureKeyword.toLowerCase(Locale.ROOT))) {
            detail = "Open screen '" + title + "' is not an AH match";
            return;
        }
        scanning = true;
        if (++tick % 10 != 0) return;

        class_1703 menu = cs.method_17577();
        int n = Math.max(0, menu.field_7761.size() - 36); // last 36 slots = player inventory
        if (dumpRequested) { dump(menu, n, title); dumpRequested = false; }

        Store store = DonutFlipScannerClient.STORE;
        List<PriceRecord> fresh = new ArrayList<>();
        int seen = 0;
        for (int i = 0; i < n; i++) {
            class_1799 st = menu.field_7761.get(i).method_7677();
            if (st.method_7960()) continue;
            PriceRecord r = toRecord(st);
            if (r == null) continue;
            seen++;
            if (store.addCaptured(r)) fresh.add(r);
        }
        detail = "Reading '" + title + "': " + seen + " priced listing(s) on screen";
        if (fresh.isEmpty()) return;
        store.flushCaptured();
        if (cfg.chatAlerts && mc.field_1724 != null) alerts(mc, store, cfg, fresh);
    }

    private static void alerts(class_310 mc, Store store, Config cfg, List<PriceRecord> fresh) {
        int sent = 0;
        Set<PriceRecord> freshSet = Collections.newSetFromMap(new IdentityHashMap<>());
        freshSet.addAll(fresh);
        for (Analysis a : Analyzer.analyze(store.records, cfg, System.currentTimeMillis())) {
            if (sent >= 3) break;
            if (!freshSet.contains(a.rec()) || a.recommendation() != Analyzer.Rec.BUY_CANDIDATE) continue;
            double maxBuy = Math.max(0, a.suggestedResalePrice() * (1 - cfg.effectiveFeeRate() / 100.0) - cfg.minProfit);
            String page = a.rec().page() > 0 ? "page " + a.rec().page() : "page unknown";
            mc.field_1724.method_7353(class_2561.method_43470("[DFS] NEW FLIP: " + a.rec().item() + " x" + a.rec().quantity()
                    + " | " + page + " | listed " + FlipScreen.money(a.observedListingPrice()) + " | max buy " + FlipScreen.money(maxBuy)
                    + " | sell ~" + FlipScreen.money(a.suggestedResalePrice()) + " | net ~+" + FlipScreen.money(a.estimatedNetProfit())
                    + " | ROI " + String.format(Locale.ROOT, "%.0f%%", a.roiPercent()) + " | conf " + a.confidence() + "%"), false);
            sent++;
        }
        for (PriceRecord r : fresh) {
            if (sent >= 5) break;
            for (Config.WatchItem w : cfg.watch) {
                if (w.name.isBlank() || w.maxUnit <= 0) continue;
                if (r.item().toLowerCase(Locale.ROOT).contains(w.name.toLowerCase(Locale.ROOT)) && r.unitPrice() <= w.maxUnit) {
                    mc.field_1724.method_7353(class_2561.method_43470("[DFS] WATCH HIT: " + r.item() + " x" + r.quantity()
                            + " at " + FlipScreen.money(r.unitPrice()) + "/unit"), false);
                    sent++;
                    break;
                }
            }
        }
    }

    private static PriceRecord toRecord(class_1799 st) {
        double price = findPrice(st);
        if (price <= 0) return null;
        String id = class_7923.field_41178.method_10221(st.method_7909()).method_12832();
        List<String> at = new ArrayList<>();
        addEnchants(at, st.method_58694(class_9334.field_49633));
        addEnchants(at, st.method_58694(class_9334.field_49643));
        Collections.sort(at);
        int dur = -1;
        if (st.method_7963() && st.method_7936() > 0) {
            dur = 100 - (int) Math.round(st.method_7919() * 100.0 / st.method_7936());
        }
        return new PriceRecord(id, st.method_7947(), price, String.join(";", at), dur, System.currentTimeMillis());
    }

    private static void addEnchants(List<String> out, class_9304 e) {
        if (e == null) return;
        for (var en : e.method_57539()) {
            out.add(en.getKey().method_55840().replace("minecraft:", "") + en.getIntValue());
        }
    }

    static double findPrice(class_1799 st) {
        class_9290 lore = st.method_58694(class_9334.field_49632);
        if (lore == null) return -1;
        double fallback = -1;
        for (class_2561 line : lore.comp_2400()) {
            String s = line.getString();
            boolean priceLine = s.toLowerCase(Locale.ROOT).contains("price");
            Matcher m = P_DOLLAR.matcher(s);
            if (m.find()) {
                double v = val(m);
                if (priceLine) return v;
                if (fallback < 0) fallback = v;
                continue;
            }
            m = P_PRICE.matcher(s);
            if (m.find()) return val(m);
        }
        return fallback;
    }

    private static double val(Matcher m) {
        try {
            return Store.parsePrice(m.group(1).replace(",", "") + (m.group(2) == null ? "" : m.group(2)));
        } catch (Exception e) {
            return -1;
        }
    }

    private static void dump(class_1703 menu, int n, String title) {
        try {
            StringBuilder sb = new StringBuilder("Screen title: ").append(title).append("\n\n");
            for (int i = 0; i < n; i++) {
                class_1799 st = menu.field_7761.get(i).method_7677();
                if (st.method_7960()) continue;
                sb.append("slot ").append(i).append(": ").append(st.method_7964().getString()).append(" x").append(st.method_7947()).append('\n');
                class_9290 lore = st.method_58694(class_9334.field_49632);
                if (lore != null) for (class_2561 c : lore.comp_2400()) sb.append("    | ").append(c.getString()).append('\n');
                sb.append("    parsed price: ").append(findPrice(st)).append("\n");
            }
            Files.createDirectories(Config.dir());
            Files.writeString(Config.dir().resolve("debug.txt"), sb.toString());
            detail = "Wrote debug.txt to the config folder";
        } catch (Exception ignored) { }
    }
}
