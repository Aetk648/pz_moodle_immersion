package com.pzmoodle.immersion.moodle;

import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.common.util.INBTSerializable;

/**
 * 僵毁式人物成长：每项技能都有「后天等级 + 累计经验」。
 * <p>
 * 经验曲线按《技能 - PZwiki》41.78.16 的两张表换算成累计门槛：
 * 被动技能（力量、体能）1500、3000、6000、9000、18000、30000、60000、90000、120000、150000；
 * 常规技能 75、150、300、750、1500、3000、4500、6000、7500、9000。
 */
public class StatData implements INBTSerializable<CompoundTag> {
    public static final int MAX_LEVEL = Skill.MAX_LEVEL;

    /** 被动技能：1500、3000、6000……逐级累计。 */
    private static final int[] PASSIVE_XP = {
            0, 1500, 4500, 10500, 19500, 37500, 67500, 127500, 217500, 337500, 487500
    };
    /** 常规技能：75、150、300……逐级累计。 */
    private static final int[] REGULAR_XP = {
            0, 75, 225, 525, 1275, 2775, 5775, 10275, 16275, 23775, 32775
    };

    private final int[] levels = new int[Skill.COUNT];
    private final int[] xp = new int[Skill.COUNT];

    public static int[] curve(Skill skill) {
        return skill.passive ? PASSIVE_XP : REGULAR_XP;
    }

    public static int xpForLevel(Skill skill, int level) {
        return curve(skill)[Math.max(0, Math.min(MAX_LEVEL, level))];
    }

    /** 当前等级内已经攒了多少经验（用于画那一格的部分填充）。 */
    public static int xpIntoLevel(Skill skill, int level, int xp) {
        int base = xpForLevel(skill, level);
        int next = xpForLevel(skill, Math.min(MAX_LEVEL, level + 1));
        return Math.max(0, Math.min(xp - base, next - base));
    }

    public static int xpRangeForLevel(Skill skill, int level) {
        int base = xpForLevel(skill, level);
        int next = xpForLevel(skill, Math.min(MAX_LEVEL, level + 1));
        return Math.max(1, next - base);
    }

    public int level(Skill skill) {
        return levels[skill.ordinal()];
    }

    public int xp(Skill skill) {
        return xp[skill.ordinal()];
    }

    /** 通用加经验入口：自动处理升级与满级封顶。 */
    public boolean addXp(Skill skill, int amount) {
        if (skill == null || amount <= 0) return false;
        int i = skill.ordinal();
        int next = xp[i] + amount;
        int level = levels[i];
        while (level < MAX_LEVEL && next >= xpForLevel(skill, level + 1)) {
            level++;
        }
        next = Math.min(next, xpForLevel(skill, MAX_LEVEL));
        boolean changed = next != xp[i] || level != levels[i];
        xp[i] = next;
        levels[i] = level;
        return changed;
    }

    // ———— 旧接口：保留给已有事件代码使用 ————

    public void addStrength(int amount) {
        addXp(Skill.STRENGTH, amount);
    }

    public void addFitness(int amount) {
        addXp(Skill.FITNESS, amount);
    }

    public void addSprinting(int amount) {
        addXp(Skill.SPRINTING, amount);
    }

    public void addLightfooted(int amount) {
        addXp(Skill.LIGHTFOOTED, amount);
    }

    public void addNimble(int amount) {
        addXp(Skill.NIMBLE, amount);
    }

    public void addAiming(int amount) {
        addXp(Skill.AIMING, amount);
    }

    /** 新的一命开始时清空所有后天技能等级与经验。 */
    public void reset() {
        java.util.Arrays.fill(levels, 0);
        java.util.Arrays.fill(xp, 0);
    }

    @Override
    public CompoundTag serializeNBT(net.minecraft.core.HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putIntArray("levels", levels);
        tag.putIntArray("xp", xp);
        return tag;
    }

    @Override
    public void deserializeNBT(net.minecraft.core.HolderLookup.Provider registries, CompoundTag tag) {
        if (tag.contains("levels")) {
            copyIn(tag.getIntArray("levels"), levels);
            copyIn(tag.getIntArray("xp"), xp);
            clampAll();
            return;
        }
        // 老存档：只有力量/体格/冲刺(旧名 speed)/轻巧/灵活/瞄准六项，逐项迁移
        readLegacy(tag, Skill.STRENGTH, "strength");
        readLegacy(tag, Skill.FITNESS, "fitness");
        readLegacy(tag, Skill.SPRINTING, "sprinting", "speed");
        readLegacy(tag, Skill.LIGHTFOOTED, "lightfooted");
        readLegacy(tag, Skill.NIMBLE, "nimble");
        readLegacy(tag, Skill.AIMING, "aiming");
        clampAll();
    }

    private void readLegacy(CompoundTag tag, Skill skill, String... names) {
        for (String n : names) {
            if (tag.contains(n + "Level")) {
                levels[skill.ordinal()] = tag.getInt(n + "Level");
                xp[skill.ordinal()] = tag.getInt(n + "Xp");
                return;
            }
        }
    }

    private void copyIn(int[] from, int[] to) {
        for (int i = 0; i < Math.min(from.length, to.length); i++) to[i] = from[i];
    }

    private void clampAll() {
        for (Skill s : Skill.ALL) {
            int i = s.ordinal();
            levels[i] = Math.max(0, Math.min(MAX_LEVEL, levels[i]));
            xp[i] = Math.max(0, Math.min(xpForLevel(s, MAX_LEVEL), xp[i]));
        }
    }

    public StatData() {
    }
}
