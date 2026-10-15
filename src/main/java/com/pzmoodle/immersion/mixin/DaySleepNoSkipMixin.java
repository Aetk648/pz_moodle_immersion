package com.pzmoodle.immersion.mixin;

import com.pzmoodle.immersion.config.CommonConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.SleepStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * 白天睡觉时不再触发原版「睡满即跳到第二天早上并叫醒所有人」。
 * 原版 ServerLevel.tick 里 areEnoughSleeping && areEnoughDeepSleeping 通过后会把时间
 * 直接跳 24000 并 wakeUpAllPlayers——单人白天躺床躺一小会儿就会被这个机制叫起来，
 * 表现为「躺着站着来回切换」。
 * <p>
 * 这里直接注入 SleepStatus.areEnoughDeepSleeping（比之前 redirect ServerLevel.tick 里的
 * 调用点更稳，不受方法体字节码调整影响）：只要本维度里有玩家处于白天睡眠，深睡判定恒为 false，
 * 时间正常流逝、玩家一直躺着，只有主动下床才结束；到了夜晚则恢复原版跳天逻辑。
 */
@Mixin(SleepStatus.class)
public class DaySleepNoSkipMixin {
    @Inject(method = "areEnoughDeepSleeping", at = @At("HEAD"), cancellable = true, require = 1)
    private void pzmi$noDaySleepSkip(int percentage, List<ServerPlayer> players, CallbackInfoReturnable<Boolean> cir) {
        if (!CommonConfig.ALLOW_DAY_SLEEP.get()) return;
        for (ServerPlayer player : players) {
            if (player.isSleeping() && player.level().isDay()) {
                cir.setReturnValue(false);
                return;
            }
        }
    }
}
