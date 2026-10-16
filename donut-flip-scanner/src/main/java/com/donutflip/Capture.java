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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    static void tick(Minecraft mc) {
        Config cfg = DonutFlipScannerClient.CONFIG;
        scanning = false;
        if (!cfg.liveCapture) { detail = "Off"; return; }
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) {
            detail = "Waiting - open the Auction House yourself";
            return;
        }
        String title = cs.getTitle().getString();
        if (!title.toLowerCase(Locale.ROOT).contains(cfg.captureKeyword.toLowerCase(Locale.ROOT))) {
            detail = "Open screen '" + title + "' is not an AH match";
            return;
        }
        scanning = true;
        if (++tick % 10 != 0) return;

        AbstractContainerMenu menu = cs.getMenu();
        int n = Math.max(0, menu.slots.size() - 36); // last 36 slots = player inventory
        if (dumpRequested) { dump(menu, n, title); dumpRequested = false; }

        Store store = DonutFlipScannerClient.STORE;
        List<PriceRecord> fresh = new ArrayList<>();
        int seen = 0;
        for (int i = 0; i < n; i++) {
            ItemStack st = menu.slots.get(i).getItem();
            if (st.isEmpty()) continue;
            PriceRecord r = toRecord(st);
            if (r == null) continue;
            seen++;
            if (store.addCaptured(r)) fresh.add(r);
        }
        detail = "Reading '" + title + "': " + seen + " priced listing(s) on screen";
        if (fresh.isEmpty()) return;
        store.flushCaptured();
        if (cfg.chatAlerts && mc.player != null) alerts(mc, store, cfg, fresh);
    }

    private static void alerts(Minecraft mc, Store store, Config cfg, List<PriceRecord> fresh) {
        int sent = 0;
        Set<PriceRecord> freshSet = Collections.newSetFromMap(new IdentityHashMap<>());
        freshSet.addAll(fresh);
        for (Analysis a : Analyzer.analyze(store.records, cfg, System.currentTimeMillis())) {
            if (sent >= 3) break;
            if (!freshSet.contains(a.rec()) || a.recommendation() != Analyzer.Rec.BUY_CANDIDATE) continue;
            mc.player.displayClientMessage(Component.literal("[DFS] BUY CANDIDATE: " + a.rec().item() + " x" + a.rec().quantity()
                    + " listed " + FlipScreen.money(a.observedListingPrice()) + ", ~net +" + FlipScreen.money(a.estimatedNetProfit())
                    + " (conf " + a.confidence() + "%)"), false);
            sent++;
        }
        for (PriceRecord r : fresh) {
            if (sent >= 5) break;
            for (Config.WatchItem w : cfg.watch) {
                if (w.name.isBlank() || w.maxUnit <= 0) continue;
                if (r.item().toLowerCase(Locale.ROOT).contains(w.name.toLowerCase(Locale.ROOT)) && r.unitPrice() <= w.maxUnit) {
                    mc.player.displayClientMessage(Component.literal("[DFS] WATCH HIT: " + r.item() + " x" + r.quantity()
                            + " at " + FlipScreen.money(r.unitPrice()) + "/unit"), false);
                    sent++;
                    break;
                }
            }
        }
    }

    private static PriceRecord toRecord(ItemStack st) {
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
        return new PriceRecord(id, st.getCount(), price, String.join(";", at), dur, System.currentTimeMillis());
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

    private static void dump(AbstractContainerMenu menu, int n, String title) {
        try {
            StringBuilder sb = new StringBuilder("Screen title: ").append(title).append("\n\n");
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
        } catch (Exception ignored) { }
    }
}
