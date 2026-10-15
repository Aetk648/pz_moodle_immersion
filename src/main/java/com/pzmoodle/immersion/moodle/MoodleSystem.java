package com.pzmoodle.immersion.moodle;

import com.pzmoodle.immersion.PZMoodleImmersion;
import com.pzmoodle.immersion.admin.DefOverrides;
import com.pzmoodle.immersion.compat.Compat;
import com.pzmoodle.immersion.config.CommonConfig;
import com.pzmoodle.immersion.trait.Occupation;
import com.pzmoodle.immersion.trait.OccupationTrait;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.food.FoodData;

/**
 * 把隐藏数值换算成 HUD 等级，并施加对应的原版效果。
 * 等级只反映在图标上，玩家看不到任何数字。
 */
public final class MoodleSystem {
    public static final ResourceLocation TIRED_MOVE = PZMoodleImmersion.id("tired_move");
    public static final ResourceLocation TIRED_MINE = PZMoodleImmersion.id("tired_mine");
    public static final ResourceLocation FRAIL_HEALTH = PZMoodleImmersion.id("frail_health");
    public static final ResourceLocation NIGHT_OWL_MOVE = PZMoodleImmersion.id("night_owl_move");
    public static final ResourceLocation OCC_ATTACK = PZMoodleImmersion.id("occ_attack");
    public static final ResourceLocation OCC_MINE = PZMoodleImmersion.id("occ_mine");
    public static final ResourceLocation OCC_HEALTH = PZMoodleImmersion.id("occ_health");
    public static final ResourceLocation OCC_SPEED = PZMoodleImmersion.id("occ_speed");
    public static final ResourceLocation OCC_ATTACK_SPEED = PZMoodleImmersion.id("occ_attack_speed");
    public static final ResourceLocation OCC_SNEAK = PZMoodleImmersion.id("occ_sneak");
    public static final ResourceLocation LIGHTFOOTED_JUMP = PZMoodleImmersion.id("lightfooted_jump");
    public static final ResourceLocation AXEMAN_ATTACK_SPEED = PZMoodleImmersion.id("axeman_attack_speed");
    /** 拳击手：空手时的攻击力与攻速强化。 */
    public static final ResourceLocation BOXER_UNARMED_DAMAGE = PZMoodleImmersion.id("boxer_unarmed_damage");
    public static final ResourceLocation BOXER_UNARMED_SPEED = PZMoodleImmersion.id("boxer_unarmed_speed");

    /** 计算所有 Moodle 的显示等级，写回 data.displayLevels。 */
    public static void computeLevels(Player player, MoodleData data) {
        int[] lv = data.displayLevels;
        java.util.Arrays.fill(lv, 0);

        // 饥饿（原版饱食度 0-20）
        FoodData food = player.getFoodData();
        int sat = food.getFoodLevel();
        if (sat <= 0) lv[Moodle.index(Moodle.HUNGER)] = 4;
        else if (sat <= CommonConfig.HUNGER_L3.get()) lv[Moodle.index(Moodle.HUNGER)] = 3;
        else if (sat <= CommonConfig.HUNGER_L2.get()) lv[Moodle.index(Moodle.HUNGER)] = 2;
        else if (sat <= CommonConfig.HUNGER_L1.get()) lv[Moodle.index(Moodle.HUNGER)] = 1;

        // 吃饱：饱食度接近满且有饱和度
        if (sat >= 18 && food.getSaturationLevel() > 0f) lv[Moodle.index(Moodle.FULL)] = 1;

        // 疼痛：按缺失生命比例（<25% 一级，<50% 二级，<75% 三级，≥75% 四级）
        float max = Math.max(1f, player.getMaxHealth());
        float missingPct = (1f - player.getHealth() / max) * 100f;
        if (missingPct > 0f) {
            int l = 1;
            if (missingPct >= CommonConfig.PAIN_L3.get()) l = 4;
            else if (missingPct >= CommonConfig.PAIN_L2.get()) l = 3;
            else if (missingPct >= CommonConfig.PAIN_L1.get()) l = 2;
            lv[Moodle.index(Moodle.PAIN)] = l;
        }

        // 不开心 / 压力 / 无聊 / 恐慌：内部值每 20 点一级
        lv[Moodle.index(Moodle.UNHAPPY)] = valueLevel(data.unhappy);
        lv[Moodle.index(Moodle.STRESS)] = valueLevel(data.stress);
        lv[Moodle.index(Moodle.BORED)] = valueLevel(data.bored);
        lv[Moodle.index(Moodle.PANIC)] = valueLevel(data.panic);

        // 累了：疲惫值 0-120，对应「3 天不睡一级、每天加一级」
        lv[Moodle.index(Moodle.TIRED)] = data.fatigue <= 40f ? 0 : Math.min(4, (int) Math.ceil((data.fatigue - 40f) / 20f));

        // 湿漉漉
        if (data.wetTicks > 0) lv[Moodle.index(Moodle.WET)] = 1;

        // 联动状态也在统一计算阶段写入，避免被 Arrays.fill 清掉
        if (CommonConfig.COMPAT_THIRST.get()) {
            int thirst = Compat.thirstValue(player);
            if (thirst >= 0) lv[Moodle.index(Moodle.THIRST)] = thirst <= 0 ? 4 : thirst <= 5 ? 3 : thirst <= 10 ? 2 : thirst <= 15 ? 1 : 0;
        }
        if (CommonConfig.COMPAT_TAVERN.get()) {
            lv[Moodle.index(Moodle.DRUNK)] = Compat.tipsyLevel(player);
        }
        if (CommonConfig.COMPAT_COLD_SWEAT.get()) {
            double temp = Compat.bodyTemperature(player);
            if (!Double.isNaN(temp)) {
                lv[Moodle.index(Moodle.COLD)] = temp <= CommonConfig.COLD_THRESHOLD.get() ? tempLevel(temp - CommonConfig.COLD_THRESHOLD.get()) : 0;
                lv[Moodle.index(Moodle.HOT)] = temp >= CommonConfig.HOT_THRESHOLD.get() ? tempLevel(temp - CommonConfig.HOT_THRESHOLD.get()) : 0;
            }
        }
        lv[Moodle.index(Moodle.BLEEDING)] = bleedingLevel(player);
        lv[Moodle.index(Moodle.STAMINA)] = Stamina.iconLevel(StaminaSystem.currentPercent(player));
        lv[Moodle.index(Moodle.HEAVY_LOAD)] = Encumbrance.iconLevel(StaminaSystem.loadState(player));
    }

    private static int tempLevel(double over) {
        return Math.min(4, 1 + (int) (Math.abs(over) / 25));
    }

    private static int bleedingLevel(Player player) {
        for (var inst : player.getActiveEffects()) {
            var key = inst.getEffect().getRegisteredName();
            if (key.contains("bleed")) return Math.min(4, inst.getAmplifier() + 1);
        }
        return 0;
    }

    private static int valueLevel(float v) {
        if (v <= 0f) return 0;
        return Math.min((int) Math.ceil(v / 20f), 4);
    }

    /** 施加与等级挂钩的原版效果（缓慢/虚弱/减速）。 */
    public static void applyEffects(Player player, MoodleData data) {
        int tiredL = data.displayLevels[Moodle.index(Moodle.TIRED)];

        // 饥饿的减速/减攻不再挂缓慢+虚弱药水效果：改由 HungerSystem 的连续属性修正
        // 渐进压低走/跑/潜行速度、跳跃、攻击力与攻速（曲线循序渐进，不是一格一台阶）。
        HungerSystem.applyPenalties(player, data);

        applySpeedModifiers(player, tiredL);
        applyTraitHealth(player, data);
        applyOccupationBonus(player, data);
    }

    private static void applySpeedModifiers(Player player, int tiredLevel) {
        AttributeInstance move = player.getAttribute(Attributes.MOVEMENT_SPEED);
        AttributeInstance mine = player.getAttribute(Attributes.BLOCK_BREAK_SPEED);
        double amount = -CommonConfig.TIRED_SPEED_PER_LEVEL.get() * tiredLevel;
        if (move != null) {
            if (tiredLevel <= 0) move.removeModifier(TIRED_MOVE);
            else setModifier(move, TIRED_MOVE, amount);
        }
        if (mine != null) {
            if (tiredLevel <= 0) mine.removeModifier(TIRED_MINE);
            else setModifier(mine, TIRED_MINE, amount);
        }
    }

    private static void applyTraitHealth(Player player, MoodleData data) {
        AttributeInstance health = player.getAttribute(Attributes.MAX_HEALTH);
        if (health == null) return;
        if (OccupationTrait.has(data, OccupationTrait.FRAIL)) {
            setModifier(health, FRAIL_HEALTH, -0.20);
        } else {
            health.removeModifier(FRAIL_HEALTH);
        }
    }

    /**
     * 职业、特质与后天成长三部分合成的最终人物数值加成。
     * 僵毁式 0-10 分，普通人 5 分：
     * 力量→攻击力/挖掘速度，体格→最大生命，冲刺→移动速度，
     * 轻巧→潜行速度，灵活→攻速，瞄准→远程伤害（在事件层结算）。
     * 这里用的是「职业基础 + 特质加成 + 成长等级」的总值，
     * 所以物品栏里看到的数值与实际生效的加成是一致的。
     */
    public static void applyOccupationBonus(Player player, MoodleData data) {
        StatData growth = MoodleAttachments.stats(player);
        String occ = data.occupation;
        int strength = Occupation.combine(Occupation.strength(occ),
                OccupationTrait.strengthBonus(data.traits), growth.level(Skill.STRENGTH));
        int fitness = Occupation.combine(Occupation.fitness(occ),
                OccupationTrait.fitnessBonus(data.traits), growth.level(Skill.FITNESS));
        int sprinting = Occupation.combine(Occupation.sprinting(occ),
                OccupationTrait.sprintingBonus(data.traits), growth.level(Skill.SPRINTING));
        int lightfooted = Occupation.combine(Occupation.lightfooted(occ),
                OccupationTrait.lightfootedBonus(data.traits), growth.level(Skill.LIGHTFOOTED));
        int nimble = Occupation.combine(Occupation.nimble(occ),
                OccupationTrait.nimbleBonus(data.traits), growth.level(Skill.NIMBLE));

        AttributeInstance attack = player.getAttribute(Attributes.ATTACK_DAMAGE);
        AttributeInstance mine = player.getAttribute(Attributes.BLOCK_BREAK_SPEED);
        AttributeInstance health = player.getAttribute(Attributes.MAX_HEALTH);
        AttributeInstance move = player.getAttribute(Attributes.MOVEMENT_SPEED);
        AttributeInstance attackSpeed = player.getAttribute(Attributes.ATTACK_SPEED);
        AttributeInstance sneak = player.getAttribute(Attributes.SNEAKING_SPEED);

        if (attack != null) {
            // 力量 0/5/10 分别对应 50%/100%/200% 攻击力。
            setModifier(attack, OCC_ATTACK, Occupation.lowToHighMultiplier(strength) - 1.0);
        }
        if (mine != null) {
            setModifier(mine, OCC_MINE, Occupation.overMultiplier(strength,
                    CommonConfig.STRENGTH_MINE_PER_POINT.get()) - 1.0);
        }
        if (health != null) {
            // 体格 0/5/10 分别对应 50%/100%/200% 最大生命。
            setModifier(health, OCC_HEALTH, Occupation.lowToHighMultiplier(fitness) - 1.0);
        }
        if (move != null) {
            // 冲刺 0-10 级对应 0%-50% 移动速度加成。
            setModifier(move, OCC_SPEED, Occupation.sprintMultiplier(sprinting) - 1.0);
        }
        if (attackSpeed != null) {
            // 灵活 10 级 = 所有手持物品攻击速度 +50%，每级 +5%。
            setModifier(attackSpeed, OCC_ATTACK_SPEED, nimble * 0.05);
        }
        if (sneak != null) {
            // 原版潜行速度约为走路的 30%；轻巧 10 级提升到走路速度（0.30 + 0.70）。
            setModifier(sneak, OCC_SNEAK, lightfooted * 0.07, AttributeModifier.Operation.ADD_VALUE);
        }
        AttributeInstance jump = player.getAttribute(Attributes.JUMP_STRENGTH);
        if (jump != null) {
            // 跳跃高度按等级线性增加：10 级高度 1.5 倍，速度按平方根换算。
            setModifier(jump, LIGHTFOOTED_JUMP, Math.sqrt(1.0 + lightfooted * 0.05) - 1.0);
        }

        boolean axeSpecialist = OccupationTrait.has(data, OccupationTrait.AXEMAN)
                && isAxe(player.getMainHandItem());
        if (attackSpeed != null) {
            if (axeSpecialist) {
                double cooldownReduction = DefOverrides.mechanic("trait", OccupationTrait.AXEMAN, "axe_attack_speed", 0.25);
                setModifier(attackSpeed, AXEMAN_ATTACK_SPEED, (1.0 / Math.max(0.01, 1.0 - cooldownReduction)) - 1.0);
            }
            else attackSpeed.removeModifier(AXEMAN_ATTACK_SPEED);
        }

        // 夜猫子：夜晚移动速度更快（默认 +10%，可在管理员界面改 night_move_bonus）
        boolean night = player.level().isNight();
        boolean nightOwl = OccupationTrait.has(data, OccupationTrait.NIGHT_OWL);
        if (move != null) {
            if (night && nightOwl) {
                setModifier(move, NIGHT_OWL_MOVE,
                        DefOverrides.mechanic("trait", OccupationTrait.NIGHT_OWL, "night_move_bonus", 0.10));
            } else move.removeModifier(NIGHT_OWL_MOVE);
        }

        applyBoxerUnarmed(player, occ, attack, attackSpeed);
        applyVillageHeroEffect(player, occ);
    }

    /**
     * 村庄英雄职业：自身常驻「村庄英雄」效果（村民交易打折、铁傀儡不会攻击你，
     * 僵尸也更容易盯上你）。用「无限时长 + 不出粒子」的方式挂上，只在换职业时撤掉。
     * 撤的时候只撤我们自己给的这一份（判断依据是无限时长），
     * 原版打赢突袭奖励的那份有时长的效果不受影响。
     */
    private static void applyVillageHeroEffect(Player player, String occupation) {
        boolean hero = Occupation.VILLAGE_HERO.equals(occupation);
        MobEffectInstance current = player.getEffect(MobEffects.HERO_OF_THE_VILLAGE);
        if (hero) {
            if (current == null || !current.isInfiniteDuration()) {
                player.addEffect(new MobEffectInstance(MobEffects.HERO_OF_THE_VILLAGE,
                        MobEffectInstance.INFINITE_DURATION, 0, true, false));
            }
        } else if (current != null && current.isInfiniteDuration()) {
            player.removeEffect(MobEffects.HERO_OF_THE_VILLAGE);
        }
    }

    /**
     * 拳击手专属：空手（主手没拿东西）时攻击力固定为 5（基础 1 + 4），
     * 攻速提升 50%。界面上只写「大幅度提高」，不显示具体数值。
     */
    private static void applyBoxerUnarmed(Player player, String occupation,
                                          AttributeInstance attack, AttributeInstance attackSpeed) {
        boolean boxer = Occupation.BOXER.equals(occupation);
        boolean bareHands = player.getMainHandItem().isEmpty();
        double damageBonus = DefOverrides.mechanic("occupation", Occupation.BOXER, "unarmed_damage_bonus", 4.0);
        double speedBonus = DefOverrides.mechanic("occupation", Occupation.BOXER, "unarmed_attack_speed", 0.5);
        if (attack != null) {
            if (boxer && bareHands) {
                if (attack.getModifier(BOXER_UNARMED_DAMAGE) == null) {
                    attack.addOrUpdateTransientModifier(new AttributeModifier(BOXER_UNARMED_DAMAGE, damageBonus,
                            AttributeModifier.Operation.ADD_VALUE));
                }
            } else {
                attack.removeModifier(BOXER_UNARMED_DAMAGE);
            }
        }
        if (attackSpeed != null) {
            if (boxer && bareHands) {
                if (attackSpeed.getModifier(BOXER_UNARMED_SPEED) == null) {
                    attackSpeed.addOrUpdateTransientModifier(new AttributeModifier(BOXER_UNARMED_SPEED, speedBonus,
                            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
                }
            } else {
                attackSpeed.removeModifier(BOXER_UNARMED_SPEED);
            }
        }
    }

    private static boolean isAxe(net.minecraft.world.item.ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (stack.getItem() instanceof AxeItem) return true;
        String path = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().toLowerCase();
        return path.contains("axe") || path.contains("hatchet");
    }

    /** 读取职业、特质与后天成长合成后的最终技能等级。 */
    public static int effectiveLevel(Player player, Skill skill) {
        MoodleData data = MoodleAttachments.get(player);
        StatData growth = MoodleAttachments.stats(player);
        return Occupation.combine(Occupation.stat(data.occupation, skill),
                OccupationTrait.traitBonus(data.traits, skill), growth.level(skill));
    }

    /** 瞄准带来的远程武器伤害倍率（在箭矢生成时结算），同样吃职业、特质与成长。 */
    public static float projectileMultiplier(Player player, MoodleData data) {
        StatData growth = MoodleAttachments.stats(player);
        int aiming = Occupation.combine(Occupation.aiming(data.occupation),
                OccupationTrait.aimingBonus(data.traits), growth.level(Skill.AIMING));
        return (float) Occupation.overMultiplier(aiming, CommonConfig.AIMING_PROJECTILE_PER_POINT.get());
    }

    /** 井井有条：把 1.0-1.3 格范围内的掉落物轻轻拉向玩家（拾取范围 +30%）。 */
    public static void attractItems(Player player, MoodleData data) {
        if (!OccupationTrait.has(data, OccupationTrait.ORGANIZED)) return;
        for (net.minecraft.world.entity.Entity entity : player.level().getEntities(player,
                player.getBoundingBox().inflate(1.3, 0.6, 1.3),
                e -> e instanceof net.minecraft.world.entity.item.ItemEntity)) {
            if (!(entity instanceof net.minecraft.world.entity.item.ItemEntity item)) continue;
            if (item.isRemoved() || item.hasPickUpDelay()) continue;
            // 原版 1.0 格内已自动拾取，只对 1.0-1.3 区间的物品施加牵引
            if (player.distanceToSqr(item) > 1.0) {
                item.setDeltaMovement(item.getDeltaMovement().add(
                        player.getX() > item.getX() ? 0.03 : -0.03,
                        0,
                        player.getZ() > item.getZ() ? 0.03 : -0.03));
            }
        }
    }

    private static void setModifier(AttributeInstance attr, ResourceLocation id, double amount) {
        setModifier(attr, id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }

    private static void setModifier(AttributeInstance attr, ResourceLocation id, double amount,
                                    AttributeModifier.Operation operation) {
        AttributeModifier existing = attr.getModifier(id);
        if (existing != null && existing.amount() == amount && existing.operation() == operation) return;
        attr.addOrUpdateTransientModifier(new AttributeModifier(id, amount, operation));
    }

    private MoodleSystem() {}
}
