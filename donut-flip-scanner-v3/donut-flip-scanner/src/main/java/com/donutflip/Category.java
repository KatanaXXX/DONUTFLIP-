package com.donutflip;

import java.util.Locale;

/** Rough item grouping derived from the item id. Only a convenience filter, never used for pricing. */
public final class Category {
    private Category() { }

    public static final String[] ALL = {"All", "Weapons/Tools", "Armor", "Books", "Other"};

    public static String of(String itemId) {
        String s = itemId.toLowerCase(Locale.ROOT);
        if (s.equals("enchanted_book")) return "Books";
        for (String k : new String[]{"helmet", "chestplate", "leggings", "boots", "elytra", "shield"}) if (s.contains(k)) return "Armor";
        for (String k : new String[]{"sword", "pickaxe", "_axe", "shovel", "hoe", "bow", "trident", "mace", "fishing_rod"}) {
            if (s.contains(k) || s.equals("axe")) return "Weapons/Tools";
        }
        return "Other";
    }
}
