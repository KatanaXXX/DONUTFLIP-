package com.donutflip;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Flat, rounded, blue button. */
final class UiButton extends Button {
    private final boolean tab;
    private final boolean selected;

    UiButton(int x, int y, int w, int h, String label, Runnable action, boolean tab, boolean selected) {
        super(x, y, w, h, Component.literal(label), b -> action.run(), DEFAULT_NARRATION);
        this.tab = tab;
        this.selected = selected;
    }

    @Override
    public void renderWidget(GuiGraphics g, int mx, int my, float delta) {
        Font f = Minecraft.getInstance().font;
        float a = FlipScreen.chromeAlpha;
        boolean hov = isHoveredOrFocused();
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        int textColor = FlipScreen.TEXT;
        if (tab) {
            if (hov && !selected) Draw.round(g, x, y, x + w, y + h, 4, Draw.mul(0xFF1A2C52, a));
            textColor = selected ? FlipScreen.ACCENT : FlipScreen.DIM;
            if (hov) textColor = FlipScreen.TEXT;
        } else {
            int bg = hov ? 0xFF2B6FD1 : 0xFF1E4F9E;
            Draw.round(g, x, y, x + w, y + h, 5, Draw.mul(bg, a));
        }
        String s = getMessage().getString();
        g.drawString(f, s, x + (w - f.width(s)) / 2, y + (h - 8) / 2, Draw.mul(textColor, a));
    }
}
