package com.pzmoodle.immersion.mixin;

import com.pzmoodle.immersion.compat.TaczCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** TACZ 可选联动：开始换弹时给经验，并把换弹起始时间戳前移来缩短实际换弹耗时。 */
@Pseudo
@Mixin(targets = "com.tacz.guns.entity.shooter.LivingEntityReload", remap = false)
public class TaczReloadMixin {
    @Unique
    private boolean pzmi$timestampAdjusted;

    @Inject(method = "reload", at = @At("TAIL"), require = 0, remap = false)
    private void pzmi$onReloadStarted(CallbackInfo ci) {
        pzmi$timestampAdjusted = false;
        TaczCompat.onReloadStarted(this);
    }

    /** tickReloadState 返回 ReloadState（非 void），注入回调必须用 CallbackInfoReturnable。 */
    @Inject(method = "tickReloadState", at = @At("HEAD"), require = 0, remap = false)
    private void pzmi$adjustReloadTimestamp(CallbackInfoReturnable<Object> cir) {
        if (pzmi$timestampAdjusted) return;
        long timestamp = TaczCompat.reloadTimestamp(this);
        if (timestamp >= 0) {
            TaczCompat.adjustReloadTimestamp(this, timestamp);
            pzmi$timestampAdjusted = true;
        }
    }
}
