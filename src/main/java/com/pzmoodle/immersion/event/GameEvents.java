package com.pzmoodle.immersion.event;

import com.pzmoodle.immersion.PZMoodleImmersion;
import com.pzmoodle.immersion.admin.DefOverrides;
import com.pzmoodle.immersion.admin.GrantItems;
import com.pzmoodle.immersion.compat.TaczCompat;
import com.pzmoodle.immersion.config.CommonConfig;
import com.pzmoodle.immersion.moodle.Moodle;
import com.pzmoodle.immersion.moodle.MoodleAttachments;
import com.pzmoodle.immersion.moodle.MoodleData;
import com.pzmoodle.immersion.moodle.MoodleSystem;
import com.pzmoodle.immersion.moodle.MoodleTicker;
import com.pzmoodle.immersion.moodle.Skill;
import com.pzmoodle.immersion.net.Packets;
import com.pzmoodle.immersion.trait.OccupationTrait;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.living.BabyEntitySpawnEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.entity.ExperienceOrb;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.CanPlayerSleepEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent;
import net.neoforged.neoforge.event.entity.player.AnvilRepairEvent;
import net.neoforged.neoforge.event.entity.player.ItemFishedEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Arrays;

/**
 * 服务端事件层：数值推进、击杀统计、命中/伤害修正、经验修正、白天睡觉。
 */
@EventBusSubscriber(modid = PZMoodleImmersion.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class GameEvents {

    /** 每玩家结算节流：用玩家 entity id 错开峰值。 */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player p = event.getEntity();
        if (p.level().isClientSide || !(p instanceof ServerPlayer sp)) return;
        MoodleData everyTickData = MoodleAttachments.get(sp);
        // 每 tick 只保留必须连续推进的部分：体力结算（动作手感）、急救加速推进、
        // 「井井有条」吸物品（没有该特质时第一行就返回，不产生扫描）与浴血奋战倒计时。
        com.pzmoodle.immersion.moodle.StaminaSystem.tick(sp);
        com.pzmoodle.immersion.compat.LrTacticalCompat.tickUsing(sp);
        MoodleSystem.attractItems(sp, everyTickData);
        if (everyTickData.furyTicks > 0) everyTickData.furyTicks--;
        int interval = CommonConfig.TICK_INTERVAL.get();
        if ((sp.getId() + sp.tickCount) % interval != 0) return;

        // 潜行甩目标（40 格半径实体扫描）收进节流段：原来只要玩家蹲着、
        // 且潜行技能大于 0 就每 tick 扫一遍周围实体，服务器人多时开销最大，
        // 降到每秒一次甩目标手感不变、开销省 20 倍。
        clearStealthTargets(sp);

        MoodleData data = everyTickData;
        boolean sync = false;
        MoodleTicker.tick(sp, interval);
        MoodleSystem.computeLevels(sp, data);
        MoodleSystem.applyEffects(sp, data);
        if (trainStats(sp, data)) sync = true;

        // 等级有变化才同步，省流量
        if (sync || sp.hasPermissions(2) || !Arrays.equals(data.syncedLevels, data.displayLevels)) {
            System.arraycopy(data.displayLevels, 0, data.syncedLevels, 0, data.displayLevels.length);
            Packets.sendSync(sp);
        }
    }

    /** 击杀僵尸或动物：保留原有心情逻辑，并推进对应战斗/屠宰技能。 */
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide) return;
        if (!(event.getSource().getEntity() instanceof ServerPlayer sp)) return;

        // 玩家亲手击杀：不再掉经验球实体，击杀瞬间直接把经验值加到玩家身上。
        // 非玩家击杀（别的怪、摔死、烧死……）走不到这里，经验球照常掉落。
        grantKillExperienceDirectly(sp, victim);

        boolean changed = false;
        if (victim.getType().is(EntityTypeTags.ZOMBIES)) {
            MoodleTicker.onZombieKill(sp);
            changed |= MoodleAttachments.stats(sp).addXp(Skill.STRENGTH, 40);
            changed |= MoodleAttachments.stats(sp).addXp(Skill.FITNESS, 15);
            changed |= MoodleAttachments.stats(sp).addXp(Skill.NIMBLE, 25);
        }
        if (victim instanceof Animal) {
            changed |= MoodleAttachments.stats(sp).addXp(Skill.BUTCHERING, 25);
            changed |= MoodleAttachments.stats(sp).addXp(Skill.TRACKING, 5);
        }

        ItemStack weapon = sp.getMainHandItem();
        Skill weaponSkill = weaponSkill(weapon);
        if (weaponSkill != null) changed |= MoodleAttachments.stats(sp).addXp(weaponSkill, 25);
        if (changed) Packets.sendSync(sp);
    }

    /**
     * 玩家击杀生物：把这只生物该给的经验当场结算给玩家，并且不让原版生成经验球实体。
     * <p>
     * 做法是先 {@code skipDropExperience()} 让原版 {@code dropExperience} 直接跳过（经验球根本不会
     * 出现在世界里，也就没有满地滚球、看不见捡不到的问题），再拿一个「只用于结算、从不加入世界」的
     * 临时经验球走一次原版拾取逻辑 {@code playerTouch}：经验值、抢夺附魔、经验修补（用经验修装备）
     * 全都按原版规则算，特质倍率则由下面 {@link #onPickupXp} 统一补差。
     */
    private static void grantKillExperienceDirectly(ServerPlayer killer, LivingEntity victim) {
        if (!(victim.level() instanceof net.minecraft.server.level.ServerLevel level)) return;
        // 与原版判定保持一致：幼年生物不掉经验、doMobLoot 关掉时不掉经验
        if (victim.wasExperienceConsumed() || !victim.shouldDropExperience()) return;
        if (!level.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_DOMOBLOOT)) return;
        // 与原版完全同一条算式：基础经验 → 抢夺附魔 → 其他 mod 的经验改动（LivingExperienceDropEvent）
        int xp = net.neoforged.neoforge.event.EventHooks.getExperienceDrop(victim, killer,
                victim.getExperienceReward(level, killer));
        if (xp <= 0) return;
        victim.skipDropExperience();
        ExperienceOrb orb = new ExperienceOrb(level, victim.getX(), victim.getY(), victim.getZ(), xp);
        killer.takeXpDelay = 0;
        orb.playerTouch(killer);
    }

    /** 按物品注册名把原版武器归到 B42 的近战技能。 */
    private static Skill weaponSkill(ItemStack stack) {
        if (stack.isEmpty()) return null;
        String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().toLowerCase();
        if (stack.getItem() instanceof AxeItem || path.contains("axe")) return Skill.AXE;
        if (stack.getItem() instanceof TridentItem || path.contains("spear")) return Skill.SPEAR;
        if (stack.getItem() instanceof SwordItem || path.contains("sword") || path.contains("machete")) {
            return path.contains("knife") || path.contains("dagger") ? Skill.SHORT_BLADE : Skill.LONG_BLADE;
        }
        if (path.contains("hammer") || path.contains("mace") || path.contains("club")) return Skill.LONG_BLUNT;
        if (path.contains("bat") || path.contains("stick")) return Skill.SHORT_BLUNT;
        return null;
    }

    /** 破坏方块：按方块注册名推进木工、石工、农耕、觅食等技能。 */
    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer sp)) return;
        String path = BuiltInRegistries.BLOCK.getKey(event.getState().getBlock()).getPath().toLowerCase();
        boolean changed = false;
        if (path.contains("log") || path.contains("stem") || path.contains("wood")) {
            changed |= MoodleAttachments.stats(sp).addXp(Skill.CARPENTRY, 8);
            if (sp.getMainHandItem().getItem() instanceof AxeItem) {
                changed |= MoodleAttachments.stats(sp).addXp(Skill.AXE, 5);
            }
        }
        if (path.contains("stone") || path.contains("brick") || path.contains("cobble") || path.contains("deepslate")) {
            changed |= MoodleAttachments.stats(sp).addXp(Skill.MASONRY, 5);
        }
        if (path.contains("glass")) changed |= MoodleAttachments.stats(sp).addXp(Skill.GLASSMAKING, 8);
        if (path.contains("clay") || path.contains("terracotta")) changed |= MoodleAttachments.stats(sp).addXp(Skill.POTTERY, 8);
        if (path.contains("gravel") || path.contains("flint")) changed |= MoodleAttachments.stats(sp).addXp(Skill.KNAPPING, 5);
        if (path.contains("crop") || path.contains("wheat") || path.contains("carrot") || path.contains("potato")
                || path.contains("beetroot") || path.contains("melon") || path.contains("pumpkin")) {
            changed |= MoodleAttachments.stats(sp).addXp(Skill.AGRICULTURE, 8);
        }
        if (path.contains("leaves") || path.contains("grass") || path.contains("fern") || path.contains("berry")) {
            changed |= MoodleAttachments.stats(sp).addXp(Skill.FORAGING, 4);
        }
        if (changed) Packets.sendSync(sp);
    }

    /** 点击炉子、红石和机械方块时，给相关制作技能少量经验。 */
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        String path = BuiltInRegistries.BLOCK.getKey(sp.level().getBlockState(event.getPos()).getBlock()).getPath().toLowerCase();
        Skill skill = null;
        if (path.contains("furnace") || path.contains("smoker") || path.contains("campfire")) skill = Skill.COOKING;
        else if (path.contains("redstone") || path.contains("lever") || path.contains("button") || path.contains("observer")) skill = Skill.ELECTRICAL;
        else if (path.contains("rail") || path.contains("hopper") || path.contains("dispenser") || path.contains("piston")) skill = Skill.MECHANICS;
        else if (path.contains("tripwire") || path.contains("cobweb")) skill = Skill.TRAPPING;
        if (skill != null && MoodleAttachments.stats(sp).addXp(skill, 3)) Packets.sendSync(sp);
    }

    /** 钓鱼成功或失败都算一次练习，和 PZ 的钓鱼经验规则一致。 */
    @SubscribeEvent
    public static void onFish(ItemFishedEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp
                && MoodleAttachments.stats(sp).addXp(Skill.FISHING, 20)) {
            Packets.sendSync(sp);
        }
    }

    /** 铁砧修理同时训练保养与焊接/锻造。 */
    @SubscribeEvent
    public static void onAnvilRepair(AnvilRepairEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        var stats = MoodleAttachments.stats(sp);
        boolean changed = stats.addXp(Skill.MAINTENANCE, 25);
        changed |= stats.addXp(Skill.WELDING, 10);
        if (changed) Packets.sendSync(sp);
    }

    /** 繁殖动物训练动物照料与农耕。 */
    @SubscribeEvent
    public static void onAnimalBreed(BabyEntitySpawnEvent event) {
        if (event.getCausedByPlayer() instanceof ServerPlayer sp) {
            var stats = MoodleAttachments.stats(sp);
            boolean changed = stats.addXp(Skill.ANIMAL_CARE, 25);
            changed |= stats.addXp(Skill.AGRICULTURE, 8);
            if (changed) Packets.sendSync(sp);
        }
    }

    /** 玩家被治疗时推进急救技能，并放大 LesRaisins 医疗物资的直接治疗量。 */
    @SubscribeEvent
    public static void onHeal(LivingHealEvent event) {
        com.pzmoodle.immersion.compat.LrTacticalCompat.scaleHeal(event);
        if (event.getEntity() instanceof ServerPlayer sp
                && MoodleAttachments.stats(sp).addXp(Skill.FIRST_AID, Math.max(5, Math.round(event.getAmount() * 4)))) {
            Packets.sendSync(sp);
        }
    }

    /** 兼容所有遵循原版使用时长的医疗物品；LesRaisins toggle 物品另由附件计时器加速。 */
    @SubscribeEvent
    public static void onMedicalUseStart(LivingEntityUseItemEvent.Start event) {
        com.pzmoodle.immersion.compat.LrTacticalCompat.scaleStart(event);
    }

    /** 玩家死亡重生：心情类数值清零、职业与特质恢复默认，等待重新选择。 */
    @SubscribeEvent
    public static void onPlayerClone(net.neoforged.neoforge.event.entity.player.PlayerEvent.Clone event) {
        if (event.isWasDeath() && event.getEntity() instanceof ServerPlayer sp) {
            MoodleData data = MoodleAttachments.get(sp);
            // 死亡重来：不开心、压力、无聊、恐慌、疲惫全部清零
            data.unhappy = 0f;
            data.stress = 0f;
            data.bored = 0f;
            data.panic = 0f;
            data.fatigue = 0f;
            data.stamina = -1f;
            data.idleTicks = 0;
            data.boredAccumTicks = 0;
            data.convertTicks = 0;
            data.daysAlive = 0;
            data.daysSinceSmoke = 0;
            data.daysSinceDrink = 0;
            data.lastDayTime = -1;
            data.zombieKills = 0;
            // 新的一命：饥饿惩罚状态全部清零
            com.pzmoodle.immersion.moodle.HungerSystem.reset(data);
            MoodleAttachments.stats(sp).reset();
            GrantItems.removeMarked(sp);
            // 新的一命默认是普通人，仍然允许玩家重新选择职业
            data.occupation = "unemployed";
            data.traits.clear();
            data.traitPoints = 0;
            data.occupationChosen = false;
            Packets.sendSync(sp);
        }
    }

    /** 近战攻击消耗体力；原版剑为 1 点，TaCZ 近战由 StaminaSystem 按重量估算。 */
    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            com.pzmoodle.immersion.moodle.StaminaSystem.attack(sp);
        }
    }

    /** 玩家攻击：疼痛/恐慌降低命中率与伤害。 */
    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity().level().isClientSide) return;
        if (!(event.getSource().getEntity() instanceof ServerPlayer attacker)) return;
        if (attacker == event.getEntity()) return;

        MoodleData data = MoodleAttachments.get(attacker);
        if (TaczCompat.isTaczBullet(event.getSource().getDirectEntity())) {
            // TaCZ 1.1.8 的真实伤害经过多个路径；在最终伤害事件统一乘瞄准倍率，避免只改普通箭矢。
            event.setAmount(event.getAmount() * TaczCompat.bulletDamageScale(event.getSource().getDirectEntity()));
            TaczCompat.onGunHit(attacker);
        }
        // 浴血奋战：受伤后 5 秒内攻击力 +50%（固定倍率，多次受伤不叠加倍率）
        if (OccupationTrait.has(data, OccupationTrait.BLOODY_FURY) && data.furyTicks > 0) {
            double bonus = DefOverrides.mechanic("trait", OccupationTrait.BLOODY_FURY, "attack_bonus", 0.50);
            event.setAmount(event.getAmount() * (float) (1.0 + bonus));
        }
        // 和平主义者：拒绝使用暴力，造成的攻击伤害 ×0.7（可在管理员界面改 damage_multiplier）
        if (OccupationTrait.has(data, OccupationTrait.PACIFIST)) {
            double mult = DefOverrides.mechanic("trait", OccupationTrait.PACIFIST, "damage_multiplier", 0.7);
            event.setAmount(event.getAmount() * (float) Math.max(0.0, mult));
        }
        int pain = data.displayLevels[Moodle.index(Moodle.PAIN)];
        int panic = data.displayLevels[Moodle.index(Moodle.PANIC)];
        if (pain == 0 && panic == 0) return;

        // 每级 -15% 攻击力（恐慌）
        float damageMult = (float) Math.max(0.1, 1.0 - panic * CommonConfig.PANIC_ATTACK_REDUCE_PER_LEVEL.get());
        event.setAmount(event.getAmount() * damageMult);

        // 命中率下降：疼痛每级 -10% + 恐慌每级 -15%
        double missChance = pain * CommonConfig.PAIN_MISS_PER_LEVEL.get()
                + panic * CommonConfig.PANIC_MISS_PER_LEVEL.get();
        if (missChance > 0 && attacker.getRandom().nextDouble() < Math.min(0.75, missChance)) {
            event.setCanceled(true);
        }
    }

    /** 吃食物完成：按饱食度减少不开心/压力；酒馆的酒快速解压；烟瘾者抽「烟」重置。 */
    @SubscribeEvent
    public static void onItemFinish(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide) return;
        var stack = event.getItem();
        if (player instanceof ServerPlayer sp && stack.getItem() instanceof net.minecraft.world.item.CrossbowItem) {
            if (MoodleAttachments.stats(sp).addXp(Skill.RELOADING, 15)) Packets.sendSync(sp);
        }
        var food = stack.getFoodProperties(player);
        if (food != null) {
            MoodleTicker.onEat(player, food.nutrition());
        }
        MoodleData data = MoodleAttachments.get(player);
        // 森罗物语：酒馆 —— 喝酒快速消除压力与不开心，并重置本命的戒酒计时。
        if (CommonConfig.COMPAT_TAVERN.get() && isTavernDrink(stack)) {
            MoodleTicker.onTavernDrink(player);
            data.daysSinceDrink = 0;
        }
        // 香烟模组通过物品标签接入；抽完一支烟就重置本命的戒烟计时。
        if (stack.is(com.pzmoodle.immersion.registry.PZTags.CIGARETTE)) {
            data.daysSinceSmoke = 0;
        }
    }

    /** 判断物品是否来自「森罗物语：酒馆」（按注册命名空间识别，不用硬编码每个酒的名字）。 */
    private static boolean isTavernDrink(net.minecraft.world.item.ItemStack stack) {
        var id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id != null && id.getNamespace().equals("kaleidoscope_tavern");
    }

    /**
     * 日常行为练级：冲刺练冲刺与体格、潜行练轻巧，每 5 秒结算一次。
     * 返回是否有数值变化，需要推送同步。
     */
    private static boolean trainStats(ServerPlayer sp, MoodleData data) {
        boolean changed = false;
        if (sp.isSprinting()) {
            data.trainTicks[0]++;
            if (data.trainTicks[0] >= 100) {
                data.trainTicks[0] = 0;
                MoodleAttachments.stats(sp).addSprinting(10);
                MoodleAttachments.stats(sp).addFitness(5);
                changed = true;
            }
        } else {
            data.trainTicks[0] = 0;
        }
        if (sp.isCrouching() && !sp.isSprinting()) {
            data.trainTicks[1]++;
            if (data.trainTicks[1] >= 100) {
                data.trainTicks[1] = 0;
                var stats = MoodleAttachments.stats(sp);
                stats.addLightfooted(10);
                changed = true;
                changed |= stats.addXp(Skill.SNEAKING, 10);
            }
        } else {
            data.trainTicks[1] = 0;
        }
        return changed;
    }

    /** 受伤处理：深得民心免玩家伤害、皮糙肉厚减伤、灵活闪避、浴血奋战计时与体格经验。 */
    @SubscribeEvent
    public static void onPlayerDamaged(net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp) || sp.level().isClientSide) return;
        MoodleData data = MoodleAttachments.get(sp);

        // 深得民心（村庄英雄专属）：完全不受到来自其他玩家的伤害。
        // 判定用的是伤害的「责任实体」，所以玩家本人、玩家射的箭、丢的药水、开的那枪都算，
        // 自己扎自己（摔落、溺水、烧死）不受影响，怪物的伤害也不受影响。
        if (OccupationTrait.has(data, OccupationTrait.BELOVED)
                && event.getSource().getEntity() instanceof Player attacker && attacker != sp) {
            event.setCanceled(true);
            return;
        }

        if (OccupationTrait.has(data, OccupationTrait.THICK_SKINNED)) {
            double reduction = DefOverrides.mechanic("trait", OccupationTrait.THICK_SKINNED, "damage_reduction", 0.25);
            event.setAmount(event.getAmount() * (float) Math.max(0.0, 1.0 - reduction));
        }

        if (event.getSource().getEntity() instanceof Mob mob
                && mob.getType().getCategory() == MobCategory.MONSTER) {
            int nimble = MoodleSystem.effectiveLevel(sp, Skill.NIMBLE);
            if (nimble > 0 && sp.getRandom().nextFloat() < nimble * 0.05f) {
                event.setCanceled(true);
                return;
            }
        }

        // 浴血奋战：受到任何伤害都刷新狂暴（时长可在管理员界面改 duration_seconds），多次受伤只叠加时间、不叠加伤害加成
        if (OccupationTrait.has(data, OccupationTrait.BLOODY_FURY)) {
            double durationSeconds = DefOverrides.mechanic("trait", OccupationTrait.BLOODY_FURY, "duration_seconds", 5.0);
            data.furyTicks = Math.max(0, data.furyTicks + Math.max(1, (int) Math.round(durationSeconds * 20.0)));
        }
        if (event.getAmount() < 2f) return;
        MoodleAttachments.stats(sp).addFitness(10);
        Packets.sendSync(sp);
    }

    /** 斧头专精：树木方块按金斧速度计算，并额外再快 25%。 */
    @SubscribeEvent
    public static void onBreakSpeed(net.neoforged.neoforge.event.entity.player.PlayerEvent.BreakSpeed event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        MoodleData data = MoodleAttachments.get(sp);
        if (!OccupationTrait.has(data, OccupationTrait.AXEMAN)
                || !isAxe(sp.getMainHandItem())
                || !isWoodBlock(event.getState())) return;
        float toolSpeed = 12.0f;
        if (sp.getMainHandItem().getItem() instanceof TieredItem tiered) {
            toolSpeed = Math.max(1.0f, tiered.getTier().getSpeed());
        }
        double treeBonus = DefOverrides.mechanic("trait", OccupationTrait.AXEMAN, "tree_speed_bonus", 0.25);
        event.setNewSpeed(event.getOriginalSpeed() * (12.0f / toolSpeed) * (float) (1.0 + treeBonus));
    }

    private static boolean isAxe(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (stack.getItem() instanceof AxeItem) return true;
        String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().toLowerCase();
        return path.contains("axe") || path.contains("hatchet");
    }

    private static boolean isWoodBlock(net.minecraft.world.level.block.state.BlockState state) {
        if (state.is(BlockTags.LOGS)) return true;
        String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath().toLowerCase();
        return path.contains("log") || path.contains("stem") || path.contains("wood");
    }

    /** 农耕、觅食：作物和树叶/浆果掉落按技能等级增加。 */
    @SubscribeEvent
    public static void onBlockDrops(net.neoforged.neoforge.event.level.BlockDropsEvent event) {
        if (!(event.getBreaker() instanceof ServerPlayer sp)) return;
        MoodleData data = MoodleAttachments.get(sp);
        int agriculture = MoodleSystem.effectiveLevel(sp, Skill.AGRICULTURE);
        int foraging = MoodleSystem.effectiveLevel(sp, Skill.FORAGING);
        if (agriculture > 0 && event.getState().is(BlockTags.CROPS)) {
            multiplyDrops(event.getDrops(), 1.0 + agriculture * 0.10);
        }
        if (foraging > 0 && event.getState().is(BlockTags.LEAVES)) {
            boolean hasApple = event.getDrops().stream().anyMatch(drop -> drop.getItem().is(net.minecraft.world.item.Items.APPLE));
            if (!hasApple && sp.getRandom().nextDouble() < 0.05 * foraging * 0.20) {
                event.getDrops().add(new net.minecraft.world.entity.item.ItemEntity(event.getLevel(),
                        event.getPos().getX() + 0.5, event.getPos().getY() + 0.5, event.getPos().getZ() + 0.5,
                        new ItemStack(net.minecraft.world.item.Items.APPLE)));
            }
        }
        if (foraging > 0 && event.getState().getBlock() instanceof net.minecraft.world.level.block.SweetBerryBushBlock) {
            multiplyDrops(event.getDrops(), 1.0 + foraging * 0.20,
                    stack -> stack.is(net.minecraft.world.item.Items.SWEET_BERRIES));
        }
    }

    private static void multiplyDrops(java.util.List<net.minecraft.world.entity.item.ItemEntity> drops, double factor) {
        multiplyDrops(drops, factor, stack -> true);
    }

    private static void multiplyDrops(java.util.List<net.minecraft.world.entity.item.ItemEntity> drops, double factor,
                                      java.util.function.Predicate<ItemStack> filter) {
        for (net.minecraft.world.entity.item.ItemEntity drop : drops) {
            ItemStack stack = drop.getItem();
            if (filter.test(stack)) stack.setCount(Math.max(1, (int) Math.ceil(stack.getCount() * factor)));
        }
    }

    /** 屠宰：动物掉落物按屠宰等级增加，10 级为双倍。 */
    @SubscribeEvent
    public static void onAnimalDrops(net.neoforged.neoforge.event.entity.living.LivingDropsEvent event) {
        if (!(event.getEntity() instanceof Animal animal)) return;
        if (!(event.getSource().getEntity() instanceof ServerPlayer sp)) return;
        int level = MoodleSystem.effectiveLevel(sp, Skill.BUTCHERING);
        if (level <= 0) return;
        double factor = 1.0 + level * 0.10;
        for (Object value : event.getDrops()) {
            if (value instanceof net.minecraft.world.entity.item.ItemEntity item) {
                item.getItem().setCount(Math.max(1, (int) Math.ceil(item.getItem().getCount() * factor)));
            }
        }
    }

    /** 拉弓练瞄准：松手放箭即算一次训练（常规曲线）。 */
    @SubscribeEvent
    public static void onBowDraw(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickItem event) {
        Player p = event.getEntity();
        if (p.level().isClientSide) return;
        if (event.getItemStack().is(com.pzmoodle.immersion.registry.PZTags.CIGARETTE)) {
            MoodleAttachments.get(p).daysSinceSmoke = 0;
        }
        var item = event.getItemStack().getItem();
        if (item instanceof net.minecraft.world.item.BowItem
                || item instanceof net.minecraft.world.item.CrossbowItem) {
            MoodleAttachments.stats(p).addAiming(15);
            if (p instanceof ServerPlayer sp) Packets.sendSync(sp);
        }
    }

    /**
     * 瞄准数值：玩家射出的箭矢/弩矢伤害按瞄准分值缩放。
     * 用实体加入世界的事件统一处理，箭与弩矢都覆盖。
     */
    @SubscribeEvent
    public static void onProjectileJoin(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide) return;
        if (!(event.getEntity() instanceof net.minecraft.world.entity.projectile.AbstractArrow arrow)) return;
        if (!(arrow.getOwner() instanceof ServerPlayer sp)) return;
        float mult = MoodleSystem.projectileMultiplier(sp, MoodleAttachments.get(sp));
        if (Math.abs(mult - 1f) < 0.001f) return;
        arrow.setBaseDamage(arrow.getBaseDamage() * mult);
    }

    /**
     * 经验拾取倍率：速学者 +30%、慢速学习者/学不会 -30%、不开心惩罚。
     * <p>
     * 这里只补「倍率带来的差额」，绝不再取消事件。之前正是 {@code setCanceled(true)} 惹的祸：
     * 取消后原版直接 return，经验球实体既没被移除、也没进拾取冷却，玩家身上每 tick 又撞一次，
     * 于是同一颗球反复加经验——就是「打某些生物经验停不下来」的成因。
     * 现在球照常由原版消耗，倍率单独补差，逻辑上不可能再死循环。
     */
    @SubscribeEvent
    public static void onPickupXp(PlayerXpEvent.PickupXp event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;
        MoodleData data = MoodleAttachments.get(player);
        float mult = OccupationTrait.xpMultiplier(data);
        int unhappyL = data.displayLevels[Moodle.index(Moodle.UNHAPPY)];
        if (unhappyL >= CommonConfig.XP_PENALTY_FROM_LEVEL.get()) {
            mult *= (float) Math.max(0.2, 1.0 - (unhappyL - 2) * CommonConfig.XP_PENALTY_PER_LEVEL.get());
        }
        if (Math.abs(mult - 1f) < 0.01f) return;
        int base = event.getOrb().getValue();
        int delta = Math.round(base * mult) - base;
        if (delta != 0) player.giveExperiencePoints(delta);
    }

    /** 铁胃：高概率免疫中毒（概率可在管理员界面改 poison_immune_chance，默认 80%）。 */
    @SubscribeEvent
    public static void onEffectApplicable(MobEffectEvent.Applicable event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide) return;
        MoodleData data = MoodleAttachments.get(player);
        if (OccupationTrait.has(data, OccupationTrait.IRON_STOMACH)
                && event.getEffectInstance().getEffect().is(MobEffects.POISON)) {
            double chance = DefOverrides.mechanic("trait", OccupationTrait.IRON_STOMACH, "poison_immune_chance", 0.80);
            if (player.getRandom().nextFloat() < chance) {
                event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
            }
        }
    }

    /** 潜行：按原版 FOLLOW_RANGE 缩短敌对生物发现潜行玩家的距离；10 级减半。 */
    @SubscribeEvent
    public static void onMobTargetChange(LivingChangeTargetEvent event) {
        if (!(event.getEntity() instanceof Mob mob)
                || !(event.getNewAboutToBeSetTarget() instanceof ServerPlayer player)
                || !player.isCrouching()) return;
        int level = MoodleSystem.effectiveLevel(player, Skill.SNEAKING);
        if (level <= 0) return;
        var follow = mob.getAttribute( net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE);
        double range = follow == null ? 16.0 : follow.getValue();
        double effective = range * Math.max(0.5, 1.0 - level * 0.05);
        if (mob.distanceToSqr(player) > effective * effective) event.setNewAboutToBeSetTarget(null);
    }

    private static void clearStealthTargets(ServerPlayer player) {
        if (!player.isCrouching()) return;
        int level = MoodleSystem.effectiveLevel(player, Skill.SNEAKING);
        if (level <= 0) return;
        for (Mob mob : player.level().getEntitiesOfClass(Mob.class,
                player.getBoundingBox().inflate(40), candidate -> candidate.getTarget() == player)) {
            var follow = mob.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE);
            double range = follow == null ? 16.0 : follow.getValue();
            double effective = range * Math.max(0.5, 1.0 - level * 0.05);
            if (mob.distanceToSqr(player) > effective * effective) mob.setTarget(null);
        }
    }

    /**
     * 0 饱食度不再按原版「每 4 秒扣 1 血」立即掉血：改由 HungerSystem 的
     * 渐进惩罚（1/2/3 周）+ 三周后的缓慢生命流失接管。
     * 我们自己的「饿死收尾一击」带着 starveKill 标记，从这里放行，保证死因仍是饿死。
     */
    @SubscribeEvent
    public static void onVanillaStarveDamage(LivingIncomingDamageEvent event) {
        if (!CommonConfig.HUNGER_DISABLE_VANILLA_STARVE.get()) return;
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        if (!event.getSource().is(net.minecraft.world.damagesource.DamageTypes.STARVE)) return;
        if (MoodleAttachments.get(sp).starveKill) return;
        event.setCanceled(true);
    }

    /** 白天睡觉：取消原版「上床时」的夜晚限制。 */
    @SubscribeEvent
    public static void onSleep(CanPlayerSleepEvent event) {
        if (!CommonConfig.ALLOW_DAY_SLEEP.get()) return;
        if (event.getProblem() == Player.BedSleepingProblem.NOT_POSSIBLE_NOW) {
            event.setProblem(null);
        }
    }

    /**
     * 白天睡觉：取消原版「躺着期间」每 tick 复查白天会把人叫醒的限制。
     * 这正是「躺着站着来回切换」的根源——原版 Player.tick 只要发现是白天就 stopSleepInBed。
     * 用官方事件（而非脆弱的字节码重定向）放行；但床已消失/被挡（NOT_POSSIBLE_HERE）仍照常下床。
     */
    @SubscribeEvent
    public static void onContinueSleep(net.neoforged.neoforge.event.entity.player.CanContinueSleepingEvent event) {
        if (!CommonConfig.ALLOW_DAY_SLEEP.get()) return;
        if (event.getProblem() == Player.BedSleepingProblem.NOT_POSSIBLE_NOW) {
            event.setContinueSleeping(true);
        }
    }

    /** 玩家进服：立即同步一次显示数据。 */
    @SubscribeEvent
    public static void onJoin(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp && !event.getLevel().isClientSide) {
            Packets.sendSync(sp);
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(sp,
                    new Packets.AdminDefinitions(com.pzmoodle.immersion.admin.DefOverrides.toJson()));
        }
    }

    private GameEvents() {}
}
