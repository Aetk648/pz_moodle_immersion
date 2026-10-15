package com.pzmoodle.immersion.mixin;

import com.pzmoodle.immersion.moodle.MoodleSystem;
import com.pzmoodle.immersion.moodle.Skill;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 钓鱼等级加速咬钩倒计时：10 级时倒计时按三倍速度推进。 */
@Mixin(FishingHook.class)
public class FishingSpeedMixin {
    @Shadow private int timeUntilLured;

    @Inject(method = "tick", at = @At("HEAD"))
    private void pzmi$accelerateFishing(CallbackInfo ci) {
        FishingHook hook = (FishingHook) (Object) this;
        if (!(hook.getPlayerOwner() instanceof Player player) || player.level().isClientSide) return;
        int level = MoodleSystem.effectiveLevel(player, Skill.FISHING);
        if (level <= 0 || timeUntilLured <= 0) return;
        int extra = Math.max(0, (int) Math.floor(level * 0.20));
        timeUntilLured = Math.max(0, timeUntilLured - extra);
    }
}
