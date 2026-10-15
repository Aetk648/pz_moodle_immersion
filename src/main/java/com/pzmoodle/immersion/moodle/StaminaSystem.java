package com.pzmoodle.immersion.moodle;

import com.pzmoodle.immersion.config.CommonConfig;
import com.pzmoodle.immersion.trait.OccupationTrait;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;

/** 服务端权威体力与负重结算。 */
public final class StaminaSystem {
    public static final ResourceLocation STAMINA_SPEED = ResourceLocation.fromNamespaceAndPath("pz_moodle_immersion", "stamina_speed");
    public static final ResourceLocation ENCUMBRANCE_SPEED = ResourceLocation.fromNamespaceAndPath("pz_moodle_immersion", "encumbrance_speed");
    public static final ResourceLocation PAIN_SPEED = ResourceLocation.fromNamespaceAndPath("pz_moodle_immersion", "pain_speed");

    public static void tick(ServerPlayer player) {
        MoodleData data = MoodleAttachments.get(player);
        int fitness = MoodleSystem.effectiveLevel(player, Skill.FITNESS);
        int strength = MoodleSystem.effectiveLevel(player, Skill.STRENGTH);
        float cap = Stamina.cap(fitness);
        if (data.stamina < 0f || data.stamina > 200f) data.stamina = cap;
        data.stamina = Math.min(data.stamina, cap);

        // 服务端移动探测：1.21.1 里 getDeltaMovement() 对玩家恒为 0，奔跑必须用真实坐标差判断
        MovementProbe.tick(player, data);

        int pain = data.displayLevels[Moodle.index(Moodle.PAIN)];
        boolean graceful = OccupationTrait.has(data, OccupationTrait.GRACEFUL);
        boolean clumsy = OccupationTrait.has(data, OccupationTrait.CLUMSY);
        boolean moving = MovementProbe.moving(data);
        boolean sprinting = player.isSprinting() && moving;
        if (sprinting) {
            data.addStamina(-Stamina.sprintDrainPerSecond(fitness, pain, graceful, clumsy) / 20f, cap);
        } else {
            boolean idle = !moving && !player.isCrouching();
            float regen = Stamina.regenPerTick(fitness, idle);
            // 躺在床上睡觉：额外恢复（默认 +200%，即 3 倍速）
            if (player.isSleeping()) {
                regen *= 1.0f + CommonConfig.STAMINA_SLEEP_REGEN_BONUS.get().floatValue();
            }
            data.addStamina(regen, cap);
        }
        applyMovement(player, data, fitness, strength, pain);
    }

    public static void attack(Player player) {
        if (player.level().isClientSide) return;
        MoodleData data = MoodleAttachments.get(player);
        int fitness = MoodleSystem.effectiveLevel(player, Skill.FITNESS);
        int pain = data.displayLevels[Moodle.index(Moodle.PAIN)];
        boolean graceful = OccupationTrait.has(data, OccupationTrait.GRACEFUL);
        boolean clumsy = OccupationTrait.has(data, OccupationTrait.CLUMSY);
        float extra = weaponExtraCost(player.getMainHandItem());
        data.addStamina(-Stamina.attackCost(extra, fitness, pain, graceful, clumsy), Stamina.cap(fitness));
    }

    /** TaCZ 近战通常把减移速写进物品数据；无数据时按名称给重锤一个保守兜底。 */
    private static float weaponExtraCost(ItemStack stack) {
        if (stack.isEmpty()) return 0f;
        String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().toLowerCase();
        if (path.contains("tacz") && (path.contains("hammer") || path.contains("melee"))) return 0.10f;
        if (path.contains("heavy_hammer") || path.contains("warhammer")) return 0.10f;
        return 0f;
    }

    private static void applyMovement(ServerPlayer player, MoodleData data, int fitness, int strength, int pain) {
        float staminaPercent = Stamina.percent(data.stamina, Stamina.cap(fitness));
        Encumbrance.State load = Encumbrance.state(occupiedSlots(player), strength);
        double staminaMultiplier = player.isSprinting() ? Stamina.sprintSpeedMultiplier(staminaPercent) : 1.0;
        double loadMultiplier = Encumbrance.speedMultiplier(load);
        double painMultiplier = Stamina.painSpeedMultiplier(pain);
        // 移动速度属性引用只取一次（原来每 tick 查三次注册表级 getter）
        AttributeInstance moveSpeed = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
        set(moveSpeed, STAMINA_SPEED, staminaMultiplier - 1.0);
        set(moveSpeed, ENCUMBRANCE_SPEED, loadMultiplier - 1.0);
        set(moveSpeed, PAIN_SPEED, painMultiplier - 1.0);
    }

    private static int occupiedSlots(Player player) {
        int count = 0;
        int size = Math.min(36, player.getInventory().getContainerSize());
        for (int i = 0; i < size; i++) if (!player.getInventory().getItem(i).isEmpty()) count++;
        return count;
    }

    public static int occupiedSlotsPublic(Player player) { return occupiedSlots(player); }
    public static float currentPercent(Player player) {
        MoodleData data = MoodleAttachments.get(player);
        return Stamina.percent(data.stamina, Stamina.cap(MoodleSystem.effectiveLevel(player, Skill.FITNESS)));
    }
    public static Encumbrance.State loadState(Player player) {
        return Encumbrance.state(occupiedSlots(player), MoodleSystem.effectiveLevel(player, Skill.STRENGTH));
    }

    private static void set(AttributeInstance attribute, ResourceLocation id, double amount) {
        if (attribute == null) return;
        AttributeModifier old = attribute.getModifier(id);
        if (Math.abs(amount) < 1.0E-6) { if (old != null) attribute.removeModifier(id); return; }
        if (old != null && old.amount() == amount) return;
        attribute.addOrUpdateTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    private StaminaSystem() {}
}
