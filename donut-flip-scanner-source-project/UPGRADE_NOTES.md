# Upgrade audit and implementation notes

## Starting point

The supplied ZIP contained the compiled mod and a source JAR, but no Gradle project, wrapper, tests, or build instructions. The source manifest identifies Minecraft 1.21.11, Java 21, Fabric Loader 0.19.2, Fabric Loom 1.15.5, and the intermediary mapping namespace. The existing six modules are Flips, Avoid, Market, Watch, Tools, and Settings. Capture is a visible-screen reader, initially off; storage is local CSV/JSON; alerting is chat-based.

## Changes in this source project

- Reconstructed a conventional Loom project layout and added Gradle build metadata and installation notes.
- Added an optional seventh CSV field for AH page attribution. Old six-field CSV rows remain readable. The current source does not expose a reliable page number, so new captured entries correctly remain unknown.
- Restructured the Flips rows to show observed listing price, a maximum buy price based on estimated resale less configured fees and minimum profit, suggested resale, estimated net, ROI, confidence, sample count, age, page, and reason.
- Added a compact Flips dashboard summary with captured count, current candidate and avoid counts, estimated potential profit, and the separate manually logged session total.
- Refreshes analysis when newly captured records change the record count.
- Expanded new-candidate chat alerts to include the page status, maximum buy, estimated resale, net, ROI, and confidence. Existing captured-record deduplication means unchanged signatures do not alert repeatedly; a lower price has a new signature and can qualify again.
- Preserved existing config defaults and kept the previous PriceRecord constructor available to source callers.

## Calculation and data limits

The analyzer continues to use recent comparable asking-price medians, not confirmed sales. The maximum buy calculation is `floor(suggested resale × (1 − verified fee rate) − configured minimum profit)`. Fees remain zero unless marked verified. Confidence continues to reflect sample size, recency, and median absolute deviation. This iteration does not add a separate volatility model, category filters, alert cooldown settings, toast/sound alerts, alert history, or a transaction ledger. Session profit remains a manual log, and potential profit is kept separate.

Page attribution remains `PAGE UNKNOWN` because the supplied screen reader only sees container title, slots, item data, and lore; it does not establish a trustworthy page number. No page is guessed. The existing capture behavior was not expanded. The official terms prohibit mods that provide unfair advantages and macros/scripts, but do not specifically state whether this passive visible-menu capture is permitted. Confirm with DonutSMP support before enabling it.

## Verification

No Gradle executable, wrapper, or Java 21 runtime was available in the workspace. The host has Java 25 only. The Gradle build and JUnit suite could not be run, so this deliverable does not claim a full project build and does not include a newly compiled JAR. The original supplied binary is not represented as an upgraded build. I added JUnit coverage for median/fee calculations, stale records, page compatibility, and CSV migration, and separately compiled the analysis/storage classes against Java 21's language target and passed a small runtime check for profit math and page-column compatibility. No tests were supplied with the original archive.
