package dev.riptide.knifelab.client;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

/** Both keys appear under Options > Controls > Key Binds and can be rebound there. */
public final class KeyBindings {
    private static final KeyBinding.Category CATEGORY =
            KeyBinding.Category.create(Identifier.of("riptide_knife_lab", "main"));

    public static final KeyBinding OPEN_LAB = new KeyBinding(
            "key.riptide_knife_lab.open_lab", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_J, CATEGORY);

    public static final KeyBinding INSPECT = new KeyBinding(
            "key.riptide_knife_lab.inspect", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_G, CATEGORY);

    private KeyBindings() {}

    public static void register() {
        KeyBindingHelper.registerKeyBinding(OPEN_LAB);
        KeyBindingHelper.registerKeyBinding(INSPECT);
    }
}
