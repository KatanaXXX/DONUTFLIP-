package com.donutflip;

import static org.junit.jupiter.api.Assertions.*;

import com.donutflip.Analyzer.Analysis;
import com.donutflip.Analyzer.Rec;
import java.util.List;
import org.junit.jupiter.api.Test;

class AnalyzerTest {
    private static final long NOW = 1_800_000_000_000L;

    @Test
    void estimatesUseComparableMedianAndVerifiedFees() {
        Config cfg = new Config();
        cfg.minSamples = 2;
        cfg.minConfidence = 0;
        cfg.feeVerified = true;
        cfg.feeRatePercent = 5;
        PriceRecord target = record(10_000, NOW);
        List<Analysis> out = Analyzer.analyze(List.of(target, record(15_000, NOW - 1_000), record(20_000, NOW - 2_000)), cfg, NOW);
        Analysis a = out.get(0);

        assertEquals(17_500, a.estimatedMarketPrice());
        assertEquals(17_150, a.suggestedResalePrice());
        assertEquals(857.5, a.estimatedFees(), 0.01);
        assertEquals(6_292.5, a.estimatedNetProfit(), 0.01);
        assertEquals(Rec.BUY_CANDIDATE, a.recommendation());
    }

    @Test
    void insufficientComparableCountDoesNotInventAnEstimate() {
        Config cfg = new Config();
        cfg.minSamples = 3;
        Analysis a = Analyzer.analyze(List.of(record(5_000, NOW), record(6_000, NOW - 1_000)), cfg, NOW).get(0);
        assertEquals(Rec.INSUFFICIENT, a.recommendation());
        assertNull(a.suggestedResalePrice());
        assertNull(a.estimatedNetProfit());
    }

    @Test
    void oldTargetIsNotRecommendedEvenWithFreshComparables() {
        Config cfg = new Config();
        cfg.minSamples = 2;
        cfg.minConfidence = 0;
        cfg.maxAgeDays = 7;
        Analysis a = Analyzer.analyze(List.of(record(5_000, NOW - 10L * 86_400_000),
                record(10_000, NOW), record(11_000, NOW - 1_000)), cfg, NOW).get(0);
        assertEquals(Rec.AVOID, a.recommendation());
        assertEquals("Listing record is stale or undated", a.reason());
    }

    @Test
    void oldCsvConstructorDefaultsPageToUnknown() {
        assertEquals(-1, new PriceRecord("minecraft:stone", 1, 10, "", -1, NOW).page());
        assertEquals(4, new PriceRecord("minecraft:stone", 1, 10, "", -1, NOW, 4).page());
    }

    @Test
    void csvPageColumnIsOptionalAndBackwardCompatible() {
        PriceRecord oldRow = Store.parse("minecraft:stone,1,100,,,-1");
        PriceRecord pageRow = Store.parse("minecraft:stone,1,100,,,-1,4");
        assertNotNull(oldRow);
        assertNotNull(pageRow);
        assertEquals(-1, oldRow.page());
        assertEquals(4, pageRow.page());
    }

    private static PriceRecord record(double price, long timestamp) {
        return new PriceRecord("minecraft:diamond", 1, price, "", -1, timestamp);
    }
}
