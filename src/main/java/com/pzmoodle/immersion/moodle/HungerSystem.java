package com.pzmoodle.immersion.moodle;

import com.pzmoodle.immersion.PZMoodleImmersion;
import com.pzmoodle.immersion.config.CommonConfig;
import com.pzmoodle.immersion.trait.OccupationTrait;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;

/**
 * 饱食度规则（服务端权威，全部按「世界时间」推进，性能 O(1)、无任何实体扫描）：
 * <ol>
 *   <li>持续消耗：站着不动也按时间掉饱食度，满一整管（20 点）正好过一个游戏日；
 *       睡觉跳到早上按跳过的时间补扣（世界时钟本身跳了多少就扣多少）。</li>
 *   <li>低饱食度渐进惩罚：低于 30% 起，随比例线性拉低走/跑/潜行速度、跳跃高度、
 *       攻击力与攻击速度；0 饱食度时正好减半。</li>
 *   <li>0 饱食度按周加深：持续 1 周降到「1 倍」、2 周「2 倍」、3 周「3 倍」，
 *       三周后维持 3 倍不再继续加深（惩罚封顶），满 3 周后生命开始缓慢流失，约 7 天耗尽。</li>
 *   <li>进食不会立刻恢复正常：惩罚值按体格线性回落，恢复速度以「扣血阶段（3 倍）」为基准——
 *       体格 0 级 2 天、体格 5 级 1 天、体格 10 级半天（每 5 级减半，10 级以上不再加速）；
 *       只要饱食度大于 0 就持续恢复（满饱食度、创造模式也不会卡住）；期间再次饿到 0，
 *       惩罚从原深度继续加深。</li>
 * </ol>
 * 原版侧：奔跑/跳跃/游泳/挨打饥饿消耗与「饿到≤6不能起跑」由混入关闭，
 * 原版 0 饱食度直接扣血由伤害事件取消（见 GameEvents）。
 */
public final class HungerSystem {
    public static final ResourceLocation STARVE_MOVE = PZMoodleImmersion.id("starve_move");
    public static final ResourceLocation STARVE_JUMP = PZMoodleImmersion.id("starve_jump");
    public static final ResourceLocation STARVE_ATTACK = PZMoodleImmersion.id("starve_attack");
    public static final ResourceLocation STARVE_ASPEED = PZMoodleImmersion.id("starve_aspeed");

    private static final long DAY_TICKS = 24000L;
    private static final long WEEK_TICKS = DAY_TICKS * 7L;

    /**
     * 由 MoodleTicker 的节流结算调用（默认每秒一次）。
     * elapsed = 本次结算覆盖的世界时间推进量（含睡觉跳过的时间，已在上游按 1 天封顶）。
     */
    public static void tick(ServerPlayer player, MoodleData data, long elapsed) {
        if (!CommonConfig.HUNGER_CONTINUOUS_DRAIN.get() || !player.isAlive()) return;
        FoodData food = player.getFoodData();
        // 创造/旁观（invulnerable）不饥饿；满饱食度时也不再往外掉。
        // 注意：这只用来决定「要不要继续加深饥饿」，恢复不受它影响（见下）。
        boolean drains = !player.getAbilities().invulnerable && food.needsFood();

        // —— 1. 按世界时间持续消耗饱食度（含睡觉快进的时间） ——
        long capped = Math.min(Math.max(0L, elapsed), DAY_TICKS);
        int foodLevel = food.getFoodLevel();
        if (drains && capped > 0 && foodLevel > 0) {
            float perDay = CommonConfig.HUNGER_FOOD_PER_DAY.get().floatValue();
            float gain = capped / (float) DAY_TICKS * perDay * OccupationTrait.hungerMultiplier(data);
            data.hungerCarry += gain;
            int lost = (int) data.hungerCarry;
            if (lost > 0) {
                data.hungerCarry -= lost;
                lost = Math.min(lost, foodLevel);
                food.setFoodLevel(foodLevel - lost);
                foodLevel -= lost;
                // 饱和度同步小幅回落，避免「吃饱」图标整天挂着
                float sat = food.getSaturationLevel() - lost * 0.15f;
                food.setSaturation(Math.max(0f, sat));
            }
            if (data.hungerCarry > 1f) data.hungerCarry = 1f;
        }

        // —— 2. 0 饱食度惩罚：随持续时间线性加深；进食后按体格线性回落 ——
        float week3 = CommonConfig.HUNGER_STARVE_WEEK3_PENALTY.get().floatValue();
        if (foodLevel <= 0) {
            if (drains) {
                data.starveTicks += capped;
                float scheduled = schedulePenalty(data.starveTicks);
                if (scheduled > data.starvePenalty) data.starvePenalty = scheduled;
                // —— 3. 满三周后：生命按剩余周数比例缓慢耗尽 ——
                if (data.starvePenalty >= week3 && capped > 0) {
                    applyHpDrain(player, data, capped);
                }
            }
        } else if (data.starvePenalty > 0f) {
            // 只要饱食度回到 0 以上就持续恢复。
            // 以前这里被上面那个 drains 提前 return 挡住：满饱食度、创造模式、
            // 或刚好卡在 20 点时整段不恢复，看起来就像「饿了很久还是慢吞吞」。
            data.starveTicks = 0;
            if (capped > 0) {
                int fitness = MoodleSystem.effectiveLevel(player, Skill.FITNESS);
                data.starvePenalty = Math.max(0f, data.starvePenalty - (float) capped * recoveryPerTick(fitness, week3));
            }
        }
        // 惩罚深度封顶在「满三周」那一档：饿得再久也不会越攒越深，
        // 恢复时间因此固定在配置表上（体格0=2天 / 5=1天 / 10=半天），不会膨胀。
        if (data.starvePenalty > week3) data.starvePenalty = week3;
    }

    /**
     * 每 tick 世界时间回落的惩罚量。
     * 以「扣血阶段（0 饱食度满 3 周）的惩罚深度」为基准：体格 0 级用
     * HUNGER_STARVE_RECOVERY_DAYS 天（默认 2 天）把这段惩罚完全恢复，
     * 体格每高 5 级所需时间减半（5 级=1 天、10 级=半天），10 级以上不再加速。
     */
    private static float recoveryPerTick(int fitness, float depth) {
        double daysAtZeroFitness = CommonConfig.HUNGER_STARVE_RECOVERY_DAYS.get();
        int level = Math.max(0, Math.min(10, fitness));
        double days = daysAtZeroFitness * Math.pow(0.5, level / 5.0);
        return (float) (depth / (days * DAY_TICKS));
    }

    /** 0 饱食度时长 → 应达到的惩罚深度（分段线性：0周=0.5，1周=1倍，2周=2倍，3周=3倍，三周后维持3倍不再加深）。 */
    private static float schedulePenalty(long ticks) {
        double weeks = ticks / (double) WEEK_TICKS;
        float p0 = CommonConfig.HUNGER_PENALTY_AT_ZERO.get().floatValue();
        float w1 = CommonConfig.HUNGER_STARVE_WEEK1_PENALTY.get().floatValue();
        float w2 = CommonConfig.HUNGER_STARVE_WEEK2_PENALTY.get().floatValue();
        float w3 = CommonConfig.HUNGER_STARVE_WEEK3_PENALTY.get().floatValue();
        if (weeks <= 1.0) return (float) (p0 + (w1 - p0) * weeks);
        if (weeks <= 2.0) return (float) (w1 + (w2 - w1) * (weeks - 1.0));
        if (weeks <= 3.0) return (float) (w2 + (w3 - w2) * (weeks - 2.0));
        // 三周之后维持 3 周档：惩罚不再无限加深，否则恢复会被拖成「越饿越久」
        return w3;
    }

    /** 缓慢掉血：按最大生命比例摊到配置的死亡天数上，攒够半心再扣，避免高频写生命值。 */
    private static void applyHpDrain(ServerPlayer player, MoodleData data, long elapsedTicks) {
        double days = CommonConfig.HUNGER_DEATH_DAYS.get();
        data.starveHpPending += player.getMaxHealth() * elapsedTicks / (days * DAY_TICKS);
        if (data.starveHpPending < 0.5f) return;
        float pending = data.starveHpPending;
        data.starveHpPending = 0f;
        float hp = player.getHealth();
        if (hp - pending <= 0f) {
            // 收尾一击用原版饿死伤害（自带绕无敌帧标签），死亡播报也是「饿死了」
            data.starveKill = true;
            try {
                player.hurt(player.damageSources().starve(), hp + 1f);
            } finally {
                data.starveKill = false;
            }
        } else {
            player.setHealth(hp - pending);
        }
    }

    /**
     * 当前应生效的整体数值倍率（走/跑/潜行速度、跳跃、攻击力、攻速共用）：
     * 取「低饱食度渐进」与「0 饱食度长期惩罚」的较大者，再夹到下限。
     */
    public static float penaltyMultiplier(Player player, MoodleData data) {
        if (player.getAbilities().invulnerable) return 1f; // 创造/旁观不受罚
        float penalty = data.starvePenalty;
        float start = CommonConfig.HUNGER_PENALTY_START_RATIO.get().floatValue();
        int foodLevel = player.getFoodData().getFoodLevel();
        if (start > 0f) {
            float ratio = foodLevel / 20f;
            if (ratio < start) {
                float lowFood = CommonConfig.HUNGER_PENALTY_AT_ZERO.get().floatValue() * (1f - ratio / start);
                penalty = Math.max(penalty, lowFood);
            }
        }
        float min = CommonConfig.HUNGER_MIN_MULTIPLIER.get().floatValue();
        return Math.max(min, 1f - penalty);
    }

    /** 由 MoodleSystem.applyEffects 在节流结算里调用：把倍率写成四个属性修正。 */
    public static void applyPenalties(Player player, MoodleData data) {
        double amount = penaltyMultiplier(player, data) - 1.0;
        if (amount > -0.002) amount = 0.0; // 40.96% 以下的惩罚幅度对手感无感，直接不挂修正
        AttributeInstance move = player.getAttribute(Attributes.MOVEMENT_SPEED);
        AttributeInstance jump = player.getAttribute(Attributes.JUMP_STRENGTH);
        AttributeInstance attack = player.getAttribute(Attributes.ATTACK_DAMAGE);
        AttributeInstance speed = player.getAttribute(Attributes.ATTACK_SPEED);
        set(move, STARVE_MOVE, amount);
        set(jump, STARVE_JUMP, amount);
        set(attack, STARVE_ATTACK, amount);
        set(speed, STARVE_ASPEED, amount);
    }

    private static void set(AttributeInstance attribute, ResourceLocation id, double amount) {
        if (attribute == null) return;
        AttributeModifier old = attribute.getModifier(id);
        if (Math.abs(amount) < 1.0E-6) {
            if (old != null) attribute.removeModifier(id);
            return;
        }
        if (old != null && old.amount() == amount) return;
        attribute.addOrUpdateTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    /** 玩家死亡重开：清空饥饿惩罚状态。 */
    public static void reset(MoodleData data) {
        data.hungerCarry = 0f;
        data.starveTicks = 0L;
        data.starvePenalty = 0f;
        data.starveHpPending = 0f;
        data.starveKill = false;
    }

    private HungerSystem() {}
}
