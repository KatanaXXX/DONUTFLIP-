package dev.riptide.knifelab.client;

import dev.riptide.knifelab.core.KnifeSettings;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;

/** Called from HeldItemRendererMixin. Kept out of the mixin so that class stays small. */
public final class KnifeRenderHooks {
    private KnifeRenderHooks() {}

    /**
     * The stack to draw. For a replaced sword this is a display copy that selects the knife model
     * through custom_model_data. The real stack, its damage, and its inventory slot are not modified.
     */
    public static ItemStack displayStack(ItemStack held) {
        if (!isReplacedHeldStack(held)) {
            return held;
        }
        KnifeSettings settings = RiptideKnifeLabClient.settings();
        ItemStack display = new ItemStack(held.getItem());
        display.set(DataComponentTypes.CUSTOM_MODEL_DATA, new CustomModelDataComponent(
                List.of(), List.of(), List.of(settings.getSelectedKnife().modelKey()), List.of()));
        return display;
    }

    public static boolean isInspectingHeldKnife(ItemStack stack) {
        return isReplacedHeldStack(stack) && RiptideKnifeLabClient.inspect().isActive();
    }

    public static float partialTick() {
        return MinecraftClient.getInstance().getRenderTickCounter().getTickProgress(true);
    }

    /** True when the stack matches the player's main-hand item and that item is one the mod replaces. */
    private static boolean isReplacedHeldStack(ItemStack stack) {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null || stack.isEmpty()) {
            return false;
        }
        ItemStack main = player.getMainHandStack();
        if (!ItemStack.areItemsAndComponentsEqual(stack, main)) {
            return false;
        }
        String id = Registries.ITEM.getId(main.getItem()).toString();
        return RiptideKnifeLabClient.settings().replacesItem(id);
    }
}
