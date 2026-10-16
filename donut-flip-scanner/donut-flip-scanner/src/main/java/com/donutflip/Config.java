package com.donutflip;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

public final class Config {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public double minProfit = 1000;
    public int minConfidence = 50;
    public int minSamples = 3;
    public int maxAgeDays = 14;
    public double undercutPercent = 2;
    /** Fees are only applied when the user confirms they have verified the real fee. */
    public boolean feeVerified = false;
    public double feeRatePercent = 0;
    public String sort = "PROFIT"; // PROFIT | ROI | CONFIDENCE

    public double effectiveFeeRate() { return feeVerified ? feeRatePercent : 0; }

    public static Path dir() { return FabricLoader.getInstance().getConfigDir().resolve("donutflipscanner"); }

    public static Config load() {
        try {
            Path p = dir().resolve("config.json");
            if (Files.exists(p)) {
                Config c = GSON.fromJson(Files.readString(p), Config.class);
                if (c != null) return c;
            }
        } catch (Exception ignored) { }
        return new Config();
    }

    public void save() {
        try {
            Files.createDirectories(dir());
            Files.writeString(dir().resolve("config.json"), GSON.toJson(this));
        } catch (Exception ignored) { }
    }
}
