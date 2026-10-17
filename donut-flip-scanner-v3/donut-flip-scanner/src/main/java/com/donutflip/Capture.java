package com.donutflip;

import com.donutflip.Analyzer.Analysis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PASSIVE capture: reads the item stacks and lore of an auction screen that the PLAYER opened.
 * It never sends packets or commands, never clicks, never changes pages, never buys or sells.
 * The AH page is taken ONLY from the screen title ("Page 4" or "(4/10)"); if the title has no page, it stays unknown.
 */
final class Capture {
    private Capture() { }

    static String detail = "Off";
    static boolean scanning = false;
    static boolean dumpRequested = false;
    static int currentPage = -1;
    private static int tick = 0;

    private static final Pattern P_DOLLAR = Pattern.compile("\\$\\s*([\\d,]*\\.?\\d+)\\s*([kKmMbB])?");
    private static final Pattern P_PRICE = Pattern.compile("(?i)price\\D*([\\d,]*\\.?\\d+)\\s*([kKmMbB])?");

    private static ExecutorService worker;
    private static synchronized ExecutorService worker() {
        if (worker == null) worker = Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, "DFS-analysis"); t.setDaemon(true); return t; });
        return worker;
    }

    static void tick(Minecraft mc) {
        Config cfg = DonutFlipScannerClient.CONFIG;
        scanning = false;
        if (!cfg.liveCapture) { detail = "Off"; return; }
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) {
            detail = "Waiting - open the Auction House yourself";
            currentPage = -1;
            return;
        }
        String title = cs.getTitle().getString();
        if (!title.toLowerCase(Locale.ROOT).contains(cfg.captureKeyword.toLowerCase(Locale.ROOT))) {
            detail = "Open screen '" + title + "' is not an AH match";
            currentPage = -1;
            return;
        }
        scanning = true;
        if (++tick % cfg.captureIntervalTicks != 0) return;

        int page = PageParser.parse(title);
        currentPage = page;
        AbstractContainerMenu menu = cs.getMenu();
        int n = Math.max(0, menu.slots.size() - 36); // last 36 slots = player inventory
        if (dumpRequested) { dump(menu, n, title, page); dumpRequested = false; }

        Store store = DonutFlipScannerClient.STORE;
        long now = System.currentTimeMillis();
        List<PriceRecord> fresh = new ArrayList<>();
        boolean changed = false;
        int seen = 0;
        for (int i = 0; i < n; i++) {
            ItemStack st = menu.slots.get(i).getItem();
            if (st.isEmpty()) continue;
            PriceRecord r = toRecord(st, page, now);
            if (r == null) continue;
            seen++;
            Store.Result res = store.addCaptured(r);
            if (res == Store.Result.NEW) { fresh.add(r); changed = true; }
            else if (res == Store.Result.UPDATED) changed = true;
        }
        detail = "Reading '" + title + "' (" + (page > 0 ? "page " + page : "page unknown") + "): " + seen + " priced listing(s)";
        if (!changed) return;
        store.flushCaptured();
        if (fresh.isEmpty()) return;

        // heavy analysis + alert decisions: compute off-thread from copies, decide back on the client thread
        final int version = store.version;
        final List<PriceRecord> records = store.records;
        final List<PriceRecord> captured = new ArrayList<>(store.captured);
        final int imported = store.imported.size();
        worker().execute(() -> {
            try {
                Market.Snapshot snap = Market.get(version, records, captured, imported, cfg, now);
                mc.execute(() -> decideAlerts(mc, snap, fresh, cfg));
            } catch (Exception e) {
                System.err.println("[DonutFlipScanner] analysis failed: " + e);
            }
        });
    }

    private static void decideAlerts(Minecraft mc, Market.Snapshot snap, List<PriceRecord> fresh, Config cfg) {
        long now = System.currentTimeMillis();
        int sent = 0;
        for (PriceRecord r : fresh) {
            if (sent >= cfg.alertMaxPerBatch) break;
            Analysis a = snap.byRecord().get(r);
            if (a == null) continue;
            Config.WatchItem w = AlertEngine.findWatch(r, cfg);
            boolean fire = w != null ? Alerts.ENGINE.considerWatch(a, cfg, w, now) || Alerts.ENGINE.consider(a, cfg, now, true)
                    : Alerts.ENGINE.consider(a, cfg, now, false);
            if (fire) {
                List<AlertEngine.AlertEntry> h = Alerts.ENGINE.history();
                Alerts.present(mc, h.get(0), cfg);
                sent++;
            }
        }
    }

    private static PriceRecord toRecord(ItemStack st, int page, long now) {
        double price = findPrice(st);
        if (price <= 0) return null;
        String id = BuiltInRegistries.ITEM.getKey(st.getItem()).getPath();
        List<String> at = new ArrayList<>();
        addEnchants(at, st.get(DataComponents.ENCHANTMENTS));
        addEnchants(at, st.get(DataComponents.STORED_ENCHANTMENTS));
        Collections.sort(at);
        int dur = -1;
        if (st.isDamageableItem() && st.getMaxDamage() > 0) {
            dur = 100 - (int) Math.round(st.getDamageValue() * 100.0 / st.getMaxDamage());
        }
        return new PriceRecord(id, st.getCount(), price, String.join(";", at), dur, now, page, now);
    }

    private static void addEnchants(List<String> out, ItemEnchantments e) {
        if (e == null) return;
        for (var en : e.entrySet()) {
            out.add(en.getKey().getRegisteredName().replace("minecraft:", "") + en.getIntValue());
        }
    }

    static double findPrice(ItemStack st) {
        ItemLore lore = st.get(DataComponents.LORE);
        if (lore == null) return -1;
        double fallback = -1;
        for (Component line : lore.lines()) {
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

    /** Opt-in (button in Settings). Writes only screen title, item names and lore to debug.txt - never to chat. */
    private static void dump(AbstractContainerMenu menu, int n, String title, int page) {
        try {
            StringBuilder sb = new StringBuilder("Screen title: ").append(title).append("\nParsed page: ").append(page).append("\n\n");
            for (int i = 0; i < n; i++) {
                ItemStack st = menu.slots.get(i).getItem();
                if (st.isEmpty()) continue;
                sb.append("slot ").append(i).append(": ").append(st.getHoverName().getString()).append(" x").append(st.getCount()).append('\n');
                ItemLore lore = st.get(DataComponents.LORE);
                if (lore != null) for (Component c : lore.lines()) sb.append("    | ").append(c.getString()).append('\n');
                sb.append("    parsed price: ").append(findPrice(st)).append("\n");
            }
            Files.createDirectories(Config.dir());
            Files.writeString(Config.dir().resolve("debug.txt"), sb.toString());
            detail = "Wrote debug.txt to the config folder";
        } catch (Exception e) {
            System.err.println("[DonutFlipScanner] dump failed: " + e);
        }
    }
}
