package dev.riptide.knifelab.core;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;

/** Persisted Knife Lab settings. No Minecraft dependencies, so it is unit-testable. */
public final class KnifeSettings {
    public static final List<String> SWORD_IDS = List.of(
            "minecraft:wooden_sword",
            "minecraft:stone_sword",
            "minecraft:iron_sword",
            "minecraft:golden_sword",
            "minecraft:diamond_sword",
            "minecraft:netherite_sword");

    private KnifeType selectedKnife = KnifeType.BUTTERFLY;
    private boolean replacementEnabled = true;
    private boolean inspectAnimationEnabled = true;
    private final Set<String> replaceableSwords = new LinkedHashSet<>(SWORD_IDS);

    public KnifeType getSelectedKnife() {
        return selectedKnife;
    }

    public void setSelectedKnife(KnifeType knife) {
        this.selectedKnife = Objects.requireNonNull(knife);
    }

    public boolean isReplacementEnabled() {
        return replacementEnabled;
    }

    public void setReplacementEnabled(boolean enabled) {
        this.replacementEnabled = enabled;
    }

    public boolean isInspectAnimationEnabled() {
        return inspectAnimationEnabled;
    }

    public void setInspectAnimationEnabled(boolean enabled) {
        this.inspectAnimationEnabled = enabled;
    }

    public Set<String> getReplaceableSwords() {
        return Collections.unmodifiableSet(replaceableSwords);
    }

    public void setSwordEnabled(String itemId, boolean enabled) {
        if (!SWORD_IDS.contains(itemId)) {
            throw new IllegalArgumentException("Not a replaceable sword: " + itemId);
        }
        if (enabled) {
            replaceableSwords.add(itemId);
        } else {
            replaceableSwords.remove(itemId);
        }
    }

    /** True when the held item with this registry id should be drawn as the selected knife. */
    public boolean replacesItem(String itemId) {
        return replacementEnabled && replaceableSwords.contains(itemId);
    }

    public void save(Path file) throws IOException {
        Properties p = new Properties();
        p.setProperty("selectedKnife", selectedKnife.id());
        p.setProperty("replacementEnabled", Boolean.toString(replacementEnabled));
        p.setProperty("inspectAnimationEnabled", Boolean.toString(inspectAnimationEnabled));
        p.setProperty("replaceableSwords", String.join(",", replaceableSwords));
        if (file.getParent() != null) {
            Files.createDirectories(file.getParent());
        }
        try (Writer w = Files.newBufferedWriter(file)) {
            p.store(w, "RIPTIDE: KNIFE LAB settings");
        }
    }

    /** Missing or malformed values fall back to defaults. */
    public static KnifeSettings load(Path file) throws IOException {
        KnifeSettings s = new KnifeSettings();
        if (!Files.exists(file)) {
            return s;
        }
        Properties p = new Properties();
        try (Reader r = Files.newBufferedReader(file)) {
            p.load(r);
        }
        KnifeType.byId(p.getProperty("selectedKnife", "")).ifPresent(s::setSelectedKnife);
        s.replacementEnabled = Boolean.parseBoolean(p.getProperty("replacementEnabled", "true"));
        s.inspectAnimationEnabled = Boolean.parseBoolean(p.getProperty("inspectAnimationEnabled", "true"));
        String swords = p.getProperty("replaceableSwords");
        if (swords != null) {
            s.replaceableSwords.clear();
            for (String raw : swords.split(",")) {
                String id = raw.trim();
                if (SWORD_IDS.contains(id)) {
                    s.replaceableSwords.add(id);
                }
            }
        }
        return s;
    }
}
