package com.pzmoodle.immersion.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 服务端/通用配置（TOML）。所有内部数值阈值都可以在这里调。
 * 玩家永远看不到这些数字——它们只决定图标等级和隐性效果。
 */
public final class CommonConfig {
    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();
    public static final ModConfigSpec SPEC;

    // 通用
    public static final ModConfigSpec.BooleanValue ALLOW_DAY_SLEEP;
    public static final ModConfigSpec.IntValue TICK_INTERVAL;

    // 饥饿 / 吃饱
    public static final ModConfigSpec.IntValue HUNGER_L1, HUNGER_L2, HUNGER_L3;
    /** 饱食度新规则总开关：按世界时间持续消耗、低饱食度渐进惩罚、0 饱食度按周加深。 */
    public static final ModConfigSpec.BooleanValue HUNGER_CONTINUOUS_DRAIN;
    public static final ModConfigSpec.DoubleValue HUNGER_FOOD_PER_DAY;
    public static final ModConfigSpec.BooleanValue HUNGER_DISABLE_VANILLA_DRAIN;
    public static final ModConfigSpec.BooleanValue HUNGER_DISABLE_VANILLA_STARVE;
    public static final ModConfigSpec.DoubleValue HUNGER_PENALTY_START_RATIO;
    public static final ModConfigSpec.DoubleValue HUNGER_PENALTY_AT_ZERO;
    public static final ModConfigSpec.DoubleValue HUNGER_STARVE_WEEK1_PENALTY;
    public static final ModConfigSpec.DoubleValue HUNGER_STARVE_WEEK2_PENALTY;
    public static final ModConfigSpec.DoubleValue HUNGER_STARVE_WEEK3_PENALTY;
    public static final ModConfigSpec.DoubleValue HUNGER_MIN_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue HUNGER_STARVE_RECOVERY_DAYS;
    public static final ModConfigSpec.DoubleValue HUNGER_DEATH_DAYS;

    // 疼痛
    public static final ModConfigSpec.IntValue PAIN_L1, PAIN_L2, PAIN_L3;
    public static final ModConfigSpec.DoubleValue PAIN_MISS_PER_LEVEL;

    // 不开心
    public static final ModConfigSpec.DoubleValue UNHAPPY_LOW_HP_PER_10S;
    public static final ModConfigSpec.DoubleValue UNHAPPY_FOOD_FACTOR;
    public static final ModConfigSpec.DoubleValue UNHAPPY_KILL_REDUCE;
    public static final ModConfigSpec.IntValue UNHAPPY_DRINK_REDUCE;
    public static final ModConfigSpec.IntValue UNHAPPY_STRESS_LINK_THRESHOLD;
    public static final ModConfigSpec.DoubleValue UNHAPPY_STRESS_LINK_PER_10S;
    public static final ModConfigSpec.DoubleValue UNHAPPY_NATURAL_DECAY_10S;
    public static final ModConfigSpec.IntValue STRESS_REQUIRE_UNHAPPY_FULL;
    public static final ModConfigSpec.IntValue XP_PENALTY_FROM_LEVEL;
    public static final ModConfigSpec.DoubleValue XP_PENALTY_PER_LEVEL;

    // 压力
    public static final ModConfigSpec.DoubleValue STRESS_FOOD_FACTOR;
    public static final ModConfigSpec.DoubleValue STRESS_KILL_REDUCE;
    public static final ModConfigSpec.IntValue STRESS_DRINK_REDUCE;
    public static final ModConfigSpec.DoubleValue STRESS_PANIC_PER_10S;
    public static final ModConfigSpec.DoubleValue STRESS_NATURAL_DECAY_10S;

    // 无聊
    public static final ModConfigSpec.IntValue BORED_IDLE_SECONDS;
    public static final ModConfigSpec.IntValue BORED_INTERVAL_SECONDS;
    public static final ModConfigSpec.IntValue BORED_PER_INTERVAL;
    public static final ModConfigSpec.IntValue BORED_ACTION_REDUCE;
    public static final ModConfigSpec.IntValue BORED_CONVERT_RESET;
    public static final ModConfigSpec.IntValue BORED_CONVERT_UNHAPPY;
    public static final ModConfigSpec.IntValue BORED_CONVERT_STRESS;

    // 恐慌
    public static final ModConfigSpec.IntValue PANIC_RADIUS;
    public static final ModConfigSpec.IntValue PANIC_MIN_ZOMBIES;
    public static final ModConfigSpec.IntValue PANIC_PER_ZOMBIE_10S;
    public static final ModConfigSpec.IntValue PANIC_DECAY_10S;
    public static final ModConfigSpec.DoubleValue PANIC_ATTACK_REDUCE_PER_LEVEL;
    public static final ModConfigSpec.DoubleValue PANIC_MISS_PER_LEVEL;
    public static final ModConfigSpec.IntValue KILLS_FEARLESS;
    public static final ModConfigSpec.IntValue KILLS_NUMB;

    // 湿漉漉
    public static final ModConfigSpec.IntValue WET_LINGER_SECONDS;

    // 累了
    public static final ModConfigSpec.IntValue TIRED_PER_DAY;
    public static final ModConfigSpec.IntValue TIRED_REST_PER_10S;
    public static final ModConfigSpec.IntValue TIRED_SLEEP_PER_10S;
    public static final ModConfigSpec.DoubleValue TIRED_SPEED_PER_LEVEL;

    // 职业与特质
    public static final ModConfigSpec.IntValue BASE_TRAIT_POINTS;
    public static final ModConfigSpec.BooleanValue ALLOW_RECHOOSE;

    // 人物数值（僵毁式属性 0-10，普通人基准 5）
    public static final ModConfigSpec.DoubleValue STRENGTH_ATTACK_PER_POINT;
    public static final ModConfigSpec.DoubleValue STRENGTH_MINE_PER_POINT;
    public static final ModConfigSpec.DoubleValue FITNESS_HEALTH_PER_POINT;
    public static final ModConfigSpec.DoubleValue SPRINTING_SPEED_PER_POINT;
    public static final ModConfigSpec.DoubleValue NIMBLE_ATTACK_SPEED_PER_POINT;
    public static final ModConfigSpec.DoubleValue LIGHTFOOTED_SNEAK_PER_POINT;
    public static final ModConfigSpec.DoubleValue AIMING_PROJECTILE_PER_POINT;

    // 体力与负重
    public static final ModConfigSpec.DoubleValue STAMINA_SPRINT_DRAIN_PER_SECOND;
    public static final ModConfigSpec.DoubleValue STAMINA_REGEN_PER_SECOND;
    public static final ModConfigSpec.DoubleValue STAMINA_PAIN_DRAIN_PER_LEVEL;
    public static final ModConfigSpec.DoubleValue STAMINA_GRACEFUL_REDUCE;
    public static final ModConfigSpec.DoubleValue STAMINA_CLUMSY_INCREASE;
    public static final ModConfigSpec.DoubleValue STAMINA_IDLE_REGEN_BONUS;
    public static final ModConfigSpec.DoubleValue STAMINA_REGEN_GLOBAL;
    public static final ModConfigSpec.DoubleValue STAMINA_SLEEP_REGEN_BONUS;

    // 联动
    public static final ModConfigSpec.BooleanValue COMPAT_THIRST;
    public static final ModConfigSpec.BooleanValue COMPAT_TAVERN;
    public static final ModConfigSpec.BooleanValue COMPAT_COLD_SWEAT;
    public static final ModConfigSpec.IntValue COLD_THRESHOLD;
    public static final ModConfigSpec.IntValue HOT_THRESHOLD;

    // 酒馆饮酒
    public static final ModConfigSpec.IntValue TAVERN_DRINK_UNHAPPY;
    public static final ModConfigSpec.IntValue TAVERN_DRINK_STRESS;
    public static final ModConfigSpec.IntValue SMOKER_QUIT_DAYS;
    public static final ModConfigSpec.IntValue DRUNKARD_QUIT_DAYS;
    public static final ModConfigSpec.IntValue DRUNKARD_STRESS_PER_DAY;

    static {
        B.comment("僵毁状态沉浸 - 通用设置").push("general");
        ALLOW_DAY_SLEEP = B.comment("允许白天睡觉（取消原版夜晚限制）").define("allowDaySleep", true);
        TICK_INTERVAL = B.comment("内部数值结算间隔（tick），越大越省性能，默认 20 = 每秒一次").defineInRange("tickInterval", 20, 5, 100);
        B.pop();

        B.comment("饥饿与吃饱").push("hunger");
        HUNGER_L1 = B.comment("饱食度低于等于此值显示等级1").defineInRange("level1", 15, 1, 20);
        HUNGER_L2 = B.defineInRange("level2", 10, 1, 20);
        HUNGER_L3 = B.defineInRange("level3", 5, 1, 20);
        HUNGER_CONTINUOUS_DRAIN = B.comment("总开关：饱食度按游戏内时间持续下降（站着不动也降，睡觉跳到早上按跳过的时间补扣），"
                + "并启用低饱食度渐进惩罚与0饱食度按周加深的惩罚").define("continuousDrain", true);
        HUNGER_FOOD_PER_DAY = B.comment("一整天（24000刻）不动消耗掉的饱食度点数；20=一整管刚好耗一天；食欲旺盛特质在此基础上×1.4")
                .defineInRange("foodPerDay", 20.0, 0, 100);
        HUNGER_DISABLE_VANILLA_DRAIN = B.comment("关闭原版奔跑/跳跃/游泳/挨打等动作性饥饿消耗，并同时放开「饱食度≤6不能起跑」的原版限制"
                + "（低饱食度改为按比例降低奔跑/走步/潜行速度，仍可奔跑）").define("disableVanillaDrain", true);
        HUNGER_DISABLE_VANILLA_STARVE = B.comment("0饱食度时不再按原版规则立即扣血，改由渐进惩罚与三周后的缓慢掉血接管").define("disableVanillaStarveDamage", true);
        HUNGER_PENALTY_START_RATIO = B.comment("饱食度低于此比例（占20格）开始出现渐进惩罚；默认30%").defineInRange("penaltyStartRatio", 0.30, 0, 0.95);
        HUNGER_PENALTY_AT_ZERO = B.comment("刚降到0饱食度时各项数值（走/跑/潜行速度、跳跃、攻击力、攻速）的降低比例；0.5=减半")
                .defineInRange("penaltyAtZero", 0.50, 0, 0.95);
        HUNGER_STARVE_WEEK1_PENALTY = B.comment("0饱食度持续1游戏周后的降低比例（线性过渡，1.0=完全无法发挥）").defineInRange("starveWeek1Penalty", 1.0, 0, 4);
        HUNGER_STARVE_WEEK2_PENALTY = B.comment("0饱食度持续2游戏周后的降低比例").defineInRange("starveWeek2Penalty", 2.0, 0, 4);
        HUNGER_STARVE_WEEK3_PENALTY = B.comment("0饱食度持续3游戏周后的降低比例（达到后立即开始缓慢掉生命；"
                + "这也是惩罚深度的上限，三周后不再继续加深）").defineInRange("starveWeek3Penalty", 3.0, 0, 6);
        HUNGER_MIN_MULTIPLIER = B.comment("惩罚后数值最低保留的比例（防止完全卡死），0.05=只剩5%").defineInRange("minMultiplier", 0.05, 0.01, 1);
        HUNGER_STARVE_RECOVERY_DAYS = B.comment("极度饥饿（0饱食度满3周、已开始掉血那一段）的恢复基准："
                + "体格0级把惩罚完全恢复所需的游戏天数；体格每高5级恢复时间减半"
                + "（默认2天：体格0=2天、体格5=1天、体格10=半天，体格10以上不再加速）。"
                + "只要饱食度回到0以上就会开始恢复，满饱食度/创造模式也不会卡住；"
                + "惩罚深度封顶在3周档，所以恢复时间不会因为饿得更久而膨胀")
                .defineInRange("starveRecoveryDaysAtFitness0", 2.0, 0.25, 30);
        HUNGER_DEATH_DAYS = B.comment("0饱食度满3周后，生命从满到耗尽所需的游戏天数").defineInRange("starveDeathDays", 7.0, 0.5, 60);
        B.pop();

        B.comment("疼痛（按缺失生命百分比分档）").push("pain");
        PAIN_L1 = B.comment("缺失 <25% 为等级1").defineInRange("level1Percent", 25, 1, 99);
        PAIN_L2 = B.defineInRange("level2Percent", 50, 1, 99);
        PAIN_L3 = B.defineInRange("level3Percent", 75, 1, 99);
        PAIN_MISS_PER_LEVEL = B.comment("每级降低多少攻击命中率").defineInRange("missChancePerLevel", 0.10, 0, 0.5);
        B.pop();

        B.comment("不开心（隐藏值 0-100，每 20 点一级）").push("unhappy");
        UNHAPPY_LOW_HP_PER_10S = B.comment("受伤时每 10 秒减少的不开心值；受伤不再增加压力").defineInRange("lowHpPer10s", 0.08, 0, 20);
        UNHAPPY_FOOD_FACTOR = B.comment("吃食物减少量 = 饱食度恢复 x 系数").defineInRange("foodFactor", 2.0, 0, 5);
        UNHAPPY_KILL_REDUCE = B.defineInRange("killReduce", 1.5, 0, 10);
        UNHAPPY_DRINK_REDUCE = B.defineInRange("drinkReduce", 15, 0, 100);
        UNHAPPY_STRESS_LINK_THRESHOLD = B.comment("压力高于此值才会反过来加重不开心").defineInRange("stressLinkThreshold", 75, 0, 100);
        UNHAPPY_STRESS_LINK_PER_10S = B.defineInRange("stressLinkPer10s", 0.10, 0, 20);
        UNHAPPY_NATURAL_DECAY_10S = B.comment("没有压力来源时，每 10 秒自然回落多少不开心").defineInRange("naturalDecayPer10s", 1.0, 0, 20);
        STRESS_REQUIRE_UNHAPPY_FULL = B.comment("不开心达到多少后压力才允许增长；默认 100 = 必须满格").defineInRange("stressRequireUnhappyFull", 100, 0, 100);
        XP_PENALTY_FROM_LEVEL = B.comment("不开心达到几级开始降低打怪经验").defineInRange("xpPenaltyFromLevel", 3, 1, 4);
        XP_PENALTY_PER_LEVEL = B.defineInRange("xpPenaltyPerLevel", 0.10, 0, 0.9);
        B.pop();

        B.comment("压力（隐藏值 0-100，每 20 点一级）").push("stress");
        STRESS_FOOD_FACTOR = B.comment("吃食物减少量 = 饱食度恢复 x 系数").defineInRange("foodFactor", 1.2, 0, 5);
        STRESS_KILL_REDUCE = B.defineInRange("killReduce", 1.2, 0, 10);
        STRESS_DRINK_REDUCE = B.defineInRange("drinkReduce", 20, 0, 100);
        STRESS_PANIC_PER_10S = B.comment("恐慌持续时每 10 秒增加").defineInRange("panicPer10s", 0.25, 0, 20);
        STRESS_NATURAL_DECAY_10S = B.comment("没有恐慌/受伤时，每 10 秒自然回落多少压力").defineInRange("naturalDecayPer10s", 1.2, 0, 20);
        B.pop();

        B.comment("无聊").push("bored");
        BORED_IDLE_SECONDS = B.comment("连续多少秒无动作开始累积").defineInRange("idleSeconds", 60, 5, 6000);
        BORED_INTERVAL_SECONDS = B.defineInRange("intervalSeconds", 30, 5, 600);
        BORED_PER_INTERVAL = B.defineInRange("perInterval", 6, 0, 100);
        BORED_ACTION_REDUCE = B.comment("移动/攻击/交互一次减少").defineInRange("actionReduce", 5, 0, 100);
        BORED_CONVERT_RESET = B.comment("满 100 后转化并重置为此值").defineInRange("convertReset", 90, 0, 100);
        BORED_CONVERT_UNHAPPY = B.defineInRange("convertUnhappy", 2, 0, 100);
        BORED_CONVERT_STRESS = B.defineInRange("convertStress", 2, 0, 100);
        B.pop();

        B.comment("恐慌").push("panic");
        PANIC_RADIUS = B.defineInRange("radius", 8, 1, 32);
        PANIC_MIN_ZOMBIES = B.comment("附近僵尸达到几只开始累积").defineInRange("minZombies", 3, 1, 64);
        PANIC_PER_ZOMBIE_10S = B.comment("每只僵尸每 10 秒增加").defineInRange("perZombiePer10s", 2, 0, 50);
        PANIC_DECAY_10S = B.defineInRange("decayPer10s", 5, 0, 50);
        PANIC_ATTACK_REDUCE_PER_LEVEL = B.defineInRange("attackReducePerLevel", 0.15, 0, 0.9);
        PANIC_MISS_PER_LEVEL = B.defineInRange("missChancePerLevel", 0.15, 0, 0.9);
        KILLS_FEARLESS = B.comment("击杀多少僵尸获得「无所畏惧」").defineInRange("fearlessKills", 10000, 1, 10000000);
        KILLS_NUMB = B.comment("击杀多少僵尸获得「麻木不仁」").defineInRange("numbKills", 100000, 1, 10000000);
        B.pop();

        B.comment("湿漉漉").push("wet");
        WET_LINGER_SECONDS = B.comment("离开水/雨后图标再持续多少秒").defineInRange("lingerSeconds", 30, 1, 600);
        B.pop();

        B.comment("累了").push("tired");
        TIRED_PER_DAY = B.comment("每过一游戏日未睡觉增加的疲惫值").defineInRange("perDay", 20, 1, 100);
        TIRED_REST_PER_10S = B.comment("安全地点静止休息每 10 秒减少").defineInRange("restPer10s", 1, 0, 100);
        TIRED_SLEEP_PER_10S = B.comment("躺床上每 10 秒减少").defineInRange("sleepPer10s", 10, 0, 100);
        TIRED_SPEED_PER_LEVEL = B.comment("每级降低移动/挖掘速度").defineInRange("speedPerLevel", 0.10, 0, 0.5);
        B.pop();

        B.comment("职业与特质").push("occupation");
        BASE_TRAIT_POINTS = B.comment("世界默认特质点数基数（OP 也可用 /pzmi defaultpoints 修改）").defineInRange("baseTraitPoints", 0, -100, 100);
        ALLOW_RECHOOSE = B.comment("是否允许在确认之后重新选择职业与特质（默认关闭：确认后锁定，死亡重生才会解锁）").define("allowRechoose", false);
        B.pop();

        B.comment("人物数值：僵毁式属性 0-10，普通人基准 5，每高出基准 1 点获得的加成")
                .push("attributes");
        STRENGTH_ATTACK_PER_POINT = B.comment("力量：实际攻击曲线为 0级50%、5级100%、10级200%，此项保留兼容旧配置").defineInRange("strengthAttackPerPoint", 0.20, 0, 1.0);
        STRENGTH_MINE_PER_POINT = B.comment("力量：每点挖掘速度加成").defineInRange("strengthMinePerPoint", 0.04, 0, 1.0);
        FITNESS_HEALTH_PER_POINT = B.comment("体格：实际生命曲线为 0级50%、5级100%、10级200%，此项保留兼容旧配置").defineInRange("fitnessHealthPerPoint", 0.20, 0, 1.0);
        SPRINTING_SPEED_PER_POINT = B.comment("冲刺：实际为 0级0%、每级5%、10级50%，此项保留兼容旧配置").defineInRange("sprintingSpeedPerPoint", 0.05, 0, 1.0);
        NIMBLE_ATTACK_SPEED_PER_POINT = B.comment("灵活：每点攻击速度加成").defineInRange("nimbleAttackSpeedPerPoint", 0.03, 0, 1.0);
        LIGHTFOOTED_SNEAK_PER_POINT = B.comment("轻巧：每点潜行移动速度加成").defineInRange("lightfootedSneakPerPoint", 0.05, 0, 1.0);
        AIMING_PROJECTILE_PER_POINT = B.comment("瞄准：每点弓箭/弩伤害加成").defineInRange("aimingProjectilePerPoint", 0.05, 0, 1.0);
        B.pop();

        B.comment("体力与负重：奔跑约 500 米耗尽，体格和特质会改变消耗").push("stamina");
        STAMINA_SPRINT_DRAIN_PER_SECOND = B.comment("普通体格奔跑每秒消耗的体力点").defineInRange("sprintDrainPerSecond", 1.00, 0.01, 10.0);
        STAMINA_REGEN_PER_SECOND = B.comment("体力每秒基础恢复量；走步也会恢复").defineInRange("regenPerSecond", 0.18, 0.01, 10.0);
        STAMINA_PAIN_DRAIN_PER_LEVEL = B.comment("疼痛每级额外消耗比例；4级合计25%").defineInRange("painDrainPerLevel", 0.0625, 0.0, 1.0);
        STAMINA_GRACEFUL_REDUCE = B.comment("身轻如燕减少体力消耗").defineInRange("gracefulReduce", 0.15, 0.0, 1.0);
        STAMINA_CLUMSY_INCREASE = B.comment("体笨如牛增加体力消耗").defineInRange("clumsyIncrease", 0.15, 0.0, 1.0);
        STAMINA_IDLE_REGEN_BONUS = B.comment("原地不动的额外恢复比例").defineInRange("idleRegenBonus", 0.50, 0.0, 3.0);
        STAMINA_REGEN_GLOBAL = B.comment("体力恢复速度全局倍率（1.15 = 整体快 15%）").defineInRange("regenGlobalMultiplier", 1.15, 0.01, 10.0);
        STAMINA_SLEEP_REGEN_BONUS = B.comment("躺床上睡觉的额外体力恢复（2.0 = 恢复速度 +200%，即 3 倍）").defineInRange("sleepRegenBonus", 2.0, 0.0, 10.0);
        B.pop();

        B.comment("多模组联动（均为可选依赖，未安装自动跳过）").push("compat");
        COMPAT_THIRST = B.comment("联动 Thirst Was Reclaimed（口渴）").define("thirst", true);
        COMPAT_TAVERN = B.comment("联动 森罗物语：酒馆（饮酒）").define("tavern", true);
        COMPAT_COLD_SWEAT = B.comment("联动 冷汗 Cold Sweat（体温）").define("coldSweat", true);
        COLD_THRESHOLD = B.comment("体温低于此值显示低温图标").defineInRange("coldAt", -50, -1000, 1000);
        HOT_THRESHOLD = B.comment("体温高于此值显示高温图标").defineInRange("hotAt", 50, -1000, 1000);
        TAVERN_DRINK_UNHAPPY = B.comment("喝下森罗物语酒馆的酒，立即减少的不开心值（大幅解压）").defineInRange("tavernDrinkUnhappy", 60, 0, 100);
        TAVERN_DRINK_STRESS = B.comment("喝下森罗物语酒馆的酒，立即减少的压力值（大幅解压）").defineInRange("tavernDrinkStress", 70, 0, 100);
        SMOKER_QUIT_DAYS = B.comment("连续多少个游戏日不抽烟后移除烟瘾特质").defineInRange("smokerQuitDays", 30, 1, 10000);
        DRUNKARD_QUIT_DAYS = B.comment("连续多少个游戏日不喝酒后移除嗜酒如命特质").defineInRange("drunkardQuitDays", 60, 1, 10000);
        DRUNKARD_STRESS_PER_DAY = B.comment("嗜酒如命在未喝酒期间每天增加的压力").defineInRange("drunkardStressPerDay", 15, 0, 100);
        B.pop();

        SPEC = B.build();
    }

    private CommonConfig() {}
}
