package com.pzmoodle.immersion.mixin;

import com.pzmoodle.immersion.config.CommonConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 白天睡觉的第三道保险：别让「床的占用复查」把躺着的人弹起来。
 * <p>
 * 原版 LivingEntity.tick 每 tick 跑一次 {@code checkBedExists()}：
 * 只要「床方块 isBed 判定 / 占用记录」跟玩家的睡眠位置对不上（白天补觉时
 * 这种不一致很容易被别的 mod 或双色床、自定义床触发），就调用
 * {@code stopSleeping()} 把玩家强制弹起来 —— 这正是「躺着站着来回切换」，
 * 白天晚上都会出现的那条路径（它跟白天限制无关）。
 * <p>
 * 这里让服务端玩家的这次复查恒过，把「被动弹起」彻底断掉。
 * 不影响正常体验：
 * <ul>
 *   <li>玩家自己下床：走起床命令包（不经此检查），照常；</li>
 *   <li>床被拆掉：BedBlock 移除方块时直接叫醒（{@code stopSleepInBed(true, true)}），照常；</li>
 *   <li>夜晚睡够跳天：{@code stopSleepInBed(false, false)}，不在本 mixin 范围，
 *       由 PassiveWakeGuardMixin 按「天亮才拦」处理。</li>
 * </ul>
 */
@Mixin(LivingEntity.class)
public class BedOccupancyCheckMixin {

    @Inject(method = "checkBedExists", at = @At("HEAD"), cancellable = true, require = 1)
    private void pzmi$neverBounceOffBed(CallbackInfoReturnable<Boolean> cir) {
        if (!CommonConfig.ALLOW_DAY_SLEEP.get()) return;
        LivingEntity self = (LivingEntity) (Object) this;
        if (!(self instanceof ServerPlayer)) return;   // 只管服务端玩家，村民照常
        if (!self.isSleeping()) return;
        cir.setReturnValue(true);
    }
}
