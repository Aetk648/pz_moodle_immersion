package com.pzmoodle.immersion.trait;

import com.pzmoodle.immersion.moodle.Skill;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 职业定义。参照僵毁 B42 的职业表，给出特质点数、完整的「人物数值」与一句加成描述。
 * 人物数值为僵毁式 0-10 分，普通人基准 5 分；
 * 高于 5 分给加成，低于 5 分给惩罚，具体换算见 MoodleSystem。
 * <p>
 * 除了六项体能数值，每个职业还带着僵毁式的「主修技能」等级（木工、瞄准、急救……），
 * 这些会直接计入物品栏与职业界面里的技能等级显示。
 */
public final class Occupation {
    /** 普通人（无职业）时的基准分值。 */
    public static final int BASE_STAT = 5;
    /** 人物数值上限（僵毁式 0-10）。 */
    public static final int MAX_STAT = 10;

    /**
     * 六项体能数值。设定里没写的项一律为 0 级，没有默认等级。
     *
     * @param strength    力量：影响攻击力与挖掘速度
     * @param fitness     体格：影响最大生命
     * @param sprinting   冲刺：影响移动速度
     * @param lightfooted 轻巧：影响潜行移动速度
     * @param nimble      灵活：影响攻击速度
     * @param aiming      瞄准：影响弓箭/弩伤害
     */
    public record Stats(int strength, int fitness, int sprinting, int lightfooted, int nimble, int aiming) {
        public static final Stats ZERO = new Stats(0, 0, 0, 0, 0, 0);

        /** 相对基准 5 分的差值，可为负。 */
        public int over(int value) {
            return value - BASE_STAT;
        }
    }

    public record Def(String id, int traitPoints, String nameKey, String bonusKey, Stats stats,
                      Map<Skill, Integer> majors) {
        public Def(String id, int traitPoints, String nameKey, String bonusKey, Stats stats) {
            this(id, traitPoints, nameKey, bonusKey, stats, Map.of());
        }
    }

    public static final String PREPPER = "prepper";
    public static final String BOXER = "boxer";
    public static final String VILLAGE_HERO = "village_hero";
    /** 隐藏职业：需要管理员解锁后才能选。 */
    public static final Set<String> HIDDEN = Set.of(PREPPER, BOXER, VILLAGE_HERO);
    /** 隐藏职业在命令里的中文名：/生存狂、/拳击手、/村庄英雄。 */
    public static final Map<String, String> HIDDEN_COMMAND_NAMES = Map.of(
            PREPPER, "生存狂", BOXER, "拳击手", VILLAGE_HERO, "村庄英雄");
    public static final Map<String, Def> ALL = new LinkedHashMap<>();
    // 数值与主修技能等级表照《主菜单选择职业界面职业特质描述以及数值平衡说明》逐条录入：
    // 说明里没写的数值/技能一律为 0 级，没有默认等级；只有说明里写明的才有等级。
    static {
        reg(new Def("unemployed", 10, key("unemployed"), key("unemployed.bonus"),
                new Stats(5, 5, 0, 0, 0, 0)));                                          // 力量5 体格5 冲刺5
        reg(new Def("firefighter", 0, key("firefighter"), key("firefighter.bonus"),
                new Stats(8, 8, 6, 5, 5, 0)));                                          // 力量8 体格8 冲刺6 轻巧5 灵活5
        reg(new Def("police", -4, key("police"), key("police.bonus"),
                new Stats(6, 6, 6, 5, 6, 8),
                majors(Skill.RELOADING, 10)));                                          // 力量6 体格6 冲刺6 轻巧5 灵活6 瞄准8 换弹10
        reg(new Def("carpenter", 1, key("carpenter"), key("carpenter.bonus"),
                new Stats(5, 5, 0, 0, 0, 0),
                majors(Skill.CARPENTRY, 10, Skill.CARVING, 8, Skill.BLACKSMITHING, 5, Skill.SHORT_BLUNT, 5)));
                                                                                                // 力量5 体格5 木工10 雕刻8 锻造5 短钝器5
        reg(new Def("farmer", 2, key("farmer"), key("farmer.bonus"),
                new Stats(5, 6, 0, 0, 0, 0),
                majors(Skill.AGRICULTURE, 10, Skill.MAINTENANCE, 5)));                  // 力量5 体格6 农耕10 保养5
        reg(new Def("doctor", -2, key("doctor"), key("doctor.bonus"),
                new Stats(4, 5, 0, 5, 5, 0),
                majors(Skill.FIRST_AID, 10, Skill.SHORT_BLADE, 8)));                    // 力量4 体格5 轻巧5 灵活5 急救10 短刀8
        reg(new Def("engineer", -1, key("engineer"), key("engineer.bonus"),
                new Stats(5, 5, 0, 0, 0, 0),
                majors(Skill.MECHANICS, 8, Skill.MAINTENANCE, 7, Skill.ELECTRICAL, 7, Skill.WELDING, 7)));
                                                                                                // 力量5 体格5 机械8 保养7 电器7 焊接7
        reg(new Def("hobo", 14, key("hobo"), key("hobo.bonus"),
                new Stats(4, 4, 0, 0, 0, 0),
                majors(Skill.FORAGING, 6)));                                            // 力量4 体格4 觅食6
        reg(new Def("lumberjack", 1, key("lumberjack"), key("lumberjack.bonus"),
                new Stats(7, 7, 0, 0, 0, 0),
                majors(Skill.AXE, 10)));                                                // 力量7 体格7 斧头10
        reg(new Def("nurse", -1, key("nurse"), key("nurse.bonus"),
                new Stats(3, 4, 0, 6, 6, 0),
                majors(Skill.FIRST_AID, 8, Skill.MAINTENANCE, 7)));                     // 力量3 体格4 轻巧6 灵活6 急救8 保养7
        reg(new Def("rancher", 2, key("rancher"), key("rancher.bonus"),
                new Stats(5, 5, 0, 0, 0, 0),
                majors(Skill.ANIMAL_CARE, 10, Skill.BUTCHERING, 8, Skill.LONG_BLUNT, 5)));
                                                                                                // 力量5 体格5 动物照料10 屠宰8 长钝器5
        reg(new Def("brewer", 10, key("brewer"), key("brewer.bonus"),
                new Stats(5, 5, 0, 0, 0, 0),
                majors(Skill.COOKING, 2)));                                             // 力量5 体格5 烹饪2
        reg(new Def("fishing_guide", 1, key("fishing_guide"), key("fishing_guide.bonus"),
                new Stats(5, 5, 2, 0, 0, 0),
                majors(Skill.FISHING, 10)));                                            // 力量5 体格5 冲刺2 钓鱼10
        reg(new Def("electrician", 0, key("electrician"), key("electrician.bonus"),
                new Stats(5, 5, 0, 0, 0, 0),
                majors(Skill.ELECTRICAL, 10, Skill.WELDING, 5)));                       // 力量5 体格5 电器10 焊接5
        reg(new Def("construction_worker", 1, key("construction_worker"), key("construction_worker.bonus"),
                new Stats(7, 7, 2, 0, 0, 0),
                majors(Skill.CARPENTRY, 8, Skill.MASONRY, 8, Skill.KNAPPING, 8, Skill.SHORT_BLUNT, 5)));
                                                                                                // 力量7 体格7 冲刺2 建造8 石工8 打制石器8 短钝器5
        reg(new Def("maintenance", 1, key("maintenance"), key("maintenance.bonus"),
                new Stats(4, 5, 0, 0, 0, 0),
                majors(Skill.MAINTENANCE, 10, Skill.SPEAR, 3, Skill.LONG_BLADE, 3,
                        Skill.CARVING, 3, Skill.GLASSMAKING, 3)));                      // 力量4 体格5 保养10 长矛3 长刀3 雕刻3 玻璃制作3
        reg(new Def("chef", 0, key("chef"), key("chef.bonus"),
                new Stats(4, 4, 0, 0, 3, 0),
                majors(Skill.COOKING, 10, Skill.BUTCHERING, 4)));                       // 力量4 体格4 灵活3 烹饪10 屠宰4
        reg(new Def("mechanic", 0, key("mechanic"), key("mechanic.bonus"),
                new Stats(5, 5, 0, 0, 0, 0),
                majors(Skill.MECHANICS, 10, Skill.ELECTRICAL, 8)));                     // 力量5 体格5 机械10 电器8
        reg(new Def("welder", 0, key("welder"), key("welder.bonus"),
                new Stats(5, 4, 0, 0, 0, 0),
                majors(Skill.WELDING, 10, Skill.LONG_BLUNT, 5, Skill.MAINTENANCE, 4))); // 力量5 体格4 焊接10 长钝器5 保养4
        reg(new Def("burglar", 2, key("burglar"), key("burglar.bonus"),
                new Stats(3, 4, 8, 8, 10, 0),
                majors(Skill.SNEAKING, 10, Skill.SHORT_BLADE, 6)));                     // 力量3 体格4 冲刺8 轻巧8 灵活10 潜行10 短刀6
        reg(new Def("fitness_instructor", 0, key("fitness_instructor"), key("fitness_instructor.bonus"),
                new Stats(10, 10, 2, 0, 0, 0)));                                        // 力量10 体格10 冲刺2
        reg(new Def("veteran", -2, key("veteran"), key("veteran.bonus"),
                new Stats(8, 8, 8, 6, 8, 10),
                majors(Skill.RELOADING, 10, Skill.SHORT_BLADE, 8,
                        Skill.SNEAKING, 6, Skill.FIRST_AID, 5)));                       // 力量8 体格8 冲刺8 轻巧6 灵活8 瞄准10 急救5 短刀8 潜行6 换弹10
        reg(new Def("blacksmith", 0, key("blacksmith"), key("blacksmith.bonus"),
                new Stats(5, 5, 0, 0, 0, 0),
                majors(Skill.BLACKSMITHING, 10, Skill.MAINTENANCE, 6, Skill.CARVING, 5)));
                                                                                                // 力量5 体格5 锻造10 保养6 雕刻5
        reg(new Def("tailor", 5, key("tailor"), key("tailor.bonus"),
                new Stats(3, 4, 0, 5, 0, 0),
                majors(Skill.TAILORING, 10)));                                          // 力量3 体格4 轻巧5 裁缝10
        reg(new Def("burger_flipper", 9, key("burger_flipper"), key("burger_flipper.bonus"),
                new Stats(4, 3, 0, 0, 0, 0),
                majors(Skill.COOKING, 8)));                                             // 力量4 体格3 烹饪8
        reg(new Def("security_guard", 1, key("security_guard"), key("security_guard.bonus"),
                new Stats(6, 7, 6, 0, 0, 0),
                majors(Skill.LONG_BLADE, 5, Skill.LONG_BLUNT, 5, Skill.SPEAR, 5)));     // 力量6 体格7 冲刺6 长刀5 长钝器5 长矛5
        reg(new Def("park_ranger", 1, key("park_ranger"), key("park_ranger.bonus"),
                new Stats(6, 6, 5, 6, 6, 0),
                majors(Skill.FORAGING, 8, Skill.FISHING, 6, Skill.MAINTENANCE, 5, Skill.AXE, 3)));
                                                                                                // 力量6 体格6 冲刺5 轻巧6 灵活6 觅食8 钓鱼6 保养5 斧头3
        reg(new Def(BOXER, 0, key(BOXER), key("boxer.bonus"),
                new Stats(10, 9, 8, 8, 8, 0)));                                         // 力量10 体格9 冲刺8 轻巧8 灵活8
        reg(new Def(VILLAGE_HERO, 0, key(VILLAGE_HERO), key("village_hero.bonus"),
                new Stats(7, 7, 7, 0, 0, 0)));                                          // 力量7 体格7 冲刺7（设定只给了这三项，其余没有默认等级）
        reg(new Def(PREPPER, 100, key(PREPPER), key("prepper.bonus"),
                new Stats(MAX_STAT, MAX_STAT, MAX_STAT, MAX_STAT, MAX_STAT, MAX_STAT),
                allSkills()));                                                          // 等级全部满级
    }

    private static String key(String suffix) {
        return "occupation.pz_moodle_immersion." + suffix;
    }

    private static void reg(Def d) { ALL.put(d.id, d); }

    private static Map<Skill, Integer> allSkills() {
        Map<Skill, Integer> m = new EnumMap<>(Skill.class);
        for (Skill skill : Skill.ALL) m.put(skill, Skill.MAX_LEVEL);
        return Map.copyOf(m);
    }

    public static boolean isPrepper(String id) { return PREPPER.equals(id); }

    /** 是否隐藏职业（默认不出现在选择界面，需管理员解锁）。 */
    public static boolean isHidden(String id) { return HIDDEN.contains(id); }

    /** 成对写主修技能：{@code majors(Skill.AXE, 3, Skill.MAINTENANCE, 1)}。 */
    private static Map<Skill, Integer> majors(Object... pairs) {
        Map<Skill, Integer> m = new EnumMap<>(Skill.class);
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            m.put((Skill) pairs[i], (Integer) pairs[i + 1]);
        }
        return Map.copyOf(m);
    }

    public static int traitPoints(String id) {
        Def d = ALL.get(id);
        return d == null ? 0 : d.traitPoints;
    }

    public static boolean exists(String id) {
        return ALL.containsKey(id);
    }

    /** 职业的人物数值；未知职业按无业游民处理，没有额外默认等级。 */
    public static Stats stats(String id) {
        Def d = ALL.get(id);
        return d == null ? ALL.get("unemployed").stats() : d.stats();
    }

    public static int strength(String id) { return stats(id).strength(); }
    public static int fitness(String id) { return stats(id).fitness(); }
    public static int sprinting(String id) { return stats(id).sprinting(); }
    public static int lightfooted(String id) { return stats(id).lightfooted(); }
    public static int nimble(String id) { return stats(id).nimble(); }
    public static int aiming(String id) { return stats(id).aiming(); }

    /**
     * 某项技能的职业基础值。
     * 设定里写明的等级照写，没写的一律 0 级，没有默认等级。
     */
    public static int stat(String id, Skill skill) {
        Def d = ALL.get(id);
        if (d == null) d = ALL.get("unemployed");
        Stats s = d.stats();
        int legacy = switch (skill) {
            case STRENGTH -> s.strength();
            case FITNESS -> s.fitness();
            case SPRINTING -> s.sprinting();
            case LIGHTFOOTED -> s.lightfooted();
            case NIMBLE -> s.nimble();
            case AIMING -> s.aiming();
            default -> -1;
        };
        if (legacy >= 0) return legacy;
        return d.majors().getOrDefault(skill, 0);
    }

    /** 六项体能数值：没有基准 5 分这回事的技能返回 0。 */
    public static boolean isStatTrack(Skill skill) {
        return switch (skill) {
            case STRENGTH, FITNESS, SPRINTING, LIGHTFOOTED, NIMBLE, AIMING -> true;
            default -> false;
        };
    }

    /**
     * 把「职业基础值 + 特质加成 + 后天成长等级」合成一项最终人物数值。
     * 结果压在 0-10 之间，和僵毁的属性上限一致。
     */
    public static int combine(int base, int traitBonus, int growthLevel) {
        return Math.max(0, Math.min(MAX_STAT, base + traitBonus + growthLevel));
    }

    /** 某一项数值相对普通人基准（5）的倍率，1.0 表示没有变化。 */
    public static double overMultiplier(int value, double perPoint) {
        return 1.0 + (value - BASE_STAT) * perPoint;
    }

    /** 力量/体格曲线：0级 50%，5级 100%，10级 200%。 */
    public static double lowToHighMultiplier(int value) {
        int v = Math.max(0, Math.min(MAX_STAT, value));
        return v <= BASE_STAT ? 0.5 + v * 0.10 : 1.0 + (v - BASE_STAT) * 0.20;
    }

    /** 冲刺曲线：0级不加速，每级增加5%，10级增加50%。 */
    public static double sprintMultiplier(int value) {
        return 1.0 + Math.max(0, Math.min(MAX_STAT, value)) * 0.05;
    }

    // —— 由人物数值换算出的属性倍率（1.0 = 无变化）——

    /** 力量：高于 5 分提高攻击力，低于 5 分降低。倍率 = 1 + (力量-5) x 每点系数。 */
    public static double attackMultiplier(String id, double perPoint) {
        return 1.0 + stats(id).over(strength(id)) * perPoint;
    }

    public static double mineMultiplier(String id, double perPoint) {
        return 1.0 + stats(id).over(strength(id)) * perPoint;
    }

    public static double healthMultiplier(String id, double perPoint) {
        return 1.0 + stats(id).over(fitness(id)) * perPoint;
    }

    public static double speedMultiplier(String id, double perPoint) {
        return 1.0 + stats(id).over(sprinting(id)) * perPoint;
    }

    public static double attackSpeedMultiplier(String id, double perPoint) {
        return 1.0 + stats(id).over(nimble(id)) * perPoint;
    }

    public static double sneakMultiplier(String id, double perPoint) {
        return 1.0 + stats(id).over(lightfooted(id)) * perPoint;
    }

    public static double projectileMultiplier(String id, double perPoint) {
        return 1.0 + stats(id).over(aiming(id)) * perPoint;
    }

    private Occupation() {}
}
