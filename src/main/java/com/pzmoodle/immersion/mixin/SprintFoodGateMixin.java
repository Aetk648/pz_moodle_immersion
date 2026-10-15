package com.pzmoodle.immersion.mixin;

import com.pzmoodle.immersion.client.ClientState;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 原版在饱食度 ≤6（30%）时禁止开始冲刺；新规则改由渐进惩罚压低奔跑速度，
 * 因此当服务端下发「关闭原版动作饥饿消耗」时放开这个客户端门槛——
 * 低饱食度照样能跑，只是越饿越慢（服务端属性修正兜底）。
 * 开关随 MoodleSync 同步，不在本地读配置，联机时以服务端为准。
 */
@Mixin(LocalPlayer.class)
public abstract class SprintFoodGateMixin {

    @Inject(method = "hasEnoughFoodToStartSprinting", at = @At("HEAD"), cancellable = true)
    private void pzmi$allowSprintWhenHungry(CallbackInfoReturnable<Boolean> cir) {
        if (ClientState.allowLowFoodSprint) {
            cir.setReturnValue(true);
        }
    }
}
