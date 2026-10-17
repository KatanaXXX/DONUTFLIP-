package com.donutflip;

import net.minecraft.class_2561;
import net.minecraft.class_310;
import net.minecraft.class_327;
import net.minecraft.class_332;
import net.minecraft.class_4185;

/** Flat, rounded, blue button. */
final class UiButton extends class_4185 {
    private final boolean tab;
    private final boolean selected;

    UiButton(int x, int y, int w, int h, String label, Runnable action, boolean tab, boolean selected) {
        super(x, y, w, h, class_2561.method_43470(label), b -> action.run(), field_40754);
        this.tab = tab;
        this.selected = selected;
    }

    @Override
    public void method_75752(class_332 g, int mx, int my, float delta) {
        class_327 f = class_310.method_1551().field_1772;
        float a = FlipScreen.chromeAlpha;
        boolean hov = method_25367();
        int x = method_46426(), y = method_46427(), w = method_25368(), h = method_25364();
        int textColor = FlipScreen.TEXT;
        if (tab) {
            if (hov && !selected) Draw.round(g, x, y, x + w, y + h, 4, Draw.mul(0xFF1A2C52, a));
            textColor = selected ? FlipScreen.ACCENT : FlipScreen.DIM;
            if (hov) textColor = FlipScreen.TEXT;
        } else {
            int bg = hov ? 0xFF2B6FD1 : 0xFF1E4F9E;
            Draw.round(g, x, y, x + w, y + h, 5, Draw.mul(bg, a));
        }
        String s = method_25369().getString();
        g.method_25303(f, s, x + (w - f.method_1727(s)) / 2, y + (h - 8) / 2, Draw.mul(textColor, a));
    }
}
