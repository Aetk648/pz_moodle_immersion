package com.pzmoodle.immersion.client;

import com.pzmoodle.immersion.moodle.Moodle;
import com.pzmoodle.immersion.moodle.Skill;
import com.pzmoodle.immersion.net.Packets;
import com.pzmoodle.immersion.server.PlayerProfile;
import com.pzmoodle.immersion.trait.Occupation;
import com.pzmoodle.immersion.trait.OccupationTrait;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** 客户端缓存的同步数据：只有等级与选择状态，没有隐藏数值。 */
public final class ClientState {
    public static final int[] LEVELS = new int[Moodle.ALL.length];
    public static String occupation = "";
    public static final Set<String> TRAITS = new HashSet<>();
    public static int availablePoints = 0;
    /** 服务端是否允许在确认之后重选（对应 allowRechoose 配置）。 */
    public static boolean canRechoose = true;
    /** 是否已经确认过职业与特质。 */
    public static boolean chosen = false;
    /** 管理员已解锁的隐藏职业 id 集合（服务端同步过来）。 */
    public static final Set<String> HIDDEN_UNLOCKED = new HashSet<>();
    /** 服务端同步的统计数据，用于职业面板显示。 */
    public static long zombieKills;
    public static long daysAlive;
    /** 各项技能的后天成长等级与经验（下标 = {@link com.pzmoodle.immersion.moodle.Skill#ordinal()}）。 */
    public static final int[] GROWTH_LEVELS = new int[Skill.COUNT];
    public static final int[] GROWTH_XP = new int[Skill.COUNT];
    /** 仅管理员收到的实时状态数值，范围统一为 0-100。 */
    public static final float[] RAW_VALUES = new float[Moodle.ALL.length];
    public static boolean HAS_RAW_VALUES;
    public static float stamina = 100f;
    public static float staminaCap = 100f;
    public static int encumbranceLevel;
    /** 服务端开了「低饱食度仍可奔跑」时，客户端放开原版饿肚子不能起跑的限制。 */
    public static boolean allowLowFoodSprint;

    public static void applySync(Packets.MoodleSync payload) {
        for (Moodle m : Moodle.ALL) {
            LEVELS[Moodle.index(m)] = payload.levels()[Moodle.index(m)] & 0xFF;
        }
        occupation = payload.occupation();
        TRAITS.clear();
        for (String s : payload.traits().split(",")) {
            if (!s.isBlank()) TRAITS.add(s.trim());
        }
        availablePoints = payload.points();
        canRechoose = payload.canRechoose();
        HIDDEN_UNLOCKED.clear();
        for (String id : payload.hiddenUnlocked().split(",")) {
            if (!id.isBlank()) HIDDEN_UNLOCKED.add(id.trim());
        }
        zombieKills = payload.zombieKills();
        daysAlive = payload.daysAlive();
        chosen = !occupation.isEmpty();
        copyGrowth(payload.growthLevels(), GROWTH_LEVELS);
        copyGrowth(payload.growthXp(), GROWTH_XP);
        java.util.Arrays.fill(RAW_VALUES, 0f);
        for (int i = 0; i < Math.min(RAW_VALUES.length, payload.rawValues().length); i++) {
            RAW_VALUES[i] = Math.max(0f, Math.min(100f, payload.rawValues()[i]));
        }
        HAS_RAW_VALUES = payload.rawValues().length > 0;
        stamina = Math.max(0f, payload.stamina());
        staminaCap = Math.max(1f, payload.staminaCap());
        encumbranceLevel = Math.max(0, Math.min(4, payload.encumbranceLevel()));
        allowLowFoodSprint = payload.allowLowFoodSprint();
    }

    /** 服务端与客户端技能表长度不一致时（版本差异）只取公共部分，避免越界。 */
    private static void copyGrowth(int[] from, int[] to) {
        java.util.Arrays.fill(to, 0);
        for (int i = 0; i < Math.min(from.length, to.length); i++) to[i] = from[i];
    }

    public static void clear() {
        Arrays.fill(LEVELS, 0);
        occupation = "";
        TRAITS.clear();
        availablePoints = 0;
        canRechoose = true;
        chosen = false;
        HIDDEN_UNLOCKED.clear();
        zombieKills = 0;
        daysAlive = 0;
        Arrays.fill(GROWTH_LEVELS, 0);
        Arrays.fill(GROWTH_XP, 0);
        Arrays.fill(RAW_VALUES, 0f);
        HAS_RAW_VALUES = false;
        stamina = 100f;
        staminaCap = 100f;
        encumbranceLevel = 0;
        allowLowFoodSprint = false;
    }

    public static int level(Moodle m) {
        return LEVELS[Moodle.index(m)];
    }

    /** 某隐藏职业是否已被管理员解锁。 */
    public static boolean isHiddenUnlocked(String occupationId) {
        return HIDDEN_UNLOCKED.contains(occupationId);
    }

    /** 某项技能的最终数值 = 职业基础 + 特质加成 + 后天成长，压在 0-10。 */
    public static int effective(Skill skill) {
        return Occupation.combine(Occupation.stat(occupation, skill),
                OccupationTrait.traitBonus(TRAITS, skill), growthLevel(skill));
    }

    public static int growthLevel(Skill skill) {
        return GROWTH_LEVELS[skill.ordinal()];
    }

    public static int growthXp(Skill skill) {
        return GROWTH_XP[skill.ordinal()];
    }

    /** 三项主数值（力量/体格/冲刺）之和，作为给玩家看的「人物等级」。 */
    public static int characterLevel() {
        return effective(Skill.STRENGTH)
                + effective(Skill.FITNESS)
                + effective(Skill.SPRINTING);
    }

    /** 人物等级上限：三项主数值各 10 级。 */
    public static int characterMaxLevel() {
        return Skill.MAX_LEVEL * 3;
    }

    /**
     * 反推「世界基础点数 + OP 覆盖」那部分：
     * 已知 availablePoints = 基数 + 当前职业点数 - 已花点数，所以基数可以算回来。
     * 这样职业界面在本地就能算出「换成别的职业后还剩多少点」。
     */
    public static int basePoints() {
        return availablePoints + PlayerProfile.spentPoints(TRAITS) - Occupation.traitPoints(occupation);
    }

    /** 按「假设选了某个职业、某些特质」计算剩余点数。 */
    public static int pendingPoints(String pendingOccupation, Iterable<String> pendingTraits) {
        return basePoints() + Occupation.traitPoints(pendingOccupation) - PlayerProfile.spentPoints(pendingTraits);
    }

    private ClientState() {}
}
