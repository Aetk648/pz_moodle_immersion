package com.pzmoodle.immersion.mixin;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attribute;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 第一人称视野不再随移动速度变化。
 * <p>
 * 原版 {@code AbstractClientPlayer.getFieldOfViewModifier()} 会把
 * 「当前移动速度 ÷ 默认行走速度」换算成视野倍率：速度越快视野越广、越慢视野越窄。
 * 本模组的冲刺/职业/饥饿惩罚/疲惫都会改移动速度，于是视野就跟着忽大忽小。
 * <p>
 * 这里把该方法里唯一一次读取 MOVEMENT_SPEED 的调用改成返回「默认行走速度」，
 * 比值恒为 1，视野倍率恒为 1 —— 于是不同速度下都是玩家在设置里调的那个视野。
 * 只有这一处，所以飞行、拉弓/望远镜缩放等其它视野效果照常保留。
 */
@Mixin(AbstractClientPlayer.class)
public abstract class SpeedFovMixin {

    @Redirect(method = "getFieldOfViewModifier",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/player/AbstractClientPlayer;getAttributeValue(Lnet/minecraft/core/Holder;)D"))
    private double pzmi$constantFov(AbstractClientPlayer player, Holder<Attribute> attribute) {
        // 与分母使用同一个「默认行走速度」，比值恒为 1.0，视野不随速度缩放
        return player.getAbilities().getWalkingSpeed();
    }
}
