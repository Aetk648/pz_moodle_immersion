package com.pzmoodle.immersion.moodle;

import java.util.ArrayList;
import java.util.List;

/**
 * 全部人物技能，顺序与分组完全照《僵尸毁灭工程》B42 的技能窗口：
 * 战斗-火器 → 战斗-近战 → 制作 → 农耕 → 体能 → 生存。
 * <p>
 * 枚举的声明顺序就是界面上的显示顺序，同时 {@link #ordinal()} 直接当作
 * 存档与网络同步里的下标用，所以<b>不要随意调整顺序</b>（会让老存档的技能经验错位）。
 * 曲线沿用 PZwiki 41.78.16 的两张经验表：力量与体能属于被动曲线，其余都是常规曲线。
 */
public enum Skill {
    // ———— 战斗 - 火器 ————
    AIMING("aiming", Group.FIREARMS, false),
    RELOADING("reloading", Group.FIREARMS, false),

    // ———— 战斗 - 近战 ————
    AXE("axe", Group.MELEE, false),
    LONG_BLADE("long_blade", Group.MELEE, false),
    LONG_BLUNT("long_blunt", Group.MELEE, false),
    MAINTENANCE("maintenance", Group.MELEE, false),
    SHORT_BLADE("short_blade", Group.MELEE, false),
    SHORT_BLUNT("short_blunt", Group.MELEE, false),
    SPEAR("spear", Group.MELEE, false),

    // ———— 制作 ————
    BLACKSMITHING("blacksmithing", Group.CRAFTING, false),
    CARPENTRY("carpentry", Group.CRAFTING, false),
    CARVING("carving", Group.CRAFTING, false),
    COOKING("cooking", Group.CRAFTING, false),
    ELECTRICAL("electrical", Group.CRAFTING, false),
    GLASSMAKING("glassmaking", Group.CRAFTING, false),
    KNAPPING("knapping", Group.CRAFTING, false),
    MASONRY("masonry", Group.CRAFTING, false),
    MECHANICS("mechanics", Group.CRAFTING, false),
    POTTERY("pottery", Group.CRAFTING, false),
    TAILORING("tailoring", Group.CRAFTING, false),
    WELDING("welding", Group.CRAFTING, false),

    // ———— 农耕 ————
    AGRICULTURE("agriculture", Group.FARMING, false),
    ANIMAL_CARE("animal_care", Group.FARMING, false),
    BUTCHERING("butchering", Group.FARMING, false),

    // ———— 体能（僵毁的「被动」类别）————
    FITNESS("fitness", Group.PHYSICAL, true),
    LIGHTFOOTED("lightfooted", Group.PHYSICAL, false),
    NIMBLE("nimble", Group.PHYSICAL, false),
    SPRINTING("sprinting", Group.PHYSICAL, false),
    SNEAKING("sneaking", Group.PHYSICAL, false),
    STRENGTH("strength", Group.PHYSICAL, true),

    // ———— 生存 ————
    FIRST_AID("first_aid", Group.SURVIVAL, false),
    FISHING("fishing", Group.SURVIVAL, false),
    FORAGING("foraging", Group.SURVIVAL, false),
    TRACKING("tracking", Group.SURVIVAL, false),
    TRAPPING("trapping", Group.SURVIVAL, false);

    /** 等级上限，与僵毁一致。 */
    public static final int MAX_LEVEL = 10;
    /** 技能总数（存档与同步数组的长度）。 */
    public static final int COUNT = values().length;

    public static final Skill[] ALL = values();

    /** 技能分组，顺序即界面分组的先后。 */
    public enum Group {
        FIREARMS("combat_firearms"),
        MELEE("combat_melee"),
        CRAFTING("crafting"),
        FARMING("farming"),
        PHYSICAL("physical"),
        SURVIVAL("survival");

        public final String key;

        Group(String key) {
            this.key = "screen.pz_moodle_immersion.skill_group." + key;
        }
    }

    /** 语言文件与存档里用的稳定 id。 */
    public final String id;
    public final Group group;
    /** 是否走被动经验曲线（力量、体能）。 */
    public final boolean passive;

    Skill(String id, Group group, boolean passive) {
        this.id = id;
        this.group = group;
        this.passive = passive;
    }

    public String nameKey() {
        return "skill.pz_moodle_immersion." + id;
    }

    public String shortKey() {
        return nameKey() + ".short";
    }

    public String descKey() {
        return nameKey() + ".desc";
    }

    public static List<Skill> inGroup(Group group) {
        List<Skill> out = new ArrayList<>();
        for (Skill s : ALL) if (s.group == group) out.add(s);
        return out;
    }

    /** 按存档 id 找技能；找不到返回 null。 */
    public static Skill byId(String id) {
        for (Skill s : ALL) if (s.id.equals(id)) return s;
        return null;
    }
}
