package dev.riptide.knifelab.client;

import dev.riptide.knifelab.core.KnifeSettings;
import dev.riptide.knifelab.core.KnifeType;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** Milestone-1 Knife Lab: a working list for choosing the knife and toggling features. The 3D preview comes in milestone 4. */
public final class KnifeLabScreen extends Screen {
    private static final int BUTTON_WIDTH = 220;
    private static final int BUTTON_HEIGHT = 20;
    private static final int ACCENT = 0xFF7FE8E0; // turquoise

    private final Map<KnifeType, ButtonWidget> knifeButtons = new EnumMap<>(KnifeType.class);
    private ButtonWidget replacementButton;
    private ButtonWidget inspectButton;

    public KnifeLabScreen() {
        super(Text.literal("Knife Lab"));
    }

    @Override
    protected void init() {
        knifeButtons.clear();
        int x = width / 2 - BUTTON_WIDTH / 2;
        int y = height / 2 - 80;

        for (KnifeType knife : KnifeType.values()) {
            ButtonWidget button = ButtonWidget.builder(Text.empty(), b -> {
                        RiptideKnifeLabClient.settings().setSelectedKnife(knife);
                        save();
                    })
                    .dimensions(x, y, BUTTON_WIDTH, BUTTON_HEIGHT)
                    .build();
            knifeButtons.put(knife, addDrawableChild(button));
            y += 24;
        }

        replacementButton = addDrawableChild(ButtonWidget.builder(Text.empty(), b -> {
                    KnifeSettings s = RiptideKnifeLabClient.settings();
                    s.setReplacementEnabled(!s.isReplacementEnabled());
                    save();
                })
                .dimensions(x, y + 8, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());

        inspectButton = addDrawableChild(ButtonWidget.builder(Text.empty(), b -> {
                    KnifeSettings s = RiptideKnifeLabClient.settings();
                    s.setInspectAnimationEnabled(!s.isInspectAnimationEnabled());
                    save();
                })
                .dimensions(x, y + 32, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> close())
                .dimensions(x, y + 64, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());

        refreshLabels();
    }

    private void save() {
        RiptideKnifeLabClient.saveSettings();
        refreshLabels();
    }

    private void refreshLabels() {
        KnifeSettings s = RiptideKnifeLabClient.settings();
        for (KnifeType knife : KnifeType.values()) {
            String suffix = s.getSelectedKnife() == knife ? "  [Equipped]" : "";
            knifeButtons.get(knife).setMessage(Text.literal(knife.displayName() + suffix));
        }
        replacementButton.setMessage(Text.literal("Sword replacement: " + onOff(s.isReplacementEnabled())));
        inspectButton.setMessage(Text.literal("Inspect animation: " + onOff(s.isInspectAnimationEnabled())));
    }

    private static String onOff(boolean value) {
        return value ? "On" : "Off";
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 20, ACCENT);
    }
}
