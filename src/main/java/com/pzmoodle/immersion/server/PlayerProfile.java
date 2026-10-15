package com.pzmoodle.immersion.server;

import com.pzmoodle.immersion.admin.GrantItems;
import com.pzmoodle.immersion.config.CommonConfig;
import com.pzmoodle.immersion.moodle.MoodleAttachments;
import com.pzmoodle.immersion.moodle.MoodleData;
import com.pzmoodle.immersion.moodle.MoodleSystem;
import com.pzmoodle.immersion.moodle.Skill;
import com.pzmoodle.immersion.moodle.Stamina;
import com.pzmoodle.immersion.net.Packets;
import com.pzmoodle.immersion.trait.Occupation;
import com.pzmoodle.immersion.trait.OccupationTrait;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashSet;
import java.util.Set;

/**
 * 职业/特质选择的服务端逻辑。
 * 可用点数 = 职业基础点数 - 已购正面特质 + 已选负面特质返还。
 * 客户端只在玩家点「确认」时发一次整套选择，服务端整体校验后才生效，
 * 所以玩家不能在界面上随手乱点就改掉职业。
 */
public final class PlayerProfile {

    /** 已花费点数（正面特质消耗 - 负面特质返还）。 */
    public static int spentPoints(Iterable<String> traits) {
        int spent = 0;
        for (String t : traits) {
            OccupationTrait.Def def = OccupationTrait.ALL.get(t);
            if (def != null) spent += def.cost(); // cost>0 消耗，cost<0 返还
        }
        return spent;
    }

    /** 指定职业时的点数基数。traitPoints 字段是 OP 的单人覆盖值（非 0 时优先生效）。 */
    public static int basePoints(MoodleData data, String occupation) {
        if (data.traitPoints != 0) return data.traitPoints;
        if (occupation == null || occupation.isEmpty()) return CommonConfig.BASE_TRAIT_POINTS.get();
        return CommonConfig.BASE_TRAIT_POINTS.get() + Occupation.traitPoints(occupation);
    }

    /** 当前剩余可用点数。 */
    public static int availablePoints(ServerPlayer player) {
        MoodleData data = MoodleAttachments.get(player);
        return basePoints(data, data.occupation) - spentPoints(data.traits);
    }

    /**
     * 确认整套职业与特质。任何一项不合法就整体放弃，原来的选择保持不变。
     *
     * @param occupation  职业 id，可为空串（表示只选特质）
     * @param traitsCsv   逗号分隔的特质 id
     */
    public static void confirmProfile(ServerPlayer player, String occupation, String traitsCsv) {
        MoodleData data = MoodleAttachments.get(player);

        // 已经确认过且服务器不允许重选：直接忽略
        if (data.occupationChosen && !CommonConfig.ALLOW_RECHOOSE.get()) {
            Packets.sendSync(player);
            return;
        }

        if (occupation == null) occupation = "";
        if (!occupation.isEmpty() && !Occupation.exists(occupation)) {
            Packets.sendSync(player);
            return;
        }
        // 隐藏职业需要解锁；但锁定不影响已经选着的人——保持原职业继续确认不拦。
        if (Occupation.isHidden(occupation) && !data.unlockedHidden.contains(occupation)
                && !occupation.equals(data.occupation)) {
            Packets.sendSync(player);
            return;
        }

        // 解析特质：只接受存在且可选的特质；里程碑特质（无所畏惧/麻木不仁）不能被手动开关
        Set<String> requested = new HashSet<>();
        if (traitsCsv != null && !traitsCsv.isBlank()) {
            for (String raw : traitsCsv.split(",")) {
                String id = raw.trim();
                if (id.isEmpty()) continue;
                OccupationTrait.Def def = OccupationTrait.ALL.get(id);
                if (def == null || !OccupationTrait.selectable(id)) {
                    Packets.sendSync(player);
                    return;
                }
                requested.add(id);
            }
        }
        // 职业自带的 0 点特质由当前职业重新计算；只有击杀里程碑特质跨职业保留。
        requested.addAll(OccupationTrait.occupationTraits(occupation));
        for (String t : data.traits) {
            if (OccupationTrait.FEARLESS.equals(t) || OccupationTrait.NUMB.equals(t)) requested.add(t);
        }
        for (String t : requested) {
            if (OccupationTrait.conflictsWithAny(requested, t)) {
                Packets.sendSync(player);
                return;
            }
        }

        // 点数校验：整套算完不能为负
        int remaining = basePoints(data, occupation) - spentPoints(requested);
        if (remaining < 0) {
            Packets.sendSync(player);
            return;
        }

        data.occupation = occupation;
        data.traits.clear();
        data.traits.addAll(requested);
        data.occupationChosen = true;
        GrantItems.grant(player, occupation, requested);
        // 新体格/力量带来的生命与体力上限立刻生效，并把当前值直接回满
        restoreAfterProfileChange(player);
        Packets.sendSync(player);
    }

    /**
     * 职业/特质变化后的即时结算：让新带来的生命上限与体力上限立刻生效，
     * 并把当前生命值、体力直接补满到新上限（不用等下一次结算 tick 的周期）。
     */
    public static void restoreAfterProfileChange(ServerPlayer player) {
        MoodleData data = MoodleAttachments.get(player);
        // 走原版属性修饰符结算，把新体格/力量带来的 MAX_HEALTH 加成挂上
        MoodleSystem.computeLevels(player, data);
        MoodleSystem.applyEffects(player, data);
        player.setHealth(player.getMaxHealth());
        // 体力按新体格上限补满（上限由体格决定，见 Stamina.cap）
        int fitness = MoodleSystem.effectiveLevel(player, Skill.FITNESS);
        data.stamina = Stamina.cap(fitness);
        data.clamp();
    }

    private PlayerProfile() {}
}
