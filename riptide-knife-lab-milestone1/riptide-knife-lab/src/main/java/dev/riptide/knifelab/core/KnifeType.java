package dev.riptide.knifelab.core;

import java.util.Arrays;
import java.util.Optional;

/** Knives in the Knife Lab. Milestone 1 ships three; the rest arrive in later milestones. */
public enum KnifeType {
    BUTTERFLY("butterfly", "Butterfly Knife"),
    KARAMBIT("karambit", "Karambit"),
    M9_BAYONET("m9_bayonet", "M9 Bayonet");

    public static final String MOD_ID = "riptide_knife_lab";

    private final String id;
    private final String displayName;

    KnifeType(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    /** Value written into the item's custom_model_data strings; matches the item model definitions. */
    public String modelKey() {
        return MOD_ID + ":" + id;
    }

    public static Optional<KnifeType> byId(String id) {
        return Arrays.stream(values()).filter(k -> k.id.equals(id)).findFirst();
    }
}
