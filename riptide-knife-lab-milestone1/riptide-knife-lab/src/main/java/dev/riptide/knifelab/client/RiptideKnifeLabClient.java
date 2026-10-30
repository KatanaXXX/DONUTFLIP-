package dev.riptide.knifelab.client;

import dev.riptide.knifelab.core.InspectAnimation;
import dev.riptide.knifelab.core.KnifeSettings;
import java.io.IOException;
import java.nio.file.Path;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class RiptideKnifeLabClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("riptide-knife-lab");

    private static final InspectAnimation INSPECT = new InspectAnimation();
    private static KnifeSettings settings = new KnifeSettings();
    private static Path configFile;
    private static int lastSlot = -1;

    @Override
    public void onInitializeClient() {
        configFile = FabricLoader.getInstance().getConfigDir().resolve("riptide-knife-lab.properties");
        try {
            settings = KnifeSettings.load(configFile);
        } catch (IOException e) {
            LOGGER.warn("Could not read {}; using defaults", configFile, e);
        }
        KeyBindings.register();
        ClientTickEvents.END_CLIENT_TICK.register(RiptideKnifeLabClient::onClientTick);
    }

    public static KnifeSettings settings() {
        return settings;
    }

    public static InspectAnimation inspect() {
        return INSPECT;
    }

    public static void saveSettings() {
        try {
            settings.save(configFile);
        } catch (IOException e) {
            LOGGER.error("Could not save Knife Lab settings", e);
        }
    }

    /** True when the player's main-hand item is a sword the mod replaces. */
    public static boolean isHoldingReplacedSword(ClientPlayerEntity player) {
        ItemStack stack = player.getMainHandStack();
        return settings.replacesItem(Registries.ITEM.getId(stack.getItem()).toString());
    }

    private static void onClientTick(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null) {
            INSPECT.cancel();
            return;
        }

        // Stop inspect when the selected slot changes or a screen opens, so the animation never sticks.
        int slot = player.getInventory().getSelectedSlot();
        if (slot != lastSlot || client.currentScreen != null) {
            INSPECT.cancel();
        }
        lastSlot = slot;

        // If both keys share one binding, Knife Lab wins and inspect is ignored.
        boolean sameKey = KeyBindings.OPEN_LAB.getBoundKeyTranslationKey()
                .equals(KeyBindings.INSPECT.getBoundKeyTranslationKey());

        while (KeyBindings.OPEN_LAB.wasPressed()) {
            if (client.currentScreen == null) {
                client.setScreen(new KnifeLabScreen());
            }
        }

        while (KeyBindings.INSPECT.wasPressed()) {
            if (sameKey) {
                continue;
            }
            boolean busy = player.isUsingItem() || client.options.attackKey.isPressed();
            if (InspectAnimation.canStart(
                    isHoldingReplacedSword(player),
                    settings.isInspectAnimationEnabled(),
                    client.currentScreen != null,
                    busy,
                    INSPECT.isActive())) {
                INSPECT.start();
            }
        }

        INSPECT.tick();
    }
}
