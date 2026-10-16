# Donut Flip Scanner (Fabric 1.21.11) — offline mode

Client-side market-analysis helper. **Live AH scanning is intentionally not implemented**
(`DonutFlipScannerClient.LIVE_SCAN_APPROVED = false`). Before adding it: read DonutSMP's current
rules and get explicit staff approval for the exact method. This mod does not claim to be
undetectable or ban-proof, sends nothing to the server, and automates nothing.

## Build
1. Install JDK 21.
2. Generate the wrapper once: `gradle wrapper --gradle-version 9.2.0` (or copy the wrapper from the Fabric example mod).
3. Check `gradle.properties` against https://fabricmc.net/develop/ (loader, loom, fabric API).
4. `./gradlew build` -> `build/libs/donut-flip-scanner-0.1.0.jar`

## Use
Press **F8** (rebindable under Controls > Misc). Put your recorded observations in
`.minecraft/config/donutflipscanner/import.csv` (a template is auto-created), then press Reload.

    item,quantity,price,attributes,durability_percent,timestamp
    diamond_sword,1,12k,sharpness5;unbreaking3,100,2026-10-14

No data ships with the mod. Every number shown comes from your file.

## Model
observedListingPrice / estimatedMarketPrice (median unit price of comparable records x qty) /
suggestedResalePrice (market minus undercut) / estimatedFees (only if verified) /
estimatedNetProfit / roiPercent / confidence (sample size 50 + recency 25 + consistency 25) /
sampleSize / lastUpdated.

## Unverified (could not compile in my sandbox — check these first if the build errors)
- Loom version and plugin id `net.fabricmc.fabric-loom-remap`
- 1.21.11 input API: `Screen.keyPressed(KeyEvent)`, `KeyMapping.matches(KeyEvent)`, `KeyMapping.Category.MISC`
- `GuiGraphics.enableScissor/disableScissor`, `drawString(Font,String,int,int,int)`


## v0.2
- Blue rounded UI, fades, 6 tabs: Flips, Avoid, Market, Watch, Tools, Settings.
- Optional passive AH capture (Settings > Live AH capture, off by default, needs CONFIRM).
  It only reads the AH screen you open yourself. Price/title formats are guesses; use Settings > Dump
  then send debug.txt if listings aren't detected.
- Watchlist chat alerts, flip calculator, session profit log, timers.
