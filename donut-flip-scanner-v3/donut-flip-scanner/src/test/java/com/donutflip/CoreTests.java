package com.donutflip;

import com.donutflip.Analyzer.Analysis;
import com.donutflip.Analyzer.Level;
import com.donutflip.Analyzer.Rec;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CoreTests {
    static final long NOW = 1_700_000_000_000L;
    static final long DAY = 86_400_000L;
    static final long HOUR = 3_600_000L;

    static PriceRecord rec(String item, int qty, double price, String attrs, int dur, long ts, int page) {
        return new PriceRecord(item, qty, price, attrs, dur, ts, page, ts);
    }
    static PriceRecord rec(String item, double price, long ts) { return rec(item, 1, price, "", -1, ts, -1); }

    /** n comparable records at the given price, all one hour old. */
    static List<PriceRecord> comps(String item, int n, double price) {
        List<PriceRecord> l = new ArrayList<>();
        for (int i = 0; i < n; i++) l.add(rec(item, price, NOW - HOUR));
        return l;
    }

    /** Analysis of one fresh listing against 5 comps priced 10,000. */
    static Analysis analysisFor(double listing, Config cfg, int page) {
        List<PriceRecord> all = comps("diamond_block", 5, 10_000);
        PriceRecord l = rec("diamond_block", 1, listing, "", -1, NOW - 60_000, page);
        all.add(l);
        return Analyzer.analyze(all, cfg, NOW).get(5);
    }
    static Analysis analysisFor(double listing) { return analysisFor(listing, new Config(), 4); }

    // ---------- money math ----------
    @Test void netProfitAndRoi() {
        assertEquals(4000.0, Calc.netProfit(12000, 8000, 0), 1e-9);
        assertEquals(3400.0, Calc.netProfit(12000, 8000, 5), 1e-9);
        assertEquals(42.5, Calc.roiPercent(3400, 8000), 1e-9);
        assertTrue(Double.isNaN(Calc.roiPercent(100, 0)));
    }

    @Test void breakEvenAndMaxBuy() {
        assertEquals(8421.0526, Calc.breakEvenSell(8000, 5), 1e-3);
        assertEquals(10000.0, Calc.maxBuyForRoi(12000, 0, 20), 1e-9);
        assertEquals(9500.0, Calc.maxBuyForRoi(12000, 5, 20), 1e-9);
        assertEquals(Double.POSITIVE_INFINITY, Calc.breakEvenSell(100, 100), 0);
    }

    @Test void feesOnlyApplyWhenVerified() {
        Config c = new Config();
        c.feeRatePercent = 5;
        assertEquals(0.0, c.effectiveFeeRate(), 0);
        c.feeVerified = true;
        assertEquals(5.0, c.effectiveFeeRate(), 0);
    }

    // ---------- recommendations ----------
    @Test void buyCandidateFromRealComparables() {
        Analysis a = analysisFor(6000);
        assertEquals(Rec.BUY_CANDIDATE, a.recommendation());
        assertEquals(5, a.sampleSize());
        assertEquals(9800.0, a.suggestedResalePrice(), 1e-9);
        assertEquals(3800.0, a.estimatedNetProfit(), 1e-9);
        assertEquals(8800.0, a.recommendedMaxBuy(), 1e-9);
        assertEquals(Level.HIGH, a.level());
        assertEquals(4, a.rec().page());
        assertTrue(a.recentlySeen());
        assertFalse(a.why().isEmpty());
    }

    @Test void maxBuyIsExactlyTheBuyBoundary() {
        Config c = new Config();
        assertEquals(Rec.BUY_CANDIDATE, analysisFor(8800, c, 1).recommendation());
        assertEquals(Rec.WATCH, analysisFor(8801, c, 1).recommendation());
    }

    @Test void verifiedFeesChangeNetAndMaxBuy() {
        Config c = new Config();
        c.feeVerified = true;
        c.feeRatePercent = 5;
        Analysis a = analysisFor(6000, c, 1);
        assertEquals(490.0, a.estimatedFees(), 1e-9);
        assertEquals(3310.0, a.estimatedNetProfit(), 1e-9);
        assertEquals(8310.0, a.recommendedMaxBuy(), 1e-9);
        assertEquals(Rec.BUY_CANDIDATE, analysisFor(8310, c, 1).recommendation());
    }

    @Test void negativeMarginIsBadMarginNotHighRisk() {
        Analysis a = analysisFor(12_000);
        assertEquals(Rec.BAD_MARGIN, a.recommendation());
        assertTrue(a.estimatedNetProfit() < 0);
        assertTrue(a.recommendation().isAvoid());
    }

    @Test void insufficientDataShowsNoPrices() {
        List<PriceRecord> all = comps("diamond_block", 2, 10_000);
        all.add(rec("diamond_block", 6000, NOW - 60_000));
        Analysis a = Analyzer.analyze(all, new Config(), NOW).get(2);
        assertEquals(Rec.INSUFFICIENT, a.recommendation());
        assertEquals(Level.INSUFFICIENT, a.level());
        assertNull(a.suggestedResalePrice());
        assertNull(a.estimatedNetProfit());
        assertNull(a.recommendedMaxBuy());
    }

    @Test void respectsMinimumComparablesSetting() {
        Config c = new Config();
        c.minSamples = 6;
        assertEquals(Rec.INSUFFICIENT, analysisFor(6000, c, 1).recommendation());
    }

    // ---------- comparable matching ----------
    @Test void differentEnchantsAreNotCombined() {
        List<PriceRecord> all = new ArrayList<>();
        for (int i = 0; i < 5; i++) all.add(rec("diamond_sword", 1, 10_000, "sharpness1", -1, NOW - HOUR, -1));
        all.add(rec("diamond_sword", 1, 3_000, "sharpness5", -1, NOW - 60_000, -1));
        assertEquals(Rec.INSUFFICIENT, Analyzer.analyze(all, new Config(), NOW).get(5).recommendation());
    }

    @Test void differentStackBandsAndDurabilityAreNotCombined() {
        List<PriceRecord> a = new ArrayList<>();
        for (int i = 0; i < 5; i++) a.add(rec("diamond", 1, 100, "", -1, NOW - HOUR, -1));
        a.add(rec("diamond", 64, 3000, "", -1, NOW - 60_000, -1));
        assertEquals(Rec.INSUFFICIENT, Analyzer.analyze(a, new Config(), NOW).get(5).recommendation());

        List<PriceRecord> d = new ArrayList<>();
        for (int i = 0; i < 5; i++) d.add(rec("pickaxe", 1, 5000, "", 100, NOW - HOUR, -1));
        d.add(rec("pickaxe", 1, 1000, "", 20, NOW - 60_000, -1));
        assertEquals(Rec.INSUFFICIENT, Analyzer.analyze(d, new Config(), NOW).get(5).recommendation());

        List<PriceRecord> s = new ArrayList<>();
        for (int i = 0; i < 5; i++) s.add(rec("pickaxe", 1, 5000, "", 95, NOW - HOUR, -1));
        s.add(rec("pickaxe", 1, 3000, "", 91, NOW - 60_000, -1));
        assertTrue(Analyzer.analyze(s, new Config(), NOW).get(5).sampleSize() == 5);
    }

    @Test void unitPricesNormalizeStacks() {
        List<PriceRecord> all = new ArrayList<>();
        for (int i = 0; i < 5; i++) all.add(rec("diamond", 64, 6400, "", -1, NOW - HOUR, -1)); // 100/unit
        all.add(rec("diamond", 64, 3200, "", -1, NOW - 60_000, -1));                            // 50/unit
        Analysis a = Analyzer.analyze(all, new Config(), NOW).get(5);
        assertEquals(6400.0, a.estimatedMarketPrice(), 1e-6);
    }

    // ---------- stale data, outliers, recency ----------
    @Test void staleComparablesAreIgnored() {
        List<PriceRecord> all = new ArrayList<>();
        for (int i = 0; i < 5; i++) all.add(rec("diamond_block", 10_000, NOW - 30 * DAY));
        all.add(rec("diamond_block", 6000, NOW - 60_000));
        assertEquals(Rec.INSUFFICIENT, Analyzer.analyze(all, new Config(), NOW).get(5).recommendation());
    }

    @Test void staleOrUndatedListingIsHighRisk() {
        List<PriceRecord> all = comps("diamond_block", 5, 10_000);
        all.add(rec("diamond_block", 6000, 0));
        Analysis a = Analyzer.analyze(all, new Config(), NOW).get(5);
        assertEquals(Rec.HIGH_RISK, a.recommendation());
        assertTrue(a.primaryReason().contains("stale"));
    }

    @Test void outliersAreIgnoredAndReported() {
        List<PriceRecord> all = new ArrayList<>();
        for (double p : new double[]{9000, 9500, 10000, 10500, 11000, 100}) all.add(rec("diamond_block", p, NOW - HOUR));
        all.add(rec("diamond_block", 7000, NOW - 60_000));
        Analysis a = Analyzer.analyze(all, new Config(), NOW).get(6);
        assertEquals(5, a.sampleSize());
        assertEquals(10_000.0, a.estimatedMarketPrice(), 1e-6);
        assertTrue(a.evidence().contains("1 outlier(s) ignored"));
    }

    @Test void medianResistsASingleHugeOutlier() {
        List<PriceRecord> all = comps("diamond_block", 5, 10_000);
        all.add(rec("diamond_block", 9_999_999, NOW - HOUR));
        all.add(rec("diamond_block", 6000, NOW - 60_000));
        assertEquals(10_000.0, Analyzer.analyze(all, new Config(), NOW).get(6).estimatedMarketPrice(), 1e-6);
    }

    @Test void recentRecordsOutweighOldOnes() {
        List<PriceRecord> all = new ArrayList<>();
        for (int i = 0; i < 3; i++) all.add(rec("diamond_block", 5000, NOW - 12 * DAY));
        for (int i = 0; i < 3; i++) all.add(rec("diamond_block", 10_000, NOW - DAY / 10));
        all.add(rec("diamond_block", 8000, NOW - 60_000));
        Analysis a = Analyzer.analyze(all, new Config(), NOW).get(6);
        assertTrue(a.estimatedMarketPrice() > 9000, "recent price should dominate, plain median would be 7500");
    }

    @Test void weightedMedianBasics() {
        assertEquals(2.5, Analyzer.weightedMedian(new double[]{1, 2, 3, 4}, new double[]{1, 1, 1, 1}), 1e-9);
        assertEquals(30.0, Analyzer.weightedMedian(new double[]{10, 20, 30}, new double[]{0.1, 0.1, 10}), 1e-9);
    }

    @Test void volatileMarketIsHighRisk() {
        List<PriceRecord> all = new ArrayList<>();
        for (double p : new double[]{2000, 5000, 9000, 14000, 20000}) all.add(rec("diamond_block", p, NOW - HOUR));
        all.add(rec("diamond_block", 3000, NOW - 60_000));
        Analysis a = Analyzer.analyze(all, new Config(), NOW).get(5);
        assertTrue(a.recommendation() == Rec.HIGH_RISK || a.recommendation() == Rec.WATCH || a.recommendation() == Rec.BAD_MARGIN);
        if (a.volatility() > new Config().maxVolatility) assertEquals(Rec.HIGH_RISK, a.recommendation());
    }

    @Test void suspiciouslyCheapListingIsFlaggedNotCelebrated() {
        Analysis a = analysisFor(500);
        assertEquals(Rec.HIGH_RISK, a.recommendation());
        assertTrue(a.outlierListing());
    }

    // ---------- confidence and score ----------
    @Test void confidenceGrowsWithSamplesAndFreshness() {
        List<Double> u3 = List.of(10.0, 10.0, 10.0), a1 = List.of(1.0, 1.0, 1.0);
        List<Double> u8 = List.of(10.0, 10.0, 10.0, 10.0, 10.0, 10.0, 10.0, 10.0);
        List<Double> a8 = List.of(1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0);
        assertTrue(Analyzer.confidence(u8, a8, 14) > Analyzer.confidence(u3, a1, 14));
        List<Double> old = List.of(12.0, 12.0, 12.0);
        assertTrue(Analyzer.confidence(u3, a1, 14) > Analyzer.confidence(u3, old, 14));
        List<Double> noisy = List.of(5.0, 10.0, 20.0);
        assertTrue(Analyzer.confidence(u3, a1, 14) > Analyzer.confidence(noisy, a1, 14));
    }

    @Test void highScoreCannotHideWeakEvidence() {
        Config c = new Config();
        int s = Analyzer.score(1_000_000, 500, 20, 0, 8, 0, c, 14);
        assertTrue(s <= 20, "score must be capped by confidence when below minimum, was " + s);
        assertTrue(Analyzer.score(100_000, 200, 95, 0, 8, 0, c, 14) > s);
    }

    // ---------- dedupe and pages ----------
    @Test void exactDuplicateObservationsAreDropped() {
        PriceRecord a = rec("x", 100, NOW), b = rec("x", 100, NOW), later = rec("x", 100, NOW + 1000);
        assertEquals(1, Store.dedupe(List.of(a, b)).size());
        assertEquals(2, Store.dedupe(List.of(a, later)).size());
    }

    @Test void recapturedListingsUpdateInsteadOfDuplicating() {
        Store s = new Store();
        PriceRecord first = rec("x", 1, 100, "", -1, NOW, 2);
        assertEquals(Store.Result.NEW, s.addCaptured(first));
        assertEquals(Store.Result.SAME, s.addCaptured(rec("x", 1, 100, "", -1, NOW + 10_000, 2)));
        assertEquals(Store.Result.UPDATED, s.addCaptured(rec("x", 1, 100, "", -1, NOW + 70_000, 2)));
        assertEquals(1, s.captured.size());
        assertEquals(NOW, s.captured.get(0).firstSeen());
        assertEquals(NOW + 70_000, s.captured.get(0).timestamp());
        assertEquals(Store.Result.UPDATED, s.addCaptured(rec("x", 1, 100, "", -1, NOW + 71_000, 5)));
        assertEquals(5, s.captured.get(0).page());
    }

    @Test void priceChangeIsADifferentObservation() {
        Store s = new Store();
        assertEquals(Store.Result.NEW, s.addCaptured(rec("x", 1, 100, "", -1, NOW, 1)));
        assertEquals(Store.Result.NEW, s.addCaptured(rec("x", 1, 80, "", -1, NOW + 1, 1)));
        assertEquals(2, s.captured.size());
    }

    @Test void pageIsNeverInvented() {
        PriceRecord withPage = Store.parse("diamond_block,64,8k,,,2026-10-14,4");
        PriceRecord noPage = Store.parse("diamond_block,64,8k,,,2026-10-14");
        assertEquals(4, withPage.page());
        assertEquals(-1, noPage.page());
        PriceRecord kept = withPage.seenAgain(NOW, -1);
        assertEquals(4, kept.page());
        assertEquals(withPage.signature(), noPage.signature());
    }

    @Test void malformedLinesNeverThrow() {
        for (String bad : new String[]{"", "#c", "a", "a,b,c", "x,0,100", "x,1,-5", "x,1,abc", "x,1,100,,150", ",,,", "x,1,NaN", "x,1,1e999"}) {
            assertNull(Store.parse(bad), bad);
        }
        assertEquals(1500.0, Store.parse("x,1,1.5k").price(), 1e-9);
        assertEquals(2_000_000.0, Store.parse("x,1,2m").price(), 1e-9);
    }

    // ---------- sorting ----------
    @Test void sortingByEachKey() {
        List<PriceRecord> all = new ArrayList<>(comps("a_item", 5, 10_000));
        all.addAll(comps("c_item", 5, 20_000));
        PriceRecord a = rec("a_item", 1, 6000, "", -1, NOW - 60_000, 3);        // net 3800, ROI 63%
        PriceRecord c = rec("c_item", 1, 15_000, "", -1, NOW - 120_000, -1);    // net 4600, ROI 30%
        PriceRecord d = rec("a_item", 1, 7000, "", -1, NOW - 10_000, 1);        // net 2800, page 1, freshest
        all.add(a); all.add(c); all.add(d);
        List<Analysis> picks = new ArrayList<>();
        for (Analysis x : Analyzer.analyze(all, new Config(), NOW)) if (x.rec() == a || x.rec() == c || x.rec() == d) picks.add(x);
        picks.sort(Analyzer.comparator("PROFIT"));
        assertTrue(picks.get(0).rec() == c);
        picks.sort(Analyzer.comparator("ROI"));
        assertTrue(picks.get(0).rec() == a);
        picks.sort(Analyzer.comparator("PAGE"));
        assertTrue(picks.get(0).rec() == d && picks.get(1).rec() == a && picks.get(2).rec() == c);
        picks.sort(Analyzer.comparator("FRESH"));
        assertTrue(picks.get(0).rec() == d);
    }

    // ---------- alerts ----------
    @Test void alertFiresOnceForAQualifyingListing() {
        AlertEngine e = new AlertEngine();
        Config c = new Config();
        Analysis a = analysisFor(6000);
        assertTrue(e.consider(a, c, NOW, false));
        assertFalse(e.consider(a, c, NOW + 1000, false), "unchanged listing must not repeat");
        assertFalse(e.consider(a, c, NOW + 10 * 60_000, false), "even after the cooldown");
        assertEquals(1, e.history().size());
        assertEquals(1, e.unread);
        assertEquals(4, e.history().get(0).page());
    }

    @Test void priceDropCanAlertAgainAfterCooldown() {
        AlertEngine e = new AlertEngine();
        Config c = new Config();
        assertTrue(e.consider(analysisFor(6000), c, NOW, false));
        assertFalse(e.consider(analysisFor(5000), c, NOW + 10_000, false), "cooldown not over");
        assertFalse(e.consider(analysisFor(5900), c, NOW + 120_000, false), "drop under 10% is not meaningful");
        assertTrue(e.consider(analysisFor(5000), c, NOW + 120_000, false));
        assertEquals(2, e.history().size());
    }

    @Test void alertThresholdsAreRespected() {
        Config c = new Config();
        Analysis a = analysisFor(6000);
        assertTrue(AlertEngine.qualifies(a, c, false));
        c.alertMinProfit = 5000;
        assertFalse(AlertEngine.qualifies(a, c, false));
        c.alertMinProfit = -1;
        c.alertMinConfidence = 95;
        assertFalse(AlertEngine.qualifies(a, c, false));
        c.alertMinConfidence = -1;
        c.alertMinSamples = 9;
        assertFalse(AlertEngine.qualifies(a, c, false));
        c.alertMinSamples = -1;
        c.alertMinRoi = 80;
        assertFalse(AlertEngine.qualifies(a, c, false));
    }

    @Test void staleOrIncompleteDataNeverAlerts() {
        Config c = new Config();
        List<PriceRecord> all = comps("diamond_block", 5, 10_000);
        all.add(rec("diamond_block", 1, 6000, "", -1, NOW - 31 * 60_000, 1)); // seen 31 min ago: availability unknown
        assertFalse(AlertEngine.qualifies(Analyzer.analyze(all, c, NOW).get(5), c, false));
        List<PriceRecord> few = comps("diamond_block", 1, 10_000);
        few.add(rec("diamond_block", 6000, NOW - 1000));
        assertFalse(AlertEngine.qualifies(Analyzer.analyze(few, c, NOW).get(1), c, false));
    }

    @Test void keywordAndWatchedOnlyFilters() {
        Config c = new Config();
        Analysis a = analysisFor(6000);
        c.alertKeywords = "elytra";
        assertFalse(AlertEngine.qualifies(a, c, false));
        assertTrue(AlertEngine.qualifies(a, c, true), "watched items bypass keywords");
        c.alertKeywords = "diamond, elytra";
        assertTrue(AlertEngine.qualifies(a, c, false));
        c.alertKeywords = "other";
        assertTrue(AlertEngine.qualifies(a, c, false), "category keyword");
        c.alertKeywords = "";
        c.alertWatchedOnly = true;
        assertFalse(AlertEngine.qualifies(a, c, false));
        assertTrue(AlertEngine.qualifies(a, c, true));
    }

    @Test void watchRulesAndSharedGate() {
        Config c = new Config();
        Analysis a = analysisFor(6000);
        Config.WatchItem w = new Config.WatchItem();
        w.name = "Diamond";
        w.maxUnit = 7000;
        assertTrue(AlertEngine.watchMatches(a.rec(), w));
        assertTrue(AlertEngine.watchQualifies(a, w));
        w.maxUnit = 5000;
        assertFalse(AlertEngine.watchQualifies(a, w));
        w.maxUnit = 7000;
        w.minProfit = 10_000;
        assertFalse(AlertEngine.watchQualifies(a, w));
        w.minProfit = 0;
        AlertEngine e = new AlertEngine();
        assertTrue(e.considerWatch(a, c, w, NOW));
        assertFalse(e.considerWatch(a, c, w, NOW + 1000));
        assertFalse(e.consider(a, c, NOW + 2000, false), "general alerts share the same duplicate gate");
        e.clearHistory();
        assertEquals(0, e.history().size());
    }

    // ---------- session accounting ----------
    @Test void onlyRecordedSalesCountAsRealized() {
        Ledger l = new Ledger(NOW - HOUR);
        assertEquals(0.0, l.realized(), 0);
        Ledger.Entry e = l.recordSale("block", 8000, 12000, 5, NOW);
        assertEquals(600.0, e.fee(), 1e-9);
        assertEquals(3400.0, e.net(), 1e-9);
        l.recordSale("loss", 5000, 4000, 0, NOW);
        assertEquals(2400.0, l.realized(), 1e-9);
        assertEquals(13000.0, l.totalCost(), 1e-9);
        assertEquals(600.0, l.totalFees(), 1e-9);
        assertEquals(2400.0, l.perHour(NOW), 1e-6);
        assertEquals(2, l.count());
        boolean threw = false;
        try { l.recordSale("bad", -1, 5, 0, NOW); } catch (IllegalArgumentException ex) { threw = true; }
        assertTrue(threw);
    }

    // ---------- config ----------
    @Test void existingDefaultsAreUnchanged() {
        Config c = new Config();
        assertEquals(1000.0, c.minProfit, 0);
        assertEquals(50, c.minConfidence);
        assertEquals(3, c.minSamples);
        assertEquals(14, c.maxAgeDays);
        assertEquals(2.0, c.undercutPercent, 0);
        assertFalse(c.feeVerified);
        assertFalse(c.liveCapture);
        assertEquals("auction", c.captureKeyword);
        assertTrue(c.chatAlerts);
        assertEquals(10, c.captureIntervalTicks);
    }

    @Test void badConfigValuesAreRepaired() {
        Config c = new Config();
        c.minSamples = 0; c.maxAgeDays = -5; c.minConfidence = 500; c.feeRatePercent = Double.NaN;
        c.sort = "BOGUS"; c.undercutPercent = 1000; c.wProfit = -3; c.captureKeyword = "  "; c.captureIntervalTicks = 0;
        c.watch.add(null);
        Config.WatchItem blank = new Config.WatchItem();
        c.watch.add(blank);
        Config.WatchItem ok = new Config.WatchItem();
        ok.name = "elytra"; ok.maxUnit = -5; ok.minConfidence = 900;
        c.watch.add(ok);
        c.sanitize();
        assertEquals(1, c.minSamples);
        assertEquals(1, c.maxAgeDays);
        assertEquals(100, c.minConfidence);
        assertEquals(0.0, c.feeRatePercent, 0);
        assertEquals("PROFIT", c.sort);
        assertEquals(90.0, c.undercutPercent, 0);
        assertEquals(0.0, c.wProfit, 0);
        assertEquals("auction", c.captureKeyword);
        assertEquals(2, c.captureIntervalTicks);
        assertEquals(1, c.watch.size());
        assertEquals(0.0, c.watch.get(0).maxUnit, 0);
        assertEquals(100, c.watch.get(0).minConfidence);
    }

    @Test void alertThresholdsFallBackToFlipSettings() {
        Config c = new Config();
        c.minProfit = 4321; c.minConfidence = 61;
        assertEquals(4321.0, c.aMinProfit(), 0);
        assertEquals(61, c.aMinConf());
        c.alertMinProfit = 99;
        assertEquals(99.0, c.aMinProfit(), 0);
    }

    // ---------- market history ----------
    @Test void historyTrendSparklineAndDateRange() {
        List<PriceRecord> all = new ArrayList<>();
        for (int i = 0; i < 8; i++) all.add(rec("x", 1000 + 100 * i, NOW - (8 - i) * DAY));
        Config c = new Config();
        Analyzer.HistoryRow row = Analyzer.history(all, c, NOW).get(0);
        assertEquals(8, row.count());
        assertTrue(row.trendPct() != null && row.trendPct() > 0);
        assertEquals(8, row.spark().length);
        c.marketRangeDays = 3;
        assertEquals(3, Analyzer.history(all, c, NOW).get(0).count());
    }

    @Test void historyNeverInventsTrendFromTooLittleData() {
        List<PriceRecord> all = new ArrayList<>(List.of(rec("x", 100, NOW - DAY), rec("x", 120, NOW)));
        Analyzer.HistoryRow row = Analyzer.history(all, new Config(), NOW).get(0);
        assertNull(row.trendPct());
        assertEquals(0, row.spark().length);
    }

    @Test void categoriesAndFormatting() {
        assertEquals("Weapons/Tools", Category.of("diamond_sword"));
        assertEquals("Weapons/Tools", Category.of("diamond_pickaxe"));
        assertEquals("Armor", Category.of("netherite_chestplate"));
        assertEquals("Armor", Category.of("elytra"));
        assertEquals("Books", Category.of("enchanted_book"));
        assertEquals("Other", Category.of("diamond_block"));
        assertEquals("12.0K", Fmt.money(12000));
        assertEquals("+3,800", Fmt.signed(3800));
        assertEquals("+38.0K", Fmt.signed(38000));
        assertEquals("-500", Fmt.signed(-500));
        assertEquals("3h", Fmt.age(3 * HOUR));
    }

    @Test void pageComesOnlyFromTheTitle() {
        assertEquals(4, PageParser.parse("Auction House - Page 4"));
        assertEquals(12, PageParser.parse("auction page: 12"));
        assertEquals(7, PageParser.parse("Auction (7/20)"));
        assertEquals(-1, PageParser.parse("Auction House"));
        assertEquals(-1, PageParser.parse("Page 0"));
        assertEquals(-1, PageParser.parse(null));
    }
}
