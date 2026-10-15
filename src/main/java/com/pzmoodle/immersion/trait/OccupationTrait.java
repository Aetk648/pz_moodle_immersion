package com.pzmoodle.immersion.trait;

import com.pzmoodle.immersion.PZMoodleImmersion;
import com.pzmoodle.immersion.admin.DefOverrides;
import com.pzmoodle.immersion.moodle.MoodleData;
import com.pzmoodle.immersion.moodle.Skill;
import net.minecraft.resources.ResourceLocation;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Collections;

/**
 * 僵毁式正面/负面特质。每个特质有点数消耗（正数=消耗，负数=返还）、
 * 一个图标贴图与一个可查询的「效果标志」，由 MoodleSystem / 事件层读取应用。
 */
public final class OccupationTrait {
    // —— 正面 ——
    public static final String SPEED_DEMON = "speed_demon";
    public static final String OUTDOORSMAN = "outdoorsman";
    public static final String DEXTEROUS = "dexterous";
    public static final String LESS_SLEEP = "less_sleep";
    public static final String CATS_EYE = "cats_eye";
    public static final String BRAVE = "brave";
    public static final String GRACEFUL = "graceful";
    public static final String ORGANIZED = "organized";
    public static final String KEEN_HEARING = "keen_hearing";
    public static final String ATHLETIC = "athletic";
    public static final String STRONG = "strong";
    public static final String LUCKY = "lucky";
    public static final String INCONSPICUOUS = "inconspicuous";
    public static final String EAGLE_EYED = "eagle_eyed";
    public static final String IRON_GUT = "iron_gut";
    public static final String FAST_READER = "fast_reader";
    public static final String RESILIENT = "resilient";

    // —— 负面 ——
    public static final String SMOKER = "smoker";
    public static final String DRUNKARD = "drunkard";
    public static final String CLUMSY = "clumsy";
    public static final String COWARDLY = "cowardly";
    public static final String ALL_THUMBS = "all_thumbs";
    public static final String SHORT_SIGHTED = "short_sighted";
    public static final String SLOW_READER = "slow_reader";
    public static final String SLOW_HEALER = "slow_healer";
    public static final String WEAK_STOMACH = "weak_stomach";
    public static final String CONSPICUOUS = "conspicuous";
    public static final String HARD_OF_HEARING = "hard_of_hearing";
    public static final String CLAUSTROPHOBIC = "claustrophobic";
    public static final String AGORAPHOBIC = "agoraphobic";
    public static final String SLEEPYHEAD = "sleepyhead";
    public static final String PRONE_TO_ILLNESS = "prone_to_illness";
    public static final String PACIFIST = "pacifist";
    public static final String HEARTY_APPETITE = "hearty_appetite";
    public static final String ASTHMATIC = "asthmatic";
    public static final String HEMOPHOBIA = "hemophobia";
    public static final String SLOW_LEARNER = "slow_learner";
    public static final String DISORGANIZED = "disorganized";
    public static final String UNDERWEIGHT = "underweight";
    public static final String UNLUCKY = "unlucky";
    public static final String ILLITERATE = "illiterate";
    public static final String HIGH_THIRST = "high_thirst";
    public static final String SUNDAY_DRIVER = "sunday_driver";

    // —— 职业专属 ——
    public static final String AXEMAN = "axeman";
    public static final String HANDY = "handy";
    public static final String THICK_SKINNED = "thick_skinned";
    public static final String PATIENT = "patient";
    public static final String SHORT_TEMPER = "short_temper";
    public static final String MARKSMAN = "marksman";
    public static final String NIGHT_OWL = "night_owl";
    /** 拳击手专属：受伤后短时间大幅提高攻击力。 */
    public static final String BLOODY_FURY = "bloody_fury";
    /** 村庄英雄专属：完全不受到来自其他玩家的伤害。 */
    public static final String BELOVED = "beloved";
    /** 兼容旧版本逻辑名称。 */
    public static final String IRON_STOMACH = IRON_GUT;
    public static final String FRAIL = UNDERWEIGHT;
    public static final String CAT_EYES = CATS_EYE;

    // —— 自动获得（击杀里程碑）——
    public static final String FEARLESS = "fearless";
    public static final String NUMB = "numb";

    /** icon 为 textures/gui/ 下的相对路径（不含 .png）。 */
    public record Def(String id, int cost, boolean positive, String nameKey, String icon) {
        public String descKey() {
            return nameKey + ".desc";
        }

        public ResourceLocation texture() {
            return PZMoodleImmersion.id("textures/gui/" + icon + ".png");
        }
    }

    public static final Map<String, Def> ALL = new LinkedHashMap<>();
    static {
        // 图标全部换成 PZ 官方特质图（PZwiki 素材，18x18），一特质一名，不再借用。
        reg(new Def(SPEED_DEMON, 2, true, key(SPEED_DEMON), "trait/speeddemon"));
        reg(new Def(OUTDOORSMAN, 2, true, key(OUTDOORSMAN), "trait/outdoorsman"));
        reg(new Def(DEXTEROUS, 2, true, key(DEXTEROUS), "trait/dextrous"));
        reg(new Def(LESS_SLEEP, 2, true, key(LESS_SLEEP), "trait/needslesssleep"));
        reg(new Def(CATS_EYE, 2, true, key(CATS_EYE), "trait/nightvision"));
        reg(new Def(BRAVE, 4, true, key(BRAVE), "trait/brave"));
        reg(new Def(GRACEFUL, 4, true, key(GRACEFUL), "trait/graceful"));
        reg(new Def(ORGANIZED, 6, true, key(ORGANIZED), "trait/organized"));
        reg(new Def(KEEN_HEARING, 6, true, key(KEEN_HEARING), "trait/keenhearing"));
        reg(new Def(ATHLETIC, 10, true, key(ATHLETIC), "trait/athletic"));
        reg(new Def(STRONG, 6, true, key(STRONG), "trait/strong"));
        reg(new Def(LUCKY, 4, true, key(LUCKY), "trait/lucky"));
        reg(new Def(INCONSPICUOUS, 4, true, key(INCONSPICUOUS), "trait/inconspicuous"));
        reg(new Def(EAGLE_EYED, 6, true, key(EAGLE_EYED), "trait/eagleeyed"));
        reg(new Def(IRON_GUT, 3, true, key(IRON_GUT), "trait/irongut"));
        reg(new Def(FAST_READER, 2, true, key(FAST_READER), "trait/fastreader"));
        reg(new Def(RESILIENT, 4, true, key(RESILIENT), "trait/resilient"));

        reg(new Def(SMOKER, -4, false, key(SMOKER), "trait/smoker"));
        reg(new Def(DRUNKARD, -8, false, key(DRUNKARD), "trait/drunkard"));
        reg(new Def(CLUMSY, -2, false, key(CLUMSY), "trait/clumsy"));
        reg(new Def(COWARDLY, -2, false, key(COWARDLY), "trait/cowardly"));
        reg(new Def(ALL_THUMBS, -2, false, key(ALL_THUMBS), "trait/allthumbs"));
        reg(new Def(SHORT_SIGHTED, -2, false, key(SHORT_SIGHTED), "trait/shortsighted"));
        reg(new Def(SLOW_READER, -2, false, key(SLOW_READER), "trait/slowreader"));
        reg(new Def(SLOW_HEALER, -6, false, key(SLOW_HEALER), "trait/slowhealer"));
        reg(new Def(WEAK_STOMACH, -3, false, key(WEAK_STOMACH), "trait/weakstomach"));
        reg(new Def(CONSPICUOUS, -4, false, key(CONSPICUOUS), "trait/conspicuous"));
        reg(new Def(HARD_OF_HEARING, -4, false, key(HARD_OF_HEARING), "trait/hardofhearing"));
        reg(new Def(CLAUSTROPHOBIC, -4, false, key(CLAUSTROPHOBIC), "trait/claustophobic"));
        reg(new Def(AGORAPHOBIC, -4, false, key(AGORAPHOBIC), "trait/agoraphobic"));
        reg(new Def(SLEEPYHEAD, -4, false, key(SLEEPYHEAD), "trait/needsmoresleep"));
        reg(new Def(PRONE_TO_ILLNESS, -4, false, key(PRONE_TO_ILLNESS), "trait/hypercondriac"));
        reg(new Def(PACIFIST, -4, false, key(PACIFIST), "trait/pacifist"));
        reg(new Def(HEARTY_APPETITE, -4, false, key(HEARTY_APPETITE), "trait/heartyappetite"));
        reg(new Def(ASTHMATIC, -5, false, key(ASTHMATIC), "trait/asthmatic"));
        reg(new Def(HEMOPHOBIA, -5, false, key(HEMOPHOBIA), "trait/hemophobic"));
        reg(new Def(SLOW_LEARNER, -6, false, key(SLOW_LEARNER), "trait/slowlearner"));
        reg(new Def(DISORGANIZED, -4, false, key(DISORGANIZED), "trait/disorganized"));
        reg(new Def(UNDERWEIGHT, -6, false, key(UNDERWEIGHT), "trait/underweight"));
        reg(new Def(UNLUCKY, -4, false, key(UNLUCKY), "trait/unlucky"));
        reg(new Def(ILLITERATE, -8, false, key(ILLITERATE), "trait/illiterate"));
        reg(new Def(HIGH_THIRST, -6, false, key(HIGH_THIRST), "trait/highthirst"));
        reg(new Def(SUNDAY_DRIVER, -1, false, key(SUNDAY_DRIVER), "trait/sundaydriver"));

        reg(new Def(AXEMAN, 0, true, key(AXEMAN), "trait/axeman"));
        reg(new Def(HANDY, 0, true, key(HANDY), "trait/handy"));
        reg(new Def(THICK_SKINNED, 0, true, key(THICK_SKINNED), "trait/thickskinned"));
        reg(new Def(PATIENT, 0, true, key(PATIENT), "trait/patient"));
        reg(new Def(SHORT_TEMPER, 0, false, key(SHORT_TEMPER), "trait/shorttemper"));
        reg(new Def(MARKSMAN, 0, true, key(MARKSMAN), "trait/target_shooter"));
        reg(new Def(NIGHT_OWL, 0, true, key(NIGHT_OWL), "trait/nightowl"));
        reg(new Def(BLOODY_FURY, 0, true, key(BLOODY_FURY), "trait/bloodfury"));
        reg(new Def(BELOVED, 0, true, key(BELOVED), "trait/beloved"));

        reg(new Def(FEARLESS, 0, true, key(FEARLESS), "trait/adrenalinejunkie"));
        reg(new Def(NUMB, 0, true, key(NUMB), "trait/desensitized"));
    }

    private static String key(String id) {
        return "trait.pz_moodle_immersion." + id;
    }

    private static void reg(Def d) { ALL.put(d.id, d); }

    private static final Map<String, Set<String>> CONFLICTS = new HashMap<>();
    static {
        conflict(BRAVE, COWARDLY);
        conflict(GRACEFUL, CLUMSY);
        conflict(ORGANIZED, DISORGANIZED);
        conflict(KEEN_HEARING, HARD_OF_HEARING);
        conflict(FAST_READER, SLOW_READER);
        conflict(LESS_SLEEP, SLEEPYHEAD);
        conflict(IRON_GUT, WEAK_STOMACH);
        conflict(INCONSPICUOUS, CONSPICUOUS);
        conflict(STRONG, UNDERWEIGHT);
        conflict(LUCKY, UNLUCKY);
        conflict(DEXTEROUS, ALL_THUMBS);
        conflict(SHORT_SIGHTED, EAGLE_EYED);
        conflict(FEARLESS, COWARDLY);
    }

    private static void conflict(String first, String second) {
        CONFLICTS.computeIfAbsent(first, ignored -> new HashSet<>()).add(second);
        CONFLICTS.computeIfAbsent(second, ignored -> new HashSet<>()).add(first);
    }

    /** 里程碑和职业自带特质不进点数商店。 */
    public static boolean selectable(String id) {
        return ALL.containsKey(id) && ALL.get(id).cost() != 0;
    }

    public static boolean occupationExclusive(String id) {
        return ALL.containsKey(id) && ALL.get(id).cost() == 0 && !FEARLESS.equals(id) && !NUMB.equals(id);
    }

    public static Set<String> conflicts(String id) {
        return CONFLICTS.getOrDefault(id, Collections.emptySet());
    }

    public static boolean conflictsWithAny(Set<String> traits, String id) {
        for (String other : conflicts(id)) if (traits.contains(other)) return true;
        return false;
    }

    /**
     * 职业自带的 0 点特质；生存狂拥有所有职业专属特质——
     * 以后每新增一个职业专属特质（cost 0 且非里程碑），生存狂都会自动获得，无需另行维护。
     */
    public static Set<String> occupationTraits(String occupation) {
        Set<String> out = new HashSet<>();
        if (Occupation.PREPPER.equals(occupation)) {
            for (String id : ALL.keySet()) if (occupationExclusive(id)) out.add(id);
            out.add(NUMB);
            return out;
        }
        switch (occupation == null ? "" : occupation) {
            case "firefighter" -> out.add(THICK_SKINNED);
            case "police" -> out.add(MARKSMAN);
            case "carpenter" -> out.add(HANDY);
            case "lumberjack" -> out.add(AXEMAN);
            case "nurse" -> out.add(PATIENT);
            case "security_guard" -> out.add(NIGHT_OWL);
            case "hobo" -> out.add(SHORT_TEMPER);
            case "veteran" -> out.add(NUMB);
            case Occupation.BOXER -> out.add(BLOODY_FURY);
            case Occupation.VILLAGE_HERO -> out.add(BELOVED);
            default -> { }
        }
        return out;
    }

    public static boolean has(MoodleData data, String id) {
        return data.traits.contains(id);
    }

    /** 恐慌累积倍率：麻木=0，无所畏惧=0.2，勇敢=0.3（数值可在管理员界面改 panic_multiplier）。 */
    public static float panicMultiplier(MoodleData data) {
        if (has(data, NUMB)) return 0f;
        float m = 1f;
        if (has(data, FEARLESS)) m *= (float) DefOverrides.mechanic("trait", FEARLESS, "panic_multiplier", 0.2);
        if (has(data, BRAVE)) m *= (float) DefOverrides.mechanic("trait", BRAVE, "panic_multiplier", 0.3);
        if (has(data, COWARDLY)) m *= (float) DefOverrides.mechanic("trait", COWARDLY, "panic_multiplier", 1.5);
        if (has(data, AGORAPHOBIC) && !data.inBuilding)
            m *= (float) DefOverrides.mechanic("trait", AGORAPHOBIC, "panic_multiplier", 1.25);
        if (has(data, CLAUSTROPHOBIC) && data.inBuilding)
            m *= (float) DefOverrides.mechanic("trait", CLAUSTROPHOBIC, "panic_multiplier", 1.25);
        return m;
    }

    /**
     * 特质自带的六项体能加成，会直接叠加到对应数值上。
     * 分量顺序：力量、体格、冲刺、轻巧、灵活、瞄准。
     */
    private static final Map<String, int[]> STAT_BONUS = new LinkedHashMap<>();
    /** 六项分量对应的技能，用于把旧数组和新技能表对上。 */
    private static final Skill[] LEGACY = {
            Skill.STRENGTH, Skill.FITNESS, Skill.SPRINTING, Skill.LIGHTFOOTED, Skill.NIMBLE, Skill.AIMING
    };
    /** 特质在六项体能之外的技能加成（木工、急救、潜行……）。 */
    private static final Map<String, Map<Skill, Integer>> SKILL_BONUS = new LinkedHashMap<>();

    private static void bonus(String id, int strength, int fitness, int sprinting,
                              int lightfooted, int nimble, int aiming) {
        STAT_BONUS.put(id, new int[]{strength, fitness, sprinting, lightfooted, nimble, aiming});
    }

    static {
        bonus(STRONG, 2, 0, 0, 0, 0, 0);            // 强壮
        bonus(ATHLETIC, 1, 2, 1, 0, 0, 0);          // 健壮
        bonus(GRACEFUL, 0, 0, 0, 1, 1, 0);          // 身轻如燕：轻巧、灵活
        bonus(SPEED_DEMON, 0, 0, 1, 0, 0, 0);       // 超速狂魔：冲刺
        bonus(DEXTEROUS, 0, 0, 0, 0, 1, 0);         // 心灵手巧：灵活
        bonus(RESILIENT, 0, 1, 0, 0, 0, 0);         // 坚韧
        bonus(EAGLE_EYED, 0, 0, 0, 0, 0, 1);        // 鹰眼：瞄准
        bonus(MARKSMAN, 0, 0, 0, 0, 0, 1);          // 神枪手：瞄准
        bonus(UNDERWEIGHT, -1, -2, 0, 0, 0, 0);     // 体重过轻
        bonus(PRONE_TO_ILLNESS, 0, -1, 0, 0, 0, 0); // 体弱多病
        bonus(SLOW_HEALER, 0, -1, 0, 0, 0, 0);      // 愈合缓慢
        bonus(ASTHMATIC, 0, -1, -1, 0, 0, 0);       // 哮喘
        bonus(CLUMSY, 0, 0, 0, 0, -1, 0);           // 笨手笨脚：灵活
        bonus(ALL_THUMBS, 0, 0, 0, 0, -1, 0);       // 笨手笨脚（职业专属）：灵活
        bonus(SHORT_SIGHTED, 0, 0, 0, 0, 0, -1);    // 近视：瞄准
        bonus(SUNDAY_DRIVER, 0, 0, -1, 0, 0, 0);    // 慢吞吞：冲刺
        bonus(SLEEPYHEAD, 0, 0, -1, 0, 0, 0);       // 嗜睡
    }

    /** 成对写技能加成：{@code skillBonus(ID, Skill.AXE, 2, Skill.MAINTENANCE, 1)}。 */
    private static void skillBonus(String id, Object... pairs) {
        Map<Skill, Integer> m = new EnumMap<>(Skill.class);
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            m.put((Skill) pairs[i], (Integer) pairs[i + 1]);
        }
        SKILL_BONUS.put(id, Map.copyOf(m));
    }

    static {
        // 体能六项之外的技能加成：让「斧头专精」「巧手」这类特质在技能表里也看得出差别
        skillBonus(AXEMAN, Skill.AXE, 2, Skill.MAINTENANCE, 1);
        skillBonus(HANDY, Skill.CARPENTRY, 1, Skill.MASONRY, 1, Skill.MAINTENANCE, 1);
        skillBonus(MARKSMAN, Skill.AIMING, 1, Skill.RELOADING, 1);
        skillBonus(EAGLE_EYED, Skill.TRACKING, 1);
        skillBonus(OUTDOORSMAN, Skill.FORAGING, 1, Skill.TRACKING, 1, Skill.TRAPPING, 1);
        skillBonus(GRACEFUL, Skill.SNEAKING, 1);
        skillBonus(INCONSPICUOUS, Skill.SNEAKING, 1);
        skillBonus(PATIENT, Skill.FIRST_AID, 1, Skill.MAINTENANCE, 1);
        skillBonus(DEXTEROUS, Skill.MAINTENANCE, 1, Skill.TAILORING, 1);
        skillBonus(RESILIENT, Skill.FIRST_AID, 1);
        skillBonus(LUCKY, Skill.FORAGING, 1);
        skillBonus(NIGHT_OWL, Skill.SNEAKING, 1);
        skillBonus(CLUMSY, Skill.SNEAKING, -1, Skill.COOKING, -1);
        skillBonus(ALL_THUMBS, Skill.MAINTENANCE, -1, Skill.CARPENTRY, -1);
        skillBonus(CONSPICUOUS, Skill.SNEAKING, -1);
        skillBonus(HARD_OF_HEARING, Skill.TRACKING, -1);
        skillBonus(PRONE_TO_ILLNESS, Skill.FIRST_AID, -1);
        skillBonus(SHORT_TEMPER, Skill.MAINTENANCE, -1);
        skillBonus(SUNDAY_DRIVER, Skill.MECHANICS, -1);
        skillBonus(UNLUCKY, Skill.FORAGING, -1);
    }

    /** 返回某一项加成的分量：0=力量 1=体格 2=冲刺 3=轻巧 4=灵活 5=瞄准。 */
    private static int statBonus(Set<String> traits, int index) {
        int sum = 0;
        for (String t : traits) {
            int[] b = STAT_BONUS.get(t);
            if (b != null) sum += b[index];
        }
        return sum;
    }

    public static int strengthBonus(Set<String> traits) { return statBonus(traits, 0); }
    public static int fitnessBonus(Set<String> traits) { return statBonus(traits, 1); }
    public static int sprintingBonus(Set<String> traits) { return statBonus(traits, 2); }
    public static int lightfootedBonus(Set<String> traits) { return statBonus(traits, 3); }
    public static int nimbleBonus(Set<String> traits) { return statBonus(traits, 4); }
    public static int aimingBonus(Set<String> traits) { return statBonus(traits, 5); }
    /** 旧接口：速度加成即冲刺加成。 */
    public static int speedBonus(Set<String> traits) { return sprintingBonus(traits); }

    /**
     * 按技能取特质加成：先查六项体能的旧数组，再叠加技能表里的额外加成。
     * 两项都查得到时会相加（例如神枪手同时吃「瞄准」的体能加成与「换弹」加成）。
     */
    /** 返回特质在六项人物数值上的加成，供管理员编辑界面读取。 */
    public static int[] statBonus(String id) {
        int[] value = STAT_BONUS.get(id);
        return value == null ? new int[6] : value.clone();
    }

    /** 应用管理员对六项人物数值加成的覆盖。 */
    public static void setStatBonus(String id, int[] values) {
        if (values == null) return;
        int[] copy = new int[6];
        System.arraycopy(values, 0, copy, 0, Math.min(6, values.length));
        STAT_BONUS.put(id, copy);
    }

    public static int traitBonus(Set<String> traits, Skill skill) {
        int sum = 0;
        for (int i = 0; i < LEGACY.length; i++) {
            if (LEGACY[i] == skill) {
                sum += statBonus(traits, i);
                break;
            }
        }
        for (String t : traits) {
            Map<Skill, Integer> m = SKILL_BONUS.get(t);
            if (m != null) sum += m.getOrDefault(skill, 0);
        }
        return sum;
    }


    /** 经验倍率：速学者 +30%，慢速学习者 -30%（数值可在管理员界面改 xp_multiplier）。 */
    public static float xpMultiplier(MoodleData data) {
        float m = 1f;
        if (has(data, FAST_READER)) m *= (float) DefOverrides.mechanic("trait", FAST_READER, "xp_multiplier", 1.3);
        if (has(data, SLOW_READER)) m *= (float) DefOverrides.mechanic("trait", SLOW_READER, "xp_multiplier", 0.7);
        if (has(data, SLOW_LEARNER)) m *= (float) DefOverrides.mechanic("trait", SLOW_LEARNER, "xp_multiplier", 0.7);
        if (has(data, ILLITERATE)) m *= (float) DefOverrides.mechanic("trait", ILLITERATE, "xp_multiplier", 0.5);
        return m;
    }

    /** 疲惫累积倍率：嗜睡 +50%，精神抖擞 -30%（数值可在管理员界面改 fatigue_multiplier）。 */
    public static float fatigueMultiplier(MoodleData data) {
        float m = 1f;
        if (has(data, SLEEPYHEAD)) m *= (float) DefOverrides.mechanic("trait", SLEEPYHEAD, "fatigue_multiplier", 1.5);
        if (has(data, LESS_SLEEP)) m *= (float) DefOverrides.mechanic("trait", LESS_SLEEP, "fatigue_multiplier", 0.7);
        return m;
    }

    /** 口渴累积倍率（联动口渴模组）：易渴 +40%。 */
    public static float thirstMultiplier(MoodleData data) {
        return has(data, HIGH_THIRST) ? 1.4f : 1f;
    }

    /** 饥饿累积倍率：食欲旺盛 +40%。 */
    public static float hungerMultiplier(MoodleData data) {
        return has(data, HEARTY_APPETITE) ? 1.4f : 1f;
    }

    /** 受伤/流血相关压力与恐慌倍率。 */
    public static float hemophobiaMultiplier(MoodleData data) {
        return has(data, HEMOPHOBIA) ? 1.35f : 1f;
    }

    private OccupationTrait() {}
}
