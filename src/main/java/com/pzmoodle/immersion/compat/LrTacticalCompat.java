package com.pzmoodle.immersion.compat;

import com.pzmoodle.immersion.moodle.MoodleSystem;
import com.pzmoodle.immersion.moodle.Skill;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** LesRaisins Tactical Equipements 的软联动，不引用其类，未安装时完全跳过。 */
public final class LrTacticalCompat {
    private static final ResourceLocation MEDICAL = ResourceLocation.fromNamespaceAndPath("lrtactical", "medical");
    private static final Map<UUID, UseState> STATES = new HashMap<>();
    private static boolean resolved;
    private static Method getConsumableIndex;
    private static AttachmentType<?> combatType;
    private static AttachmentType<?> cooldownType;
    private static Method getData;

    public static boolean isMedical(ItemStack stack) {
        Object data = consumableData(stack);
        if (data == null) return false;
        try {
            Object category = data.getClass().getMethod("getCooldownCategory").invoke(data);
            return MEDICAL.equals(category);
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** 使用中每 tick 把 LesRaisins 的 toggle 计时器按急救等级向前推进。 */
    public static void tickUsing(ServerPlayer player) {
        // 性能：绝大多数 tick 玩家并没有在用医疗品，先廉价判断再进状态表，
        // 避免每玩家每 tick 都建/查 Map。
        if (!player.isUsingItem() && !STATES.containsKey(player.getUUID())) return;
        ItemStack stack = player.getUseItem();
        boolean medical = player.isUsingItem() && isMedical(stack);
        UseState state = STATES.computeIfAbsent(player.getUUID(), ignored -> new UseState());
        if (medical) {
            int level = MoodleSystem.effectiveLevel(player, Skill.FIRST_AID);
            float scale = Math.max(0.5f, 1f - level * 0.05f);
            state.wasUsing = true;
            Object data = consumableData(stack);
            state.category = medicalCategory(data);
            state.cooldown = intValue(data, "getCooldown");
            if (level > 0) {
                Object combat = attachment(player, combatType("combat_properties"));
                if (combat != null) {
                    try {
                        Method get = combat.getClass().getMethod("getToggleUseTicks");
                        Method set = combat.getClass().getMethod("setToggleUseTicks", int.class);
                        int current = ((Number) get.invoke(combat)).intValue();
                        state.fraction += (1.0 / scale) - 1.0;
                        int extra = (int) state.fraction;
                        if (extra > 0) {
                            set.invoke(combat, current + extra);
                            state.fraction -= extra;
                        }
                    } catch (Throwable ignored) {
                    }
                }
            }
        } else if (state.wasUsing) {
            state.wasUsing = false;
            if (state.category != null && state.cooldown > 0) {
                Object cooldowns = attachment(player, combatType("custom_cooldown"));
                if (cooldowns != null) {
                    try {
                        Method remove = cooldowns.getClass().getMethod("removeCooldown", ResourceLocation.class);
                        Method add = cooldowns.getClass().getMethod("addCooldown", ResourceLocation.class, int.class);
                        int level = MoodleSystem.effectiveLevel(player, Skill.FIRST_AID);
                        int reduced = Math.max(1, Math.round(state.cooldown * Math.max(0.5f, 1f - level * 0.05f)));
                        remove.invoke(cooldowns, state.category);
                        add.invoke(cooldowns, state.category, reduced);
                    } catch (Throwable ignored) {
                    }
                }
            }
            state.category = null;
            state.cooldown = 0;
            state.fraction = 0;
        }
    }

    /** 通用使用事件：对遵循原版 useDuration 的医疗物品缩短实际使用时间。 */
    public static void scaleStart(net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent.Start event) {
        if (!(event.getEntity() instanceof Player player) || !isMedical(event.getItem())) return;
        int level = MoodleSystem.effectiveLevel(player, Skill.FIRST_AID);
        if (level <= 0) return;
        float scale = Math.max(0.5f, 1f - level * 0.05f);
        event.setDuration(Math.max(1, Math.round(event.getDuration() * scale)));
    }

    /** LesRaisins 的 heal 字段走 LivingEntity.heal，且结算时仍处于使用状态。 */
    public static void scaleHeal(net.neoforged.neoforge.event.entity.living.LivingHealEvent event) {
        if (!(event.getEntity() instanceof Player player) || !player.isUsingItem() || !isMedical(player.getUseItem())) return;
        int level = MoodleSystem.effectiveLevel(player, Skill.FIRST_AID);
        if (level > 0) event.setAmount(event.getAmount() * (1f + level * 0.05f));
    }

    private static Object consumableData(ItemStack stack) {
        resolve();
        if (getConsumableIndex == null || stack.isEmpty()) return null;
        try {
            Object result = getConsumableIndex.invoke(null, stack);
            if (!(result instanceof Optional<?> optional) || optional.isEmpty()) return null;
            return optional.get().getClass().getMethod("getData").invoke(optional.get());
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static ResourceLocation medicalCategory(Object data) {
        if (data == null) return null;
        try {
            Object value = data.getClass().getMethod("getCooldownCategory").invoke(data);
            return value instanceof ResourceLocation rl && MEDICAL.equals(rl) ? rl : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static int intValue(Object target, String method) {
        if (target == null) return 0;
        try {
            return ((Number) target.getClass().getMethod(method).invoke(target)).intValue();
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static Object attachment(ServerPlayer player, AttachmentType<?> type) {
        if (type == null) return null;
        try {
            if (getData == null) getData = player.getClass().getMethod("getData", AttachmentType.class);
            return getData.invoke(player, type);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static AttachmentType<?> combatType(String id) {
        resolve();
        return "combat_properties".equals(id) ? combatType : cooldownType;
    }

    private static void resolve() {
        if (resolved) return;
        synchronized (LrTacticalCompat.class) {
            if (resolved) return;
            resolved = true;
            try {
                Class<?> api = Class.forName("me.xjqsh.lrtactical.api.LrTacticalAPI");
                getConsumableIndex = api.getMethod("getConsumableIndex", ItemStack.class);
                combatType = NeoForgeRegistries.ATTACHMENT_TYPES.get(
                        ResourceLocation.fromNamespaceAndPath("lrtactical", "combat_properties"));
                cooldownType = NeoForgeRegistries.ATTACHMENT_TYPES.get(
                        ResourceLocation.fromNamespaceAndPath("lrtactical", "custom_cooldown"));
            } catch (Throwable ignored) {
                getConsumableIndex = null;
                combatType = null;
                cooldownType = null;
            }
        }
    }

    private static final class UseState {
        boolean wasUsing;
        double fraction;
        ResourceLocation category;
        int cooldown;
    }

    private LrTacticalCompat() {}
}
