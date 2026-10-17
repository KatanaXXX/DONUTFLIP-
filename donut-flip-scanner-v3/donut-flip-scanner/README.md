# Donut Flip Scanner 0.3.0 (Fabric 1.21.11)

Client-side market-analysis helper for DonutSMP's Auction House. It **never** clicks, sends packets or commands,
changes AH pages, or buys/sells. Live capture only READS the AH screen you open yourself, is OFF by default, and needs
a confirmation to enable. It cannot promise to be ban-proof: check DonutSMP's rules / ask staff.

## Build
1. JDK 21. 2. `gradle wrapper --gradle-version 9.2.0` (once). 3. `./gradlew build` -> `build/libs/donut-flip-scanner-0.3.0.jar`
(or use the GitHub Actions workflow). Unit tests run during the build and print PASSED/FAILED in the log.

## Use
F8 opens the dashboard. 1-8 switch tabs, arrows/PgUp/PgDn scroll, `?` explains every number, hover buttons for tips.
Data: `config/donutflipscanner/import.csv` (manual) and `captured.csv` (auto, from live capture).

    item,quantity,price,attributes,durability_percent,timestamp,page
    diamond_block,64,8k,,,2026-10-14,4

## How the numbers work (see Analyzer.java header)
Comparable = same item, enchants, 10% durability band, stack-size band. Outliers (3.5 scaled MADs) are dropped.
Market estimate = recency-weighted median unit price x quantity. Resale = estimate minus undercut. Fees only when
"Fee verified" = YES. Max buy = highest price meeting BOTH min profit and min ROI (so price <= max buy is the margin
test for BUY CANDIDATE). Confidence = sample size 50 + recency 25 + consistency 25. Score weights are in config.json
(wProfit, wRoi, wConf, wFresh, wSample, wVol) and the score is capped by confidence when confidence < your minimum.

## Migration
Old config.json / import.csv / captured.csv load unchanged: new fields get defaults, bad values are clamped, old
6-column CSV lines still parse (page = unknown). Existing default values are unchanged.

## Known limitations
- AH page comes ONLY from the screen title ("Page 4" / "(4/10)"); otherwise PAGE UNKNOWN. Title format is unverified.
- Price/lore format is a guess ("$" or "price" in lore). Use Settings > Dump and send debug.txt if nothing is detected.
- Identical listings (same item, stack, price, traits) collapse into one record; a price change creates a new record.
- No "minimal mode" exists (never did). Row click-selection and per-row tooltips are not implemented.
