package com.pzmoodle.immersion.mixin;

import com.pzmoodle.immersion.moodle.MoodleAttachments;
import com.pzmoodle.immersion.moodle.MoodleSystem;
import com.pzmoodle.immersion.moodle.Skill;
import com.pzmoodle.immersion.trait.OccupationTrait;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.function.Consumer;

/** 保养技能：手持耐久物品时，10 级把每次耐久消耗减半。 */
@Mixin(ItemStack.class)
public class ItemDurabilityMixin {
    /** argsOnly 的 @ModifyVariable 处理器必须带上传入值 + 目标方法的全部参数。 */
    @ModifyVariable(method = "hurtAndBreak(ILnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;Ljava/util/function/Consumer;)V",
            at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
    private int pzmi$reduceDurabilityCost(int original, int amount, ServerLevel level, LivingEntity entity,
                                          Consumer<ItemStack> onBroken) {
        return reduce(original, entity);
    }

    private static int reduce(int amount, LivingEntity entity) {
        if (!(entity instanceof ServerPlayer player) || amount <= 0) return amount;
        int skill = MoodleSystem.effectiveLevel(player, Skill.MAINTENANCE);
        if (skill <= 0) return amount;
        // 10 级消耗 50%，每级减少 5%；至少保留一次真实消耗，避免耐久永不变化。
        return Math.max(1, Math.round(amount * Math.max(0.5f, 1f - skill * 0.05f)));
    }
}
