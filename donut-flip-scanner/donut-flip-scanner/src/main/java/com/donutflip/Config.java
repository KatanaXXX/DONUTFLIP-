package com.donutflip;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class Config {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static class WatchItem {
        public String name = "";
        public double maxUnit = 0;
    }

    public double minProfit = 1000;
    public int minConfidence = 50;
    public int minSamples = 3;
    public int maxAgeDays = 14;
    public double undercutPercent = 2;
    public boolean feeVerified = false;
    public double feeRatePercent = 0;
    public String sort = "PROFIT"; // PROFIT | ROI | CONFIDENCE

    /** Passive reading of the AH screen YOU open. Off until the user confirms. */
    public boolean liveCapture = false;
    public String captureKeyword = "auction";
    public boolean chatAlerts = true;
    public List<WatchItem> watch = new ArrayList<>();

    public double effectiveFeeRate() { return feeVerified ? feeRatePercent : 0; }

    public static Path dir() { return FabricLoader.getInstance().getConfigDir().resolve("donutflipscanner"); }

    public static Config load() {
        try {
            Path p = dir().resolve("config.json");
            if (Files.exists(p)) {
                Config c = GSON.fromJson(Files.readString(p), Config.class);
                if (c != null) {
                    if (c.watch == null) c.watch = new ArrayList<>();
                    if (c.captureKeyword == null || c.captureKeyword.isBlank()) c.captureKeyword = "auction";
                    return c;
                }
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
