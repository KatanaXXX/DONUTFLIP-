# Donut Flip Scanner 0.2.0

Fabric client mod for Minecraft 1.21.11. This project was reconstructed from the supplied source JAR; the original Gradle wrapper/build files were not included.

## Build

Requires JDK 21 and Gradle 9.2 or newer. From this directory run `gradle build`. The distributable is created under `build/libs/`.

The included Java sources use Minecraft intermediary names, matching the namespace recorded in the supplied source JAR manifest. The build uses Fabric Loom 1.15.5, Fabric Loader 0.19.2, and Fabric API 0.141.4 for Minecraft 1.21.11.

## Install

Copy `build/libs/donut-flip-scanner-0.2.0.jar` to the Minecraft instance's `mods` folder with Fabric Loader and Fabric API installed.

## Notes

- Existing `import.csv`, `captured.csv`, and JSON configuration remain readable. The captured CSV accepts an optional final `page` column; prior six-column rows load with page unknown.
- The current capture mechanism only reads the visible menu. It does not navigate pages, click listings, buy, sell, or send packets. The source archive does not expose a reliable page number, so records show `PAGE UNKNOWN`.
- The official server terms prohibit modifications that provide unfair advantages and macros/scripts. They do not specifically clarify whether passive visible-menu capture is permitted. Confirm with DonutSMP support before enabling live capture; no safety guarantee is made.
- Asking-price observations are not confirmed sales, and profit estimates are not guarantees.
