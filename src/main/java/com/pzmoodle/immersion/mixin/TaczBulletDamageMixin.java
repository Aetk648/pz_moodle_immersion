package com.pzmoodle.immersion.mixin;

import com.pzmoodle.immersion.compat.TaczCompat;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** TaCZ 的子弹和假人伤害都从 getDamage 进入，瞄准技能在这里统一放大。 */
@Pseudo
@Mixin(targets = "com.tacz.guns.entity.EntityKineticBullet", remap = false)
public class TaczBulletDamageMixin {
    @Inject(method = "getDamage", at = @At("RETURN"), cancellable = true, require = 0, remap = false)
    private void pzmi$scaleBulletDamage(Vec3 position, CallbackInfoReturnable<Float> cir) {
        float damage = cir.getReturnValue();
        if (damage > 0f) cir.setReturnValue(damage * TaczCompat.bulletDamageScale(this));
    }
}
