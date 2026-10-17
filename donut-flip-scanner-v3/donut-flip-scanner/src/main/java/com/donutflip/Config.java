package com.donutflip;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Settings. Existing fields keep their names and defaults; new fields only add behavior. */
public final class Config {
    public static class WatchItem {
        public String name = "";
        public double maxUnit = 0;     // target buy price per unit
        public double targetSell = 0;  // target resale price per unit (0 = use the market estimate)
        public double minProfit = 0;   // per-watch minimum estimated net profit (0 = not required)
        public double minRoi = 0;      // per-watch minimum ROI % (0 = not required)
        public int minConfidence = 0;  // per-watch minimum confidence (0 = not required)
    }

    // ---- existing settings (unchanged names/defaults) ----
    public double minProfit = 1000;
    public int minConfidence = 50;
    public int minSamples = 3;
    public int maxAgeDays = 14;
    public double undercutPercent = 2;
    public boolean feeVerified = false;
    public double feeRatePercent = 0;
    public String sort = "PROFIT";
    public boolean liveCapture = false;
    public String captureKeyword = "auction";
    public int captureIntervalTicks = 10;  // how often an open AH screen is read (was fixed at 10 before)
    public boolean chatAlerts = true;
    public List<WatchItem> watch = new ArrayList<>();

    // ---- analysis (new) ----
    public double minRoi = 0;
    public double maxVolatility = 0.5;   // MAD/median above this = unusually volatile
    public double outlierRatio = 0.4;    // listing below this fraction of the comparable median = suspected outlier
    public int recentSeenMinutes = 30;   // a listing last seen longer ago than this is "availability unknown"
    public int marketRangeDays = 0;      // Market tab date filter, 0 = all
    // score weights (relative; normalized when used)
    public double wProfit = 30, wRoi = 15, wConf = 25, wFresh = 10, wSample = 10, wVol = 10;

    // ---- alerts (new). -1 means "same as the Flips setting" ----
    public boolean toastAlerts = true;
    public boolean soundAlerts = false;
    public double alertMinProfit = -1;
    public double alertMinRoi = -1;
    public int alertMinConfidence = -1;
    public int alertMinSamples = -1;
    public int alertMaxAgeDays = -1;
    public boolean alertWatchedOnly = false;
    public String alertKeywords = "";
    public int alertCooldownSec = 60;
    public int alertMaxPerBatch = 3;

    public double effectiveFeeRate() { return feeVerified ? feeRatePercent : 0; }
    public double aMinProfit() { return alertMinProfit < 0 ? minProfit : alertMinProfit; }
    public double aMinRoi() { return alertMinRoi < 0 ? minRoi : alertMinRoi; }
    public int aMinConf() { return alertMinConfidence < 0 ? minConfidence : alertMinConfidence; }
    public int aMinSamples() { return alertMinSamples < 0 ? minSamples : alertMinSamples; }
    public int aMaxAge() { return alertMaxAgeDays < 0 ? maxAgeDays : alertMaxAgeDays; }

    /** Everything that changes analysis results; used as a cache key. */
    public String analysisKey() {
        return minProfit + "|" + minConfidence + "|" + minSamples + "|" + maxAgeDays + "|" + undercutPercent + "|" + effectiveFeeRate()
                + "|" + minRoi + "|" + maxVolatility + "|" + outlierRatio + "|" + recentSeenMinutes + "|" + marketRangeDays
                + "|" + wProfit + "," + wRoi + "," + wConf + "," + wFresh + "," + wSample + "," + wVol;
    }

    /** Clamps bad/old values so a hand-edited or outdated file can never break analysis. */
    public void sanitize() {
        if (watch == null) watch = new ArrayList<>();
        watch.removeIf(w -> w == null || w.name == null || w.name.isBlank());
        if (captureKeyword == null || captureKeyword.isBlank()) captureKeyword = "auction";
        if (alertKeywords == null) alertKeywords = "";
        if (sort == null || !java.util.Set.of("PROFIT", "ROI", "CONFIDENCE", "FRESH", "PAGE", "SCORE").contains(sort)) sort = "PROFIT";
        minProfit = Math.max(0, fin(minProfit, 1000));
        minRoi = Math.max(0, fin(minRoi, 0));
        minConfidence = clamp(minConfidence, 0, 100);
        minSamples = clamp(minSamples, 1, 50);
        maxAgeDays = clamp(maxAgeDays, 1, 365);
        undercutPercent = Math.max(0, Math.min(90, fin(undercutPercent, 2)));
        feeRatePercent = Math.max(0, Math.min(99, fin(feeRatePercent, 0)));
        maxVolatility = Math.max(0.05, Math.min(5, fin(maxVolatility, 0.5)));
        outlierRatio = Math.max(0, Math.min(0.95, fin(outlierRatio, 0.4)));
        recentSeenMinutes = clamp(recentSeenMinutes, 1, 1440);
        captureIntervalTicks = clamp(captureIntervalTicks, 2, 200);
        for (WatchItem w : watch) {
            w.maxUnit = Math.max(0, fin(w.maxUnit, 0)); w.targetSell = Math.max(0, fin(w.targetSell, 0));
            w.minProfit = Math.max(0, fin(w.minProfit, 0)); w.minRoi = Math.max(0, fin(w.minRoi, 0));
            w.minConfidence = clamp(w.minConfidence, 0, 100);
        }
        marketRangeDays = clamp(marketRangeDays, 0, 365);
        wProfit = Math.max(0, fin(wProfit, 30)); wRoi = Math.max(0, fin(wRoi, 15)); wConf = Math.max(0, fin(wConf, 25));
        wFresh = Math.max(0, fin(wFresh, 10)); wSample = Math.max(0, fin(wSample, 10)); wVol = Math.max(0, fin(wVol, 10));
        alertCooldownSec = clamp(alertCooldownSec, 0, 3600);
        alertMaxPerBatch = clamp(alertMaxPerBatch, 1, 20);
    }

    private static double fin(double v, double def) { return Double.isNaN(v) || Double.isInfinite(v) ? def : v; }
    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }

    // ---- persistence ----
    private static Gson gson() { return new GsonBuilder().setPrettyPrinting().create(); }

    public static Path dir() { return FabricLoader.getInstance().getConfigDir().resolve("donutflipscanner"); }

    public static Config load() {
        try {
            Path p = dir().resolve("config.json");
            if (Files.exists(p)) {
                Config c = gson().fromJson(Files.readString(p), Config.class);
                if (c != null) { c.sanitize(); return c; }
            }
        } catch (Exception e) {
            System.err.println("[DonutFlipScanner] Could not read config.json, using defaults: " + e);
        }
        return new Config();
    }

    public void save() {
        try {
            Files.createDirectories(dir());
            Files.writeString(dir().resolve("config.json"), gson().toJson(this));
        } catch (Exception e) {
            System.err.println("[DonutFlipScanner] Could not save config.json: " + e);
        }
    }
}
