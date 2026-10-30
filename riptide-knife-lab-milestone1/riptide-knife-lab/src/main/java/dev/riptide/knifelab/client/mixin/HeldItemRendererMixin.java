package dev.riptide.knifelab.client.mixin;

import dev.riptide.knifelab.client.KnifeRenderHooks;
import dev.riptide.knifelab.client.RiptideKnifeLabClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ModelTransformationMode;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * First-person hook. UNVERIFIED against 1.21.11: the held-item renderer was refactored in recent
 * versions, so the method name and parameter list here are the most likely things to fix on first build.
 */
@Mixin(HeldItemRenderer.class)
public abstract class HeldItemRendererMixin {

    /** Replaces the sword stack with the knife display stack. */
    @ModifyVariable(method = "renderItem", at = @At("HEAD"), argsOnly = true)
    private ItemStack riptide$swapSword(ItemStack stack) {
        return KnifeRenderHooks.displayStack(stack);
    }

    /** Applies the inspect spin to the held knife. */
    @Inject(method = "renderItem", at = @At("HEAD"))
    private void riptide$applyInspect(LivingEntity entity, ItemStack stack, ModelTransformationMode renderMode,
                                      boolean leftHanded, MatrixStack matrices,
                                      VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
        if (KnifeRenderHooks.isInspectingHeldKnife(stack)) {
            float angle = RiptideKnifeLabClient.inspect().swingAngle(KnifeRenderHooks.partialTick());
            matrices.multiply(new Quaternionf().rotateY(angle));
        }
    }
}
