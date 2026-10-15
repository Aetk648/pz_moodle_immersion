package com.pzmoodle.immersion.moodle;

import com.pzmoodle.immersion.compat.Compat;
import com.pzmoodle.immersion.config.CommonConfig;
import com.pzmoodle.immersion.net.Packets;
import com.pzmoodle.immersion.trait.OccupationTrait;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * 服务端数值推进：把隐藏的不开心/压力/无聊/恐慌/疲惫按规则累积，
 * 并处理联动模组（口渴/醉酒/体温）的等级映射。
 * 内部按 tick 分摊「每 10 秒」类规则，无需额外定时器。
 */
public final class MoodleTicker {

    /** 玩家动作（移动/攻击/交互/吃东西）时调用：重置无聊计时并减少无聊值。 */
    public static void onAction(Player player) {
        MoodleData data = MoodleAttachments.get(player);
        data.idleTicks = 0;
        data.boredAccumTicks = 0;
        data.addBored(-CommonConfig.BORED_ACTION_REDUCE.get());
    }

    /**
     * 吃食物后：减少不开心与压力（按饱食度恢复量）。
     * 不再一次性扣满，先进缓释池，之后十几秒里一点点生效，曲线平滑。
     */
    public static void onEat(Player player, int nutrition) {
        MoodleData data = MoodleAttachments.get(player);
        data.queueUnhappyRelief((float) (nutrition * CommonConfig.UNHAPPY_FOOD_FACTOR.get()));
        data.queueStressRelief((float) (nutrition * CommonConfig.STRESS_FOOD_FACTOR.get()));
        onAction(player);
    }

    /** 喝酒后：减少不开心与压力（先进缓释池，避免一格跳十几二十点）。 */
    public static void onDrink(Player player) {
        MoodleData data = MoodleAttachments.get(player);
        data.queueUnhappyRelief(CommonConfig.UNHAPPY_DRINK_REDUCE.get().floatValue());
        data.queueStressRelief(CommonConfig.STRESS_DRINK_REDUCE.get().floatValue());
        onAction(player);
    }

    /**
     * 喝下森罗物语：酒馆的酒。酒馆的酒是「消遣」，一整杯能压掉一大截压力与不开心
     * （数值见 compat.tavernDrink*）——但现在是先进缓释池，十几秒内逐步松开，
     * 而不是一瞬间掉一大格。
     */
    public static void onTavernDrink(Player player) {
        MoodleData data = MoodleAttachments.get(player);
        data.queueUnhappyRelief(CommonConfig.TAVERN_DRINK_UNHAPPY.get().floatValue());
        data.queueStressRelief(CommonConfig.TAVERN_DRINK_STRESS.get().floatValue());
        // 酒本身也算一次「找点事做」
        onAction(player);
    }

    /** 击杀僵尸后：减少不开心与压力，累加击杀数，检查里程碑特质。 */
    public static void onZombieKill(Player player) {
        MoodleData data = MoodleAttachments.get(player);
        data.zombieKills++;
        data.addUnhappy((float) (-CommonConfig.UNHAPPY_KILL_REDUCE.get()));
        data.addStress((float) (-CommonConfig.STRESS_KILL_REDUCE.get()));
        onAction(player);
        if (data.zombieKills >= CommonConfig.KILLS_FEARLESS.get()) data.traits.add(OccupationTrait.FEARLESS);
        if (data.zombieKills >= CommonConfig.KILLS_NUMB.get()) data.traits.add(OccupationTrait.NUMB);
    }

    /** 湿漉漉：进入水/淋雨时刷新剩余时间。 */
    public static void refreshWet(Player player) {
        MoodleData data = MoodleAttachments.get(player);
        data.wetTicks = CommonConfig.WET_LINGER_SECONDS.get() * 20;
    }

    /**
     * 每个结算 tick 调用一次（服务端，由事件层按 TICK_INTERVAL 节流）。
     * delta = 本次结算覆盖的 tick 数。
     */
    public static void tick(ServerPlayer player, int delta) {
        MoodleData data = MoodleAttachments.get(player);
        if (OccupationTrait.has(data, OccupationTrait.NUMB)) {
            data.stress = 0f;
            data.panic = 0f;
        }
        Level level = player.level();

        // 湿漉漉计时递减
        if (data.wetTicks > 0) data.wetTicks -= delta;
        if (player.isInWater() || level.isRainingAt(player.blockPosition())) {
            refreshWet(player);
        }

        // 移动即动作（用服务端真实位移判断；deltaMovement 在 1.21.1 服务端对玩家恒为 0）
        if (MovementProbe.movedRecently(data) && player.onGround()) {
            onAction(player);
        }

        // 无聊：连续无动作后开始累积。原来每 30 秒整加 6 点（一格一格地跳），
        // 现在改成每 tick 按同一速率连续累积，曲线循序渐进。
        data.idleTicks += delta;
        if (data.idleTicks >= CommonConfig.BORED_IDLE_SECONDS.get() * 20) {
            float perTick = CommonConfig.BORED_PER_INTERVAL.get()
                    / (float) (CommonConfig.BORED_INTERVAL_SECONDS.get() * 20);
            data.addBored(perTick * delta);
        } else {
            data.boredAccumTicks = 0;
        }

        // 无聊满级：每 10 秒先转化为不开心；压力由 addStress 自己把关，不开心没满就一点不加
        if (data.bored >= 100f) {
            data.convertTicks += delta;
            while (data.convertTicks >= 200) {
                data.convertTicks -= 200;
                data.addUnhappy(CommonConfig.BORED_CONVERT_UNHAPPY.get());
                data.addStress(CommonConfig.BORED_CONVERT_STRESS.get());
                data.bored = CommonConfig.BORED_CONVERT_RESET.get();
            }
        } else {
            data.convertTicks = 0;
        }

        // 恐慌：附近僵尸数量驱动
        int zombies = countNearbyZombies(player);
        boolean panicking = zombies >= CommonConfig.PANIC_MIN_ZOMBIES.get();
        if (!OccupationTrait.has(data, OccupationTrait.NUMB)) {
            if (panicking) {
                float perTick = zombies * CommonConfig.PANIC_PER_ZOMBIE_10S.get() / 200f;
                data.addPanic(perTick * delta * OccupationTrait.panicMultiplier(data));
            } else {
                data.addPanic(-CommonConfig.PANIC_DECAY_10S.get() / 200f * delta);
            }
        } else {
            data.panic = 0f;
        }

        boolean hurt = player.getHealth() < player.getMaxHealth();

        // 压力：只有不开心的隐藏值积满后才会增长（见 MoodleData.addStress）；
        // 受伤本身不再增加压力，没有来源时仍然缓慢自然平复。
        if (!OccupationTrait.has(data, OccupationTrait.NUMB) && data.panic >= 20f) {
            data.addStress((float) (CommonConfig.STRESS_PANIC_PER_10S.get() / 200.0 * delta));
        } else if (!OccupationTrait.has(data, OccupationTrait.NUMB) && !hurt) {
            data.addStress((float) (-CommonConfig.STRESS_NATURAL_DECAY_10S.get() / 200.0 * delta));
        }

        // 不开心：受伤会持续减少不开心；压力值在受伤期间保持不变，不会被伤害降低。
        // 不开心的主要来源是无聊满级后的转化（见上面的 convertTicks 分支）。
        if (hurt) {
            data.addUnhappy((float) (-CommonConfig.UNHAPPY_LOW_HP_PER_10S.get() / 200.0 * delta));
        } else {
            data.addUnhappy((float) (-CommonConfig.UNHAPPY_NATURAL_DECAY_10S.get() / 200.0 * delta));
        }

        // 缓释池：吃东西/喝酒攒下的减压量在这里按秒平滑落地（循序渐进，不是一格跳 25%）
        data.tickRelief(delta);

        // 疲惫与过天：按世界时钟的真实推进量累积。
        // 原来「换天瞬间 +20」是一下跳一大格，现在把每一天摊到时间轴上匀速累积；
        // 睡过夜把时间快进的话，快进的时钟同样计入总量，不会丢也永远不会爆跳。
        long dayTime = level.getDayTime();
        if (data.lastDayTime < 0) data.lastDayTime = dayTime;
        long elapsed = Math.max(0L, dayTime - data.lastDayTime);
        // 饱食度系统同样按世界时间推进：站着不动也消耗、睡觉跳过的时间一并补扣、
        // 0 饱食度按周加深惩罚（细节见 HungerSystem）。
        HungerSystem.tick(player, data, elapsed);
        if (elapsed > 0) {
            data.lastDayTime = dayTime;
            data.addFatigue(CommonConfig.TIRED_PER_DAY.get() * OccupationTrait.fatigueMultiplier(data)
                    * Math.min(elapsed, 24000L) / 24000f);
            data.dayClockTicks += Math.min(elapsed, 48000L);
            boolean traitsChanged = false;
            while (data.dayClockTicks >= 24000L) {
                data.dayClockTicks -= 24000L;
                data.daysAlive++;
                data.daysSinceSmoke++;
                data.daysSinceDrink++;

                if (OccupationTrait.has(data, OccupationTrait.SMOKER)) {
                    if (data.daysSinceSmoke > 0) data.addStress((float) com.pzmoodle.immersion.admin.DefOverrides
                            .mechanic("trait", OccupationTrait.SMOKER, "stress_per_day", 10.0));
                    if (data.daysSinceSmoke >= CommonConfig.SMOKER_QUIT_DAYS.get()) {
                        data.traits.remove(OccupationTrait.SMOKER);
                        traitsChanged = true;
                        player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                                "message.pz_moodle_immersion.smoker_quit"));
                    }
                }
                if (OccupationTrait.has(data, OccupationTrait.DRUNKARD)) {
                    if (data.daysSinceDrink > 0) data.addStress(CommonConfig.DRUNKARD_STRESS_PER_DAY.get());
                    if (data.daysSinceDrink >= CommonConfig.DRUNKARD_QUIT_DAYS.get()) {
                        data.traits.remove(OccupationTrait.DRUNKARD);
                        traitsChanged = true;
                        player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                                "message.pz_moodle_immersion.drunkard_quit"));
                    }
                }
            }
            if (traitsChanged) {
                Packets.sendSync(player);
            }
        }
        if (player.isSleeping()) {
            data.addFatigue(-CommonConfig.TIRED_SLEEP_PER_10S.get() / 200f * delta);
        } else if (data.idleTicks >= 200) {
            data.addFatigue(-CommonConfig.TIRED_REST_PER_10S.get() / 200f * delta);
        }

        // 猫眼/夜猫子：暗处或夜晚自动获得隐藏的夜视效果，时长足够长，避免一闪一闪
        boolean catEyes = OccupationTrait.has(data, OccupationTrait.CAT_EYES)
                && level.getMaxLocalRawBrightness(player.blockPosition()) <= 3;
        boolean nightOwl = OccupationTrait.has(data, OccupationTrait.NIGHT_OWL) && level.isNight();
        if (catEyes || nightOwl) {
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.NIGHT_VISION, 600, 0, true, false, false));
        }

        // 联动：口渴
        if (CommonConfig.COMPAT_THIRST.get()) {
            int thirst = Compat.thirstValue(player);
            if (thirst >= 0) {
                data.displayLevels[Moodle.index(Moodle.THIRST)] =
                        thirst <= 0 ? 4 : thirst <= 5 ? 3 : thirst <= 10 ? 2 : thirst <= 15 ? 1 : 0;
            }
        }

        // 联动：醉酒
        if (CommonConfig.COMPAT_TAVERN.get()) {
            int tipsy = Compat.tipsyLevel(player);
            data.displayLevels[Moodle.index(Moodle.DRUNK)] = tipsy;
            if (tipsy > 0) data.daysSinceDrink = 0;
        }

        // 联动：体温
        if (CommonConfig.COMPAT_COLD_SWEAT.get()) {
            double temp = Compat.bodyTemperature(player);
            if (!Double.isNaN(temp)) {
                data.displayLevels[Moodle.index(Moodle.COLD)] =
                        temp <= CommonConfig.COLD_THRESHOLD.get() ? tempLevel(temp - CommonConfig.COLD_THRESHOLD.get()) : 0;
                data.displayLevels[Moodle.index(Moodle.HOT)] =
                        temp >= CommonConfig.HOT_THRESHOLD.get() ? tempLevel(temp - CommonConfig.HOT_THRESHOLD.get()) : 0;
            }
        }

        // 流血：兼容任意带 bleed 字样的效果模组
        data.displayLevels[Moodle.index(Moodle.BLEEDING)] = bleedingLevel(player);

        if (OccupationTrait.has(data, OccupationTrait.NUMB)) {
            data.stress = 0f;
            data.panic = 0f;
        }
        data.clamp();
    }

    private static int tempLevel(double over) {
        return Math.min(4, 1 + (int) (Math.abs(over) / 25));
    }

    private static int bleedingLevel(Player player) {
        for (var inst : player.getActiveEffects()) {
            var holder = inst.getEffect();
            var key = holder.getRegisteredName();
            if (key.contains("bleed")) return Math.min(4, inst.getAmplifier() + 1);
        }
        return 0;
    }

    private static int countNearbyZombies(ServerPlayer player) {
        int radius = CommonConfig.PANIC_RADIUS.get();
        AABB box = player.getBoundingBox().inflate(radius);
        int count = 0;
        for (Entity e : player.level().getEntities(player, box,
                ent -> ent instanceof LivingEntity le && le.isAlive() && le.getType().is(EntityTypeTags.ZOMBIES))) {
            count++;
        }
        return count;
    }

    private MoodleTicker() {}
}
