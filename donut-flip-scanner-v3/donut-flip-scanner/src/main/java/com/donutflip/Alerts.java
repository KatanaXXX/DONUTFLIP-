package com.donutflip;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/** Presents an alert through the channels the user enabled (toast, sound, chat). Never performs any game action. */
final class Alerts {
    private Alerts() { }

    static final AlertEngine ENGINE = new AlertEngine();

    static void present(Minecraft mc, AlertEngine.AlertEntry e, Config c) {
        if (c.toastAlerts) Toasts.push(e);
        if (c.soundAlerts) mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.2f));
        if (c.chatAlerts && mc.player != null) {
            mc.player.displayClientMessage(Component.literal("[DFS] " + (e.watch() ? "WATCH HIT: " : "NEW FLIP: ")
                    + e.item().replace('_', ' ') + " x" + e.quantity() + " | " + Toasts.pageText(e.page())
                    + " | BUY " + Fmt.money(e.price()) + " -> SELL ~" + Fmt.money(e.resale())
                    + " | NET ~" + Fmt.signed(e.net()) + " | ROI ~" + Fmt.pct(e.roi()) + " | " + e.level().name()), false);
        }
    }
}
