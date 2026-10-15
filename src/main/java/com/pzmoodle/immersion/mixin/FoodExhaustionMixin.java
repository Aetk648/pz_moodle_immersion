package com.pzmoodle.immersion.mixin;

import com.pzmoodle.immersion.config.CommonConfig;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 饱食度新规则：奔跑/跳跃/游泳/挨打等「动作性」饥饿消耗全部走
 * Player.causeFoodExhaustion 这一个入口，直接掐断即可（配置可关）。
 * 之后饱食度只按世界时间匀速下降（见 HungerSystem），
 * 低饱食度对速度/攻击的影响改为渐进的属性修正而不是瞬间扣满。
 */
@Mixin(Player.class)
public abstract class FoodExhaustionMixin {

    @Inject(method = "causeFoodExhaustion", at = @At("HEAD"), cancellable = true)
    private void pzmi$disableVanillaExhaustion(float amount, CallbackInfo ci) {
        if (CommonConfig.HUNGER_DISABLE_VANILLA_DRAIN.get()) {
            ci.cancel();
        }
    }
}
