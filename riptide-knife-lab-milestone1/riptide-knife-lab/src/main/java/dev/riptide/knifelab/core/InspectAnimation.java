package dev.riptide.knifelab.core;

/**
 * Tick-driven inspect animation state. The renderer reads swingAngle(partialTick).
 * No Minecraft dependencies, so the state machine is unit-testable.
 */
public final class InspectAnimation {
    public static final int DURATION_TICKS = 40; // 2 seconds at 20 TPS
    public static final float MAX_SWING_RADIANS = (float) Math.toRadians(55.0);

    private int elapsedTicks = -1; // -1 means inactive

    public boolean isActive() {
        return elapsedTicks >= 0;
    }

    public void start() {
        elapsedTicks = 0;
    }

    public void cancel() {
        elapsedTicks = -1;
    }

    /** Advances one client tick. No effect when inactive. */
    public void tick() {
        if (!isActive()) {
            return;
        }
        elapsedTicks++;
        if (elapsedTicks >= DURATION_TICKS) {
            elapsedTicks = -1;
        }
    }

    /** Progress in [0, 1], including the fractional tick. Zero when inactive. */
    public float progress(float partialTick) {
        if (!isActive()) {
            return 0f;
        }
        float p = (elapsedTicks + partialTick) / DURATION_TICKS;
        return Math.max(0f, Math.min(1f, p));
    }

    /** Spin angle in radians: rises to MAX_SWING_RADIANS mid-animation and returns to zero. */
    public float swingAngle(float partialTick) {
        return (float) Math.sin(progress(partialTick) * Math.PI) * MAX_SWING_RADIANS;
    }

    /** Gate for starting: knife equipped, feature enabled, and the player is free to act. */
    public static boolean canStart(boolean knifeEquipped, boolean featureEnabled,
                                   boolean screenOpen, boolean playerBusy, boolean alreadyActive) {
        return knifeEquipped && featureEnabled && !screenOpen && !playerBusy && !alreadyActive;
    }
}
