package com.pzmoodle.immersion.compat;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.lang.reflect.Method;

/**
 * 多模组软依赖：全部用反射 + 注册表查询，编译期与运行期都不依赖对方。
 * 未安装对应模组时每个方法返回「未知」，本模组功能自动跳过。
 */
public final class Compat {

    // ————— Thirst Was Reclaimed（modid: thirst）—————
    private static volatile boolean thirstResolved;
    private static AttachmentType<?> thirstType;
    private static Method thirstGetter;

    /** 返回玩家口渴值（0-20），未安装模组或读不到返回 -1。 */
    public static int thirstValue(Player player) {
        if (player instanceof FakePlayer) return -1;
        resolveThirst();
        if (thirstType == null || thirstGetter == null) return -1;
        try {
            Object cap = getDataMethod().invoke(player, thirstType);
            if (cap == null) return -1;
            Object v = thirstGetter.invoke(cap);
            return v instanceof Integer i ? i : -1;
        } catch (Throwable t) {
            return -1;
        }
    }

    private static void resolveThirst() {
        if (thirstResolved) return;
        synchronized (Compat.class) {
            if (thirstResolved) return;
            thirstResolved = true;
            try {
                thirstType = NeoForgeRegistries.ATTACHMENT_TYPES.get(
                        ResourceLocation.fromNamespaceAndPath("thirst", "player_thirst"));
                if (thirstType != null) {
                    Class<?> holder = Class.forName("cn.mlus.thirst.content.thirst.PlayerThirst");
                    thirstGetter = holder.getMethod("getThirst");
                }
            } catch (Throwable ignored) {
                thirstType = null;
            }
        }
    }

    // ————— Cold Sweat（modid: cold_sweat）—————
    private static volatile boolean tempResolved;
    private static AttachmentType<?> tempType;
    private static Method getTraitMethod;
    private static Object bodyTrait;

    /** 返回玩家体温（冷汗模组刻度），未安装返回 Double.NaN。 */
    public static double bodyTemperature(Player player) {
        if (player instanceof FakePlayer) return Double.NaN;
        resolveTemperature();
        if (tempType == null || getTraitMethod == null || bodyTrait == null) return Double.NaN;
        try {
            Object cap = getDataMethod().invoke(player, tempType);
            if (cap == null) return Double.NaN;
            Object v = getTraitMethod.invoke(cap, bodyTrait);
            return v instanceof Double d ? d : Double.NaN;
        } catch (Throwable t) {
            return Double.NaN;
        }
    }

    private static void resolveTemperature() {
        if (tempResolved) return;
        synchronized (Compat.class) {
            if (tempResolved) return;
            tempResolved = true;
            try {
                tempType = NeoForgeRegistries.ATTACHMENT_TYPES.get(
                        ResourceLocation.fromNamespaceAndPath("cold_sweat", "entity_temperature"));
                if (tempType != null) {
                    Class<?> traitClass = Class.forName("com.momosoftworks.coldsweat.api.util.Temperature$Trait");
                    @SuppressWarnings({"unchecked", "rawtypes"})
                    Object trait = Enum.valueOf((Class<? extends Enum>) traitClass, "BODY");
                    bodyTrait = trait;
                    // ITemperatureCap.getTrait(Trait)
                    Class<?> capClass = Class.forName("com.momosoftworks.coldsweat.common.capability.temperature.ITemperatureCap");
                    getTraitMethod = capClass.getMethod("getTrait", traitClass);
                }
            } catch (Throwable ignored) {
                tempType = null;
            }
        }
    }

    // ————— 森罗物语：酒馆（modid: kaleidoscope_tavern）—————
    private static volatile boolean tavernResolved;
    private static Holder<MobEffect> tipsyHolder;

    /** 玩家微醺效果的等级（0=没有喝酒）。 */
    public static int tipsyLevel(Player player) {
        resolveTavern();
        if (tipsyHolder == null) return 0;
        MobEffectInstance inst = player.getEffect(tipsyHolder);
        if (inst == null) return 0;
        return Math.min(inst.getAmplifier() + 1, 4);
    }

    private static void resolveTavern() {
        if (tavernResolved) return;
        synchronized (Compat.class) {
            if (tavernResolved) return;
            tavernResolved = true;
            try {
                MobEffect effect = BuiltInRegistries.MOB_EFFECT.get(
                        ResourceLocation.fromNamespaceAndPath("kaleidoscope_tavern", "slightly_tipsy"));
                if (effect != null) tipsyHolder = BuiltInRegistries.MOB_EFFECT.wrapAsHolder(effect);
            } catch (Throwable ignored) {
                tipsyHolder = null;
            }
        }
    }

    // ————— 反射工具 —————
    private static Method getDataMethod;

    private static Method getDataMethod() throws NoSuchMethodException {
        Method m = getDataMethod;
        if (m == null) {
            m = IAttachmentHolder.class.getMethod("getData", AttachmentType.class);
            getDataMethod = m;
        }
        return m;
    }

    /** 该模组是否已加载。 */
    public static boolean modLoaded(String modid) {
        return net.neoforged.fml.ModList.get().isLoaded(modid);
    }

    private Compat() {}
}
