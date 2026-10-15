package com.pzmoodle.immersion.mixin;

import com.pzmoodle.immersion.config.ClientConfig;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 配置关闭时直接跳过原版经验条绘制。 */
@Mixin(net.minecraft.client.gui.Gui.class)
public class ExperienceBarMixin {
    @Inject(method = "maybeRenderExperienceBar", at = @At("HEAD"), cancellable = true)
    private void pzmi$hideExperienceBar(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (!ClientConfig.SHOW_VANILLA_EXPERIENCE.get()) ci.cancel();
    }

    @Inject(method = "renderExperienceLevel", at = @At("HEAD"), cancellable = true, require = 0)
    private void pzmi$hideExperienceLevel(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (!ClientConfig.SHOW_VANILLA_EXPERIENCE.get()) ci.cancel();
    }
}
