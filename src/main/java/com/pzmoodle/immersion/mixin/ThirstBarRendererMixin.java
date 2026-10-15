package com.pzmoodle.immersion.mixin;

import com.pzmoodle.immersion.config.ClientConfig;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 口渴模组（Thirst Was Reclaimed）的口渴条不是普通的 GUI 层，
 * 而是它自己 Mixin 到 Gui 里画出来的，光靠取消 GUI 层关不掉。
 * <p>
 * 这里直接把它「要不要画」的判断改掉：本模组配置里关掉口渴条时返回 false，
 * 口渴条与它自带的苹果皮式饱和度覆盖层就都不会画了。
 * <p>
 * targets 用字符串写，所以没装口渴模组时这个类不会参与编译，装不装都能正常加载。
 */
@Mixin(targets = "cn.mlus.thirst.foundation.gui.ThirstBarRenderer", remap = false)
public class ThirstBarRendererMixin {

    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void pzmi$hideThirstBar(Minecraft minecraft, CallbackInfoReturnable<Boolean> cir) {
        if (!ClientConfig.SHOW_THIRST_HUD.get()) {
            cir.setReturnValue(false);
        }
    }
}
