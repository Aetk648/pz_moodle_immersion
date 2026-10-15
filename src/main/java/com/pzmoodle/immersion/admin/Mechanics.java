package com.pzmoodle.immersion.admin;

import com.pzmoodle.immersion.config.CommonConfig;
import com.pzmoodle.immersion.moodle.Encumbrance;
import com.pzmoodle.immersion.moodle.Skill;
import com.pzmoodle.immersion.moodle.Stamina;
import com.pzmoodle.immersion.trait.Occupation;
import com.pzmoodle.immersion.trait.OccupationTrait;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 管理员界面的「详细隐藏数值」文本。
 * 两条原则：
 * 1. 所有数字都来自真实游戏逻辑（对应实现处注释里标了文件），不写假数值；
 * 2. 带参数的特质，说明文字直接反映管理员「修改机制数值」框里的当前值，改了立刻生效。
 */
public final class Mechanics {
    /** 每个带参数特质/职业的默认参数模板；游戏逻辑的 fallback 与此处保持一致。 */
    public static String defaultParameters(String kind, String id) {
        if ("occupation".equals(kind)) {
            return switch (id) {
                case Occupation.BOXER -> "unarmed_damage_bonus=4;unarmed_attack_speed=0.5";
                default -> "";
            };
        }
        return switch (id) {
            case OccupationTrait.THICK_SKINNED -> "damage_reduction=0.25";
            case OccupationTrait.AXEMAN -> "tree_speed_bonus=0.25;axe_attack_speed=0.25";
            case OccupationTrait.BLOODY_FURY -> "attack_bonus=0.50;duration_seconds=5";
            case OccupationTrait.IRON_GUT -> "poison_immune_chance=0.8";
            case OccupationTrait.NIGHT_OWL -> "night_move_bonus=0.10";
            case OccupationTrait.LESS_SLEEP -> "fatigue_multiplier=0.7";
            case OccupationTrait.SLEEPYHEAD -> "fatigue_multiplier=1.5";
            case OccupationTrait.BRAVE -> "panic_multiplier=0.3";
            case OccupationTrait.COWARDLY -> "panic_multiplier=1.5";
            case OccupationTrait.FEARLESS -> "panic_multiplier=0.2";
            case OccupationTrait.AGORAPHOBIC -> "panic_multiplier=1.25";
            case OccupationTrait.CLAUSTROPHOBIC -> "panic_multiplier=1.25";
            case OccupationTrait.FAST_READER -> "xp_multiplier=1.3";
            case OccupationTrait.SLOW_READER -> "xp_multiplier=0.7";
            case OccupationTrait.SLOW_LEARNER -> "xp_multiplier=0.7";
            case OccupationTrait.ILLITERATE -> "xp_multiplier=0.5";
            case OccupationTrait.SMOKER -> "stress_per_day=10";
            case OccupationTrait.PACIFIST -> "damage_multiplier=0.7";
            default -> "";
        };
    }

    /** 兼容旧调用点；管理员正文使用 detailText。 */
    public static String defaultText(String kind, String id) {
        return detailText(kind, id, null, null);
    }

    /** 「机制数值大全」面板用：按默认参数生成一条具体效果说明。 */
    public static String effectSummary(String kind, String id) {
        Map<String, Double> params = parseParams(defaultParameters(kind, id));
        if ("occupation".equals(kind)) {
            String exclusive = exclusiveTraitText(id, params);
            return exclusive.isBlank() ? "该职业没有额外机制参数，效果全部来自人物数值。" : exclusive;
        }
        return traitEffectText(id, params);
    }

    /** 解析「参数名=数值;参数名=数值」文本，供说明文字实时反映编辑值。 */
    private static Map<String, Double> parseParams(String text) {
        Map<String, Double> out = new HashMap<>();
        if (text == null || text.isBlank()) return out;
        for (String part : text.split(";")) {
            String[] pair = part.trim().split("=", 2);
            if (pair.length == 2) {
                try { out.put(pair[0].trim(), Double.parseDouble(pair[1].trim())); } catch (NumberFormatException ignored) { }
            }
        }
        return out;
    }

    private static double p(Map<String, Double> params, String key, double fallback) {
        Double v = params.get(key);
        return v == null ? fallback : v;
    }

    /** 根据当前编辑框里的数值，生成具体效果说明。mechanicsText 传管理员正在编辑的机制参数。 */
    public static String detailText(String kind, String id, int[] edited, String mechanicsText) {
        if ("occupation".equals(kind)) {
            Occupation.Def def = Occupation.ALL.get(id);
            if (def == null) return "";
            int[] v = edited != null && edited.length >= 6 ? edited : new int[]{def.stats().strength(), def.stats().fitness(),
                    def.stats().sprinting(), def.stats().lightfooted(), def.stats().nimble(), def.stats().aiming()};
            int strength = clamp(v[0]), fitness = clamp(v[1]), sprint = clamp(v[2]);
            int light = clamp(v[3]), nimble = clamp(v[4]), aiming = clamp(v[5]);
            double attack = Occupation.lowToHighMultiplier(strength) * 100.0;
            double health = Occupation.lowToHighMultiplier(fitness) * 100.0;
            double move = sprint * 5.0;
            double sneak = Math.min(70.0, light * 7.0);
            double jump = (Math.sqrt(1.0 + light * 0.05) - 1.0) * 100.0;
            double attackSpeed = nimble * 5.0;
            double ranged = aiming * CommonConfig.AIMING_PROJECTILE_PER_POINT.get() * 100.0;
            Map<String, Double> params = parseParams(mechanicsText == null
                    ? DefOverrides.mechanicsText(kind, id) : mechanicsText);
            StringBuilder sb = new StringBuilder();
            sb.append(String.format(Locale.ROOT,
                    "人物数值（力量/体格/冲刺/轻巧/灵活/瞄准 = %d/%d/%d/%d/%d/%d）：\n攻击力 %.0f%%；最大生命 %.0f%%；挖掘每点 +4%%。\n移动速度 +%.0f%%；潜行速度 +%.0f%%（10 级≈走路）；跳跃高度 +%.0f%%。\n攻击速度 +%.0f%%；弓弩/TaCZ 伤害 +%.0f%%。\n体力上限：体格 %d 级 = %.0f 点。\n负重：力量 %d 级从 %d 格开始负重，之后每多放 1 格物品移速再降一点，36 格装满时最高（-75%% 移速）；力量 10 级完全免负重。\n",
                    strength, fitness, sprint, light, nimble, aiming,
                    attack, health, move, sneak, jump, attackSpeed, ranged,
                    fitness, Stamina.cap(fitness), strength,
                    strength >= 10 ? 36 : Encumbrance.state(0, strength).start()));
            if (Occupation.VILLAGE_HERO.equals(id)) {
                sb.append("常驻「村庄英雄」效果：村民交易打折、铁傀儡不会攻击你，僵尸更容易盯上你。\n");
            }
            String exclusive = exclusiveTraitText(id, params);
            if (!exclusive.isEmpty()) sb.append("专属机制：").append(exclusive);
            return sb.toString();
        }

        int[] stats = edited != null && edited.length >= 6 ? edited : OccupationTrait.statBonus(id);
        String stat = String.format(Locale.ROOT, "人物数值变化：力量 %+d、体格 %+d、冲刺 %+d、轻巧 %+d、灵活 %+d、瞄准 %+d。\n",
                stats[0], stats[1], stats[2], stats[3], stats[4], stats[5]);
        Map<String, Double> params = parseParams(mechanicsText == null
                ? DefOverrides.mechanicsText(kind, id) : mechanicsText);
        String effect = traitEffectText(id, params);
        return stat + effect + String.format(Locale.ROOT,
                "\n通用规则：疼痛每级攻击 -10%%、失手率 +10%%；恐慌每级攻击 -15%%、失手率 +15%%；\n奔跑体力低于 50%% 开始降速，低于 20%% 只能走步。\n负重：超过起负格数后每多 1 格物品移速多降一点，装满 36 格达到最高负重；力量 10 级免负重。\n饱食度：按游戏时间持续下降（满管约耗一天，睡觉跳过的时间也计入），奔跑/跳跃不再额外消耗；低于 30%% 起速度/跳跃/攻击力/攻速渐进下降，0 时减半，之后每满一周（游戏 7 天）继续加深，满三周开始缓慢掉生命；进食后按体格比例渐进恢复。", 0);
    }

    /** 职业的专属机制一句话（取该职业赠送的特质说明）。 */
    private static String exclusiveTraitText(String occupation, Map<String, Double> params) {
        StringBuilder sb = new StringBuilder();
        for (String traitId : OccupationTrait.occupationTraits(occupation)) {
            String text = traitEffectText(traitId, params);
            if (text.isBlank()) continue;
            sb.append('·').append(traitName(traitId)).append("：").append(text.replace("\n", ""));
        }
        return sb.toString();
    }

    /** 特质显示名（管理员界面在客户端使用，直接取语言文件）。 */
    private static String traitName(String id) {
        OccupationTrait.Def def = OccupationTrait.ALL.get(id);
        return def == null ? id
                : net.minecraft.network.chat.Component.translatable(def.nameKey()).getString();
    }

    /** 单个特质的真实效果说明；数值优先取管理员编辑的参数。 */
    private static String traitEffectText(String id, Map<String, Double> params) {
        return switch (id) {
            // ---- 正面特质 ----
            case OccupationTrait.SPEED_DEMON -> "冲刺 +1：移动速度约 +5%。";
            case OccupationTrait.OUTDOORSMAN -> "觅食 +1（树叶掉苹果概率 +1%/级、浆果掉落 +20%/级）、追踪 +1、陷阱 +1。";
            case OccupationTrait.DEXTEROUS -> "灵活 +1（攻击速度 +5%、对怪物闪避 +5%）；保养 +1（耐久消耗 -5%/级）；裁缝 +1。";
            case OccupationTrait.LESS_SLEEP -> String.format(Locale.ROOT, "疲惫累积 ×%.2f（每天 20→%.0f 点）；疲惫每级移速与挖掘 -10%%。",
                    p(params, "fatigue_multiplier", 0.7), 20.0 * p(params, "fatigue_multiplier", 0.7));
            case OccupationTrait.CATS_EYE -> "光照 ≤3 的暗处自动获得隐藏夜视（60 秒持续刷新）。";
            case OccupationTrait.BRAVE -> String.format(Locale.ROOT, "恐慌累积 ×%.2f（-%.0f%%）。",
                    p(params, "panic_multiplier", 0.3), (1.0 - p(params, "panic_multiplier", 0.3)) * 100.0);
            case OccupationTrait.GRACEFUL -> "轻巧 +1（潜行速度 +7%/级）、灵活 +1（攻速 +5%）、潜行 +1（被发现距离 -5%）；奔跑与近战体力消耗 -15%。";
            case OccupationTrait.ORGANIZED -> "拾取范围 +30%，1.0~1.3 格内的掉落物被吸向玩家（每 tick 0.03）。";
            case OccupationTrait.KEEN_HEARING -> "当前版本没有额外机制，只消耗点数（后续版本再补听觉范围效果）。";
            case OccupationTrait.ATHLETIC -> "力量 +1（攻击 +10%）、体格 +2（生命 +40%）、冲刺 +1（移速 +5%）；体力上限与恢复速度随体格提高。";
            case OccupationTrait.STRONG -> "力量 +2（攻击 +20%、挖掘 +4%/点）；起负格数 = 8 + 力量×2，力量 10 级完全免负重。";
            case OccupationTrait.LUCKY -> "觅食 +1（树叶掉苹果概率 +1%/级、浆果掉落 +20%/级）。";
            case OccupationTrait.INCONSPICUOUS -> "潜行 +1：怪物发现距离 -5%/级（潜行 10 级最多 -50%）。";
            case OccupationTrait.EAGLE_EYED -> "瞄准 +1（弓弩/TaCZ 伤害 +5%）；追踪 +1。";
            case OccupationTrait.IRON_GUT -> String.format(Locale.ROOT, "中毒免疫概率 %.0f%%（poison_immune_chance）。",
                    p(params, "poison_immune_chance", 0.8) * 100.0);
            case OccupationTrait.FAST_READER -> String.format(Locale.ROOT, "经验获取 ×%.1f（xp_multiplier）。", p(params, "xp_multiplier", 1.3));
            case OccupationTrait.RESILIENT -> "体格 +1（生命 +20%）；急救 +1（治疗量 +5%/级、使用时间 -5%/级、冷却 -5%/级）。";
            // ---- 负面特质 ----
            case OccupationTrait.SMOKER -> String.format(Locale.ROOT, "每天不抽烟压力 +%.0f（stress_per_day）；连续 30 个游戏日不抽自动戒烟。",
                    p(params, "stress_per_day", 10.0));
            case OccupationTrait.DRUNKARD -> "每天不喝酒压力 +15（可用 config 的 drunkardStressPerDay 调整）；连续 60 个游戏日不喝自动戒酒。";
            case OccupationTrait.CLUMSY -> "灵活 -1（攻速 -5%）；潜行 -1、烹饪 -1；奔跑与近战体力消耗 +15%。";
            case OccupationTrait.COWARDLY -> String.format(Locale.ROOT, "恐慌累积 ×%.2f（+%.0f%%）。",
                    p(params, "panic_multiplier", 1.5), (p(params, "panic_multiplier", 1.5) - 1.0) * 100.0);
            case OccupationTrait.ALL_THUMBS -> "灵活 -1（攻速 -5%）；保养 -1（耐久消耗变多）、木工 -1。";
            case OccupationTrait.SHORT_SIGHTED -> "瞄准 -1（弓弩/TaCZ 伤害 -5%）。";
            case OccupationTrait.SLOW_READER -> String.format(Locale.ROOT, "经验获取 ×%.1f（xp_multiplier）。", p(params, "xp_multiplier", 0.7));
            case OccupationTrait.SLOW_HEALER -> "体格 -1（生命 -20%）；当前版本没有额外的愈合减速机制。";
            case OccupationTrait.WEAK_STOMACH -> "当前版本没有额外机制，只返还点数（铁胃的毒免疫不受它影响）。";
            case OccupationTrait.CONSPICUOUS -> "潜行 -1：更容易被怪物发现。";
            case OccupationTrait.HARD_OF_HEARING -> "追踪 -1。";
            case OccupationTrait.CLAUSTROPHOBIC -> String.format(Locale.ROOT, "在室内时恐慌累积 ×%.2f（panic_multiplier）。", p(params, "panic_multiplier", 1.25));
            case OccupationTrait.AGORAPHOBIC -> String.format(Locale.ROOT, "在室外时恐慌累积 ×%.2f（panic_multiplier）。", p(params, "panic_multiplier", 1.25));
            case OccupationTrait.SLEEPYHEAD -> String.format(Locale.ROOT, "疲惫累积 ×%.2f（每天 20→%.0f 点）；冲刺 -1（移速 -5%%）。",
                    p(params, "fatigue_multiplier", 1.5), 20.0 * p(params, "fatigue_multiplier", 1.5));
            case OccupationTrait.PRONE_TO_ILLNESS -> "体格 -1（生命 -20%）；急救 -1。";
            case OccupationTrait.PACIFIST -> String.format(Locale.ROOT, "造成的攻击伤害 ×%.2f（-%.0f%%，damage_multiplier）。",
                    p(params, "damage_multiplier", 0.7), (1.0 - p(params, "damage_multiplier", 0.7)) * 100.0);
            case OccupationTrait.HEARTY_APPETITE -> "当前版本没有额外机制，只返还点数。";
            case OccupationTrait.ASTHMATIC -> "体格 -1（生命 -20%）、冲刺 -1（移速 -5%）；体力上限更低，更容易进入低体力减速区间。";
            case OccupationTrait.HEMOPHOBIA -> "当前版本没有额外机制，只返还点数。";
            case OccupationTrait.SLOW_LEARNER -> String.format(Locale.ROOT, "经验获取 ×%.1f（xp_multiplier）。", p(params, "xp_multiplier", 0.7));
            case OccupationTrait.DISORGANIZED -> "没有额外机制（与井井有条互斥，只是不享受它的拾取加成）。";
            case OccupationTrait.UNDERWEIGHT -> "力量 -1（攻击 -10%）、体格 -2（生命 -40%）；最大生命额外 -20%。";
            case OccupationTrait.UNLUCKY -> "觅食 -1（树叶/浆果掉落减少）。";
            case OccupationTrait.ILLITERATE -> String.format(Locale.ROOT, "经验获取 ×%.1f（xp_multiplier）。", p(params, "xp_multiplier", 0.5));
            case OccupationTrait.HIGH_THIRST -> "当前版本没有额外机制，只返还点数。";
            case OccupationTrait.SUNDAY_DRIVER -> "冲刺 -1（移速 -5%）；机械 -1。";
            // ---- 职业专属 / 里程碑 ----
            case OccupationTrait.AXEMAN -> String.format(Locale.ROOT,
                    "所有斧头砍树按金斧速度再 ×%.2f（tree_speed_bonus）；斧头攻击冷却 -%.0f%%（axe_attack_speed，等效攻速提升）；斧 +2、保养 +1。",
                    1.0 + p(params, "tree_speed_bonus", 0.25), p(params, "axe_attack_speed", 0.25) * 100.0);
            case OccupationTrait.HANDY -> "木工 +1、石工 +1、保养 +1（保养每级耐久消耗 -5%，10 级减半）。";
            case OccupationTrait.THICK_SKINNED -> String.format(Locale.ROOT, "受到的所有伤害 ×%.2f（-%.0f%%，damage_reduction）。",
                    1.0 - p(params, "damage_reduction", 0.25), p(params, "damage_reduction", 0.25) * 100.0);
            case OccupationTrait.PATIENT -> "急救 +1（治疗量 +5%/级、使用时间 -5%/级、冷却 -5%/级）；保养 +1。";
            case OccupationTrait.SHORT_TEMPER -> "保养 -1；当前版本没有其他机制。";
            case OccupationTrait.MARKSMAN -> "瞄准 +1（远程伤害 +5%）、瞄准技能 +1、换弹 +1（换弹速度每级 +5%，最多翻倍）。";
            case OccupationTrait.NIGHT_OWL -> String.format(Locale.ROOT, "夜晚移动速度 +%.0f%%（night_move_bonus）；夜晚自动获得隐藏夜视；潜行 +1。",
                    p(params, "night_move_bonus", 0.10) * 100.0);
            case OccupationTrait.BLOODY_FURY -> String.format(Locale.ROOT,
                    "受伤后 %.0f 秒内攻击力 ×%.2f（duration_seconds、attack_bonus）；连续受伤只延长时间，不叠加倍率。",
                    p(params, "duration_seconds", 5.0), 1.0 + p(params, "attack_bonus", 0.50));
            case OccupationTrait.FEARLESS -> String.format(Locale.ROOT, "恐慌累积 ×%.2f（击杀 10000 只僵尸自动获得）。", p(params, "panic_multiplier", 0.2));
            case OccupationTrait.NUMB -> "压力与恐慌持续清零，完全不产生这两种状态（击杀 100000 只僵尸自动获得）。";
            case OccupationTrait.BELOVED -> "完全不受来自其他玩家的伤害（玩家本人、箭矢、药水、枪械都算）；对怪物、环境伤害与自己造成的伤害无效判定不变。";
            default -> "该条目没有隐藏机制，效果全部来自上面的人物数值变化；上方数字为当前编辑值。";
        };
    }

    private static int clamp(int value) { return Math.max(0, Math.min(10, value)); }
    private Mechanics() {}
}