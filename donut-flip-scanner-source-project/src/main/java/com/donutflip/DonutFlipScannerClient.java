package com.donutflip;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.class_304;
import net.minecraft.class_3675;
import org.lwjgl.glfw.GLFW;

public class DonutFlipScannerClient implements ClientModInitializer {
    public static class_304 OPEN_KEY;
    public static Config CONFIG;
    public static Store STORE;

    @Override
    public void onInitializeClient() {
        CONFIG = Config.load();
        STORE = new Store();
        STORE.load();

        OPEN_KEY = KeyBindingHelper.registerKeyBinding(new class_304(
                "key.donutflipscanner.open", class_3675.class_307.field_1668, GLFW.GLFW_KEY_F8, class_304.class_11900.field_62556));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (OPEN_KEY.method_1436()) {
                if (mc.field_1755 instanceof FlipScreen) mc.method_1507(null);
                else if (mc.field_1755 == null) mc.method_1507(new FlipScreen());
            }
            Tools.tick(mc);
            Capture.tick(mc);
        });
    }
}
