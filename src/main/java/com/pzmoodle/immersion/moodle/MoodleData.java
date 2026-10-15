package com.pzmoodle.immersion.moodle;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.neoforged.neoforge.common.util.INBTSerializable;

import java.util.HashSet;
import java.util.Set;

/**
 * 每个玩家的隐藏 Moodle 数值。全部存在服务端数据附件里，
 * 客户端只收到「哪个图标、几级」这类显示信息，看不到具体数字。
 */
public class MoodleData implements INBTSerializable<CompoundTag> {
    // 内部值 0-100（不显示给玩家）
    public float unhappy;
    public float stress;
    public float bored;
    public float panic;
    public float fatigue;
    /** 体力当前值；上限由体格、职业和特质实时计算。 */
    public float stamina = 100f;

    // 计时器（tick）
    public int idleTicks;          // 已连续无动作的 tick
    public int boredAccumTicks;    // 无聊累积计时
    public int wetTicks;           // 湿漉漉剩余 tick
    public int convertTicks;       // 无聊满级后的转化计时
    /** 过天累积的时钟 tick 数（配合「按真实推进量摊平疲劳」的曲线）。 */
    public long dayClockTicks;

    // 饱食度系统（全部按「世界时间」推进，睡觉跳过的时间也计入）
    /** 持续消耗的不足 1 点余量，攒够 1 才真的掉一格饱食度。 */
    public float hungerCarry;
    /** 本轮 0 饱食度已持续的世界时间 tick 数（饱食度恢复即清零）。 */
    public long starveTicks;
    /** 当前饥饿惩罚深度：0=正常，0.5=减半，1=完全失去，2/3=按周继续加深。 */
    public float starvePenalty;
    /** 三周后缓慢掉血的累积余量（攒够 0.5 心才真正扣）。 */
    public float starveHpPending;
    /** 本模组自己的「饿死收尾一击」标记：放行原版饿死伤害的取消逻辑。 */
    public transient boolean starveKill;

    // ————— 服务端移动探测（每 tick 刷新，不落盘）—————
    /** 上一 tick 的玩家坐标；NaN 表示还没有参照点。 */
    public transient double probeX = Double.NaN;
    public transient double probeZ = Double.NaN;
    /** 上一 tick 的水平位移（格/tick），传送/击退等异常值已被过滤。 */
    public transient float movePerTick;
    /** 「刚移动过」缓存剩余 tick，供低频结算判断。 */
    public int moveCacheTicks;

    // 统计
    public long zombieKills;
    /** 这条命存活的游戏天数：死亡重生后清零。 */
    public long daysAlive;
    /** 距离上次抽烟/喝酒经过的游戏日数，用于戒断规则。 */
    public int daysSinceSmoke;
    public int daysSinceDrink;
    public long lastDayTime = -1;  // 上次结算时的世界时间，用来判断「过了一天」

    // 职业与特质
    public String occupation = "";
    public int traitPoints;
    public boolean occupationChosen;
    /** 管理员通过 /生存狂、/拳击手 等命令解锁的隐藏职业 id 集合。 */
    public final Set<String> unlockedHidden = new HashSet<>();
    /** 浴血奋战剩余 tick：受到伤害后 +50% 攻击力的持续时间。 */
    public int furyTicks;
    public final Set<String> traits = new HashSet<>();
    /** 是否处在室内，用于幽闭/开阔恐惧等特质的简化判定。 */
    public boolean inBuilding;

    // 缓存的显示等级（服务端算好后同步给客户端）
    public final int[] displayLevels = new int[Moodle.ALL.length];
    /** 上一次发给客户端的等级，用于只在变化时同步。 */
    public final int[] syncedLevels = new int[Moodle.ALL.length];
    /** 冲刺 / 潜行的训练计时（不落盘，重进世界从零开始）。 */
    public final int[] trainTicks = new int[2];

    public void clamp() {
        unhappy = clamp01(unhappy);
        stress = clamp01(stress);
        bored = clamp01(bored);
        panic = clamp01(panic);
        fatigue = Math.max(0, Math.min(fatigue, 120f)); // 疲惫可到 120（对应累了 4 级）
        stamina = Math.max(0f, Math.min(stamina, 200f));
    }

    public void addStamina(float value, float cap) {
        stamina = Math.max(0f, Math.min(cap, stamina + value));
    }

    private static float clamp01(float v) {
        return v < 0 ? 0 : Math.min(v, 100f);
    }

    public void addUnhappy(float v) { unhappy = clamp01(unhappy + v); }

    /** 正向压力只有在不开心积满后才允许进入；负向压力始终可以正常下降。 */
    public void addStress(float v) {
        if (v > 0f && unhappy < com.pzmoodle.immersion.config.CommonConfig.STRESS_REQUIRE_UNHAPPY_FULL.get()) return;
        stress = clamp01(stress + v);
    }

    // ————— 减压缓释池 —————
    // 吃东西/喝酒这类「一次性大幅减压」如果直接扣满，状态值会一格跳 25% 左右，很突兀。
    // 现在改成先进池子，再由 MoodleTicker 按秒平滑地放进 unhappy/stress，循序渐进。
    public float unhappyRelief;
    public float stressRelief;

    /** 把一次性的不开心减免放进缓释池（正值=要减掉多少）。 */
    public void queueUnhappyRelief(float amount) {
        if (amount > 0f) unhappyRelief = Math.min(unhappyRelief + amount, 200f);
    }

    /** 把一次性的压力减免放进缓释池（正值=要减掉多少）。 */
    public void queueStressRelief(float amount) {
        if (amount > 0f) stressRelief = Math.min(stressRelief + amount, 200f);
    }

    /** 每秒排空剩余缓释量约 15%，返回本 tick 实际生效的量。 */
    private float drainPool(float pool, int delta) {
        if (pool <= 0f) return 0f;
        float step = Math.min(pool, pool * Math.min(1f, delta * 0.0075f));
        if (step < 0.01f) step = Math.min(pool, 0.01f);
        return step;
    }

    /** 每个结算 tick 排一次缓释池。返回是否有生效。 */
    public boolean tickRelief(int delta) {
        boolean any = false;
        if (unhappyRelief > 0f) {
            float step = drainPool(unhappyRelief, delta);
            unhappyRelief -= step;
            addUnhappy(-step);
            any = true;
        }
        if (stressRelief > 0f) {
            float step = drainPool(stressRelief, delta);
            stressRelief -= step;
            addStress(-step);
            any = true;
        }
        return any;
    }

    public void addBored(float v) { bored = clamp01(bored + v); }
    public void addPanic(float v) { panic = clamp01(panic + v); }
    public void addFatigue(float v) { fatigue = Math.max(0, Math.min(fatigue + v, 120f)); }

    @Override
    public CompoundTag serializeNBT(net.minecraft.core.HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putFloat("unhappy", unhappy);
        tag.putFloat("stress", stress);
        tag.putFloat("bored", bored);
        tag.putFloat("panic", panic);
        tag.putFloat("fatigue", fatigue);
        tag.putFloat("stamina", stamina);
        tag.putFloat("unhappyRelief", unhappyRelief);
        tag.putFloat("stressRelief", stressRelief);
        tag.putInt("idleTicks", idleTicks);
        tag.putInt("boredAccumTicks", boredAccumTicks);
        tag.putInt("wetTicks", wetTicks);
        tag.putInt("convertTicks", convertTicks);
        tag.putLong("zombieKills", zombieKills);
        tag.putLong("daysAlive", daysAlive);
        tag.putInt("daysSinceSmoke", daysSinceSmoke);
        tag.putInt("daysSinceDrink", daysSinceDrink);
        tag.putLong("lastDayTime", lastDayTime);
        tag.putFloat("hungerCarry", hungerCarry);
        tag.putLong("starveTicks", starveTicks);
        tag.putFloat("starvePenalty", starvePenalty);
        tag.putFloat("starveHpPending", starveHpPending);
        tag.putString("occupation", occupation);
        tag.putInt("traitPoints", traitPoints);
        tag.putBoolean("occupationChosen", occupationChosen);
        ListTag hidden = new ListTag();
        for (String id : unlockedHidden) hidden.add(StringTag.valueOf(id));
        tag.put("unlockedHidden", hidden);
        tag.putInt("furyTicks", furyTicks);
        tag.putBoolean("inBuilding", inBuilding);
        ListTag list = new ListTag();
        for (String t : traits) list.add(StringTag.valueOf(t));
        tag.put("traits", list);
        return tag;
    }

    @Override
    public void deserializeNBT(net.minecraft.core.HolderLookup.Provider registries, CompoundTag tag) {
        unhappy = tag.getFloat("unhappy");
        stress = tag.getFloat("stress");
        bored = tag.getFloat("bored");
        panic = tag.getFloat("panic");
        fatigue = tag.getFloat("fatigue");
        stamina = tag.contains("stamina") ? tag.getFloat("stamina") : 100f;
        // 缓释池是新增字段，旧存档没有时按 0 处理
        unhappyRelief = tag.getFloat("unhappyRelief");
        stressRelief = tag.getFloat("stressRelief");
        idleTicks = tag.getInt("idleTicks");
        boredAccumTicks = tag.getInt("boredAccumTicks");
        wetTicks = tag.getInt("wetTicks");
        convertTicks = tag.getInt("convertTicks");
        zombieKills = tag.getLong("zombieKills");
        daysAlive = tag.getLong("daysAlive");
        daysSinceSmoke = tag.getInt("daysSinceSmoke");
        daysSinceDrink = tag.getInt("daysSinceDrink");
        lastDayTime = tag.getLong("lastDayTime");
        // 饱食度新系统的字段：旧存档没有时按「无惩罚」处理
        hungerCarry = tag.getFloat("hungerCarry");
        starveTicks = tag.getLong("starveTicks");
        starvePenalty = tag.getFloat("starvePenalty");
        starveHpPending = tag.getFloat("starveHpPending");
        occupation = tag.getString("occupation");
        traitPoints = tag.getInt("traitPoints");
        occupationChosen = tag.getBoolean("occupationChosen");
        unlockedHidden.clear();
        if (tag.contains("unlockedHidden", Tag.TAG_LIST)) {
            ListTag hidden = tag.getList("unlockedHidden", Tag.TAG_STRING);
            for (int i = 0; i < hidden.size(); i++) unlockedHidden.add(hidden.getString(i));
        }
        // 旧存档兼容：单独的生存狂布尔字段迁移进集合
        if (tag.getBoolean("prepperUnlocked")) unlockedHidden.add(com.pzmoodle.immersion.trait.Occupation.PREPPER);
        furyTicks = tag.getInt("furyTicks");
        inBuilding = tag.getBoolean("inBuilding");
        traits.clear();
        if (tag.contains("traits", Tag.TAG_LIST)) {
            ListTag list = tag.getList("traits", Tag.TAG_STRING);
            for (int i = 0; i < list.size(); i++) traits.add(list.getString(i));
        }
    }
}
