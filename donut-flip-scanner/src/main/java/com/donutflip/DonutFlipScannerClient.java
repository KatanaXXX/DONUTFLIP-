package com.donutflip;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public class DonutFlipScannerClient implements ClientModInitializer {
    public static KeyMapping OPEN_KEY;
    public static Config CONFIG;
    public static Store STORE;

    @Override
    public void onInitializeClient() {
        CONFIG = Config.load();
        STORE = new Store();
        STORE.load();

        OPEN_KEY = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.donutflipscanner.open", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F8, KeyMapping.Category.MISC));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (OPEN_KEY.consumeClick()) {
                if (mc.screen instanceof FlipScreen) mc.setScreen(null);
                else if (mc.screen == null) mc.setScreen(new FlipScreen());
            }
            Tools.tick(mc);
            Capture.tick(mc);
        });
    }
}
