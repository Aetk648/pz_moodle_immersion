package com.pzmoodle.immersion.mixin;

import com.pzmoodle.immersion.config.CommonConfig;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 白天睡觉的第二道保险：拦住「睡够跳天」式被动叫醒。
 * <p>
 * 原版叫醒玩家有好几条路径，参数不同：
 * <ul>
 *   <li>玩家自己点起床 / 下线 / 被传送：{@code stopSleepInBed(true, ...)} 或 {@code (false, true)}，照常放行；</li>
 *   <li>白天复查失败（Player.tick）：{@code (false, true)} —— 和主动起床参数相同，不能拦，
 *       这条已由 {@code CanContinueSleepingEvent} 在事件层放行；</li>
 *   <li>只有「睡够跳天叫醒」（ServerLevel.wakeUpAllPlayers）是唯一的 {@code (false, false)}。</li>
 * </ul>
 * 本 mixin 只在这种纯被动叫醒且天已亮、玩家还活着时取消，
 * 防止某些情况（别的 mod 触发跳天等）把躺在床上的人突然弹起来，
 * 也就是玩家看到的「躺着站着来回切换」。
 * 夜晚跳天叫醒时 {@code isDay()} 为 false，不受影响，起床体验保持原版。
 */
@Mixin(ServerPlayer.class)
public class PassiveWakeGuardMixin {

    @Inject(method = "stopSleepInBed", at = @At("HEAD"), cancellable = true, require = 1)
    private void pzmi$guardPassiveWake(boolean updateState, boolean setPosition, CallbackInfo ci) {
        if (updateState || setPosition) return;
        if (!CommonConfig.ALLOW_DAY_SLEEP.get()) return;
        ServerPlayer self = (ServerPlayer) (Object) this;
        if (self.isAlive() && self.level().isDay()) {
            ci.cancel();
        }
    }
}
