package com.pzmoodle.immersion.moodle;

import com.pzmoodle.immersion.config.CommonConfig;
import com.pzmoodle.immersion.trait.OccupationTrait;
import com.pzmoodle.immersion.trait.Occupation;

/**
 * 体力（Endurance）换算，规则照《僵尸毁灭工程》：
 * 上限由体格决定（0 级 -25%，5 级基准，10 级翻倍）；
 * 走步不消耗且缓慢恢复，奔跑持续消耗，近战每次攻击按武器消耗量扣除；
 * 疼痛、体笨如牛会加重消耗，身轻如燕、高体格会减少消耗；
 * 体力低于 50% 后无法全速奔跑，一路掉到走步速度。
 */
public final class Stamina {

    /** 体格 0/5/10 对应上限 75%/100%/200%。 */
    public static float cap(int fitness) {
        float v = Math.max(0, Math.min(Skill.MAX_LEVEL, fitness));
        return (float) (v <= Occupation.BASE_STAT
                ? 75.0 + (v - Occupation.BASE_STAT) * (25.0 / Occupation.BASE_STAT)
                : 100.0 + (v - Occupation.BASE_STAT) * (100.0 / Occupation.BASE_STAT));
    }

    /** 奔跑每秒消耗（占上限百分比），疼痛与特质会放大、体格与身轻如燕会缩小。 */
    public static float sprintDrainPerSecond(int fitness, int painLevel, boolean graceful, boolean clumsy) {
        float base = CommonConfig.STAMINA_SPRINT_DRAIN_PER_SECOND.get().floatValue();
        base *= 1.0 + painLevel * CommonConfig.STAMINA_PAIN_DRAIN_PER_LEVEL.get() * 4.0;
        if (graceful) base *= 1.0f - CommonConfig.STAMINA_GRACEFUL_REDUCE.get().floatValue();
        if (clumsy) base *= 1.0f + CommonConfig.STAMINA_CLUMSY_INCREASE.get().floatValue();
        base *= consumptionModifier(fitness);
        return Math.max(0.1f, base);
    }

    /** 体格越高消耗越少：10 级消耗为 50%，每级 5%。 */
    public static float consumptionModifier(int fitness) {
        return Math.max(0.5f, 1.0f - Math.max(0, Math.min(10, fitness)) * 0.05f);
    }

    /** 近战每次攻击的体力消耗：基准 1 点 + 武器附加消耗（TaCZ 按减移速换算）。 */
    public static float attackCost(float weaponExtra, int fitness, int painLevel, boolean graceful, boolean clumsy) {
        float v = 1.0f + weaponExtra;
        v *= 1.0 + painLevel * CommonConfig.STAMINA_PAIN_DRAIN_PER_LEVEL.get() * 4.0;
        if (graceful) v *= 1.0f - CommonConfig.STAMINA_GRACEFUL_REDUCE.get().floatValue();
        if (clumsy) v *= 1.0f + CommonConfig.STAMINA_CLUMSY_INCREASE.get().floatValue();
        v *= consumptionModifier(fitness);
        return Math.max(0.05f, v);
    }

    /** 疼痛每级消耗加成（配置值按 4 级=25% 折算）。 */
    public static float painDrainMultiplier(int painLevel) {
        return 1.0f + painLevel * CommonConfig.STAMINA_PAIN_DRAIN_PER_LEVEL.get().floatValue() * 4.0f;
    }

    /** 恢复速度倍率：体格越高恢复越快（10 级翻倍），原地静止再 +50%。 */
    public static float regenMultiplier(int fitness, boolean idle) {
        float v = 1.0f + Math.max(0, Math.min(10, fitness)) * 0.10f;
        if (idle) v *= 1.0f + CommonConfig.STAMINA_IDLE_REGEN_BONUS.get().floatValue();
        return v;
    }

    /** 每 tick 恢复量（走步与静止共用同一个基准，静止时再乘 idle 加成，最后乘全局倍率）。 */
    public static float regenPerTick(int fitness, boolean idle) {
        return CommonConfig.STAMINA_REGEN_PER_SECOND.get().floatValue() / 20.0f * regenMultiplier(fitness, idle)
                * CommonConfig.STAMINA_REGEN_GLOBAL.get().floatValue();
    }

    /** 体力百分比（0-100）。 */
    public static float percent(float current, float cap) {
        return cap <= 0f ? 100f : Math.max(0f, Math.min(100f, current / cap * 100f));
    }

    /**
     * 图标等级：体力 80% 以上不显示图标；往下降每 20% 升一级，耗尽为 4 级。
     * 分级比原来更细（80/60/40/20），图标强度跟着体力循序渐进地变化。
     */
    public static int iconLevel(float percent) {
        if (percent >= 80f) return 0;
        if (percent >= 60f) return 1;
        if (percent >= 40f) return 2;
        if (percent >= 20f) return 3;
        return 4;
    }

    /**
     * 奔跑限速：体力 ≥50% 全速；50%→20% 之间线性掉到走步速度；
     * 低于 20% 完全跑不起来（等同走步）。
     */
    public static double sprintSpeedMultiplier(float percent) {
        if (percent >= 50f) return 1.0;
        if (percent >= 20f) return 0.25 + (percent - 20.0) / 30.0 * 0.75;
        return 0.25;
    }

    /** 疼痛对移动速度的削减：4 级（满疼痛）-25%，每级 -6.25%。 */
    public static double painSpeedMultiplier(int painLevel) {
        return 1.0 - painLevel * 0.0625;
    }

    private Stamina() {}
}
