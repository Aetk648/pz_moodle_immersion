package com.pzmoodle.immersion.moodle;

import net.minecraft.server.level.ServerPlayer;

/**
 * 服务端「玩家到底有没有在移动」的探测。
 * <p>
 * 1.21.1 里玩家坐标由客户端移动包直接写入服务端，服务端并不用这套坐标去算物理，
 * 所以 {@code getDeltaMovement()} 在正常走路/奔跑时几乎恒为 0。
 * 以前拿它判断「在奔跑」，结果是奔跑被当成站桩：体力不仅不扣，
 * 反而按「原地休息」一路补满（体力永远 100 的直接原因）。
 * 这里改用每 tick 的真实坐标差来判定移动量。
 */
public final class MovementProbe {

    /** 视为「在移动」的最小水平位移（格/tick）。潜行约 0.065、走步约 0.216、奔跑约 0.28。 */
    public static final float MOVE_EPS = 0.01f;
    /** 单 tick 位移超过这个值按传送/击退处理，不计入移动（带增益的奔跑也远低于它）。 */
    private static final double TP_GUARD = 1.0;

    /** 「刚移动过」缓存长度（tick）：要盖住最大结算间隔（tickInterval 上限 100），
     *  否则低频结算会漏看短暂移动，把走过路的玩家当成一直静止。 */
    private static final int CACHE_TICKS = 100;

    /** 每 tick 调用一次（StaminaSystem.tick 是最早的每 tick 入口），刷新探测结果。 */
    public static void tick(ServerPlayer player, MoodleData data) {
        double x = player.getX();
        double z = player.getZ();
        float dist = 0f;
        if (!Double.isNaN(data.probeX)) {
            double dx = x - data.probeX;
            double dz = z - data.probeZ;
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d <= TP_GUARD) dist = (float) d;
        }
        data.probeX = x;
        data.probeZ = z;
        data.movePerTick = dist;
        if (dist > MOVE_EPS) data.moveCacheTicks = CACHE_TICKS;
        else if (data.moveCacheTicks > 0) data.moveCacheTicks--;
    }

    /** 本 tick 实际在移动。 */
    public static boolean moving(MoodleData data) {
        return data.movePerTick > MOVE_EPS;
    }

    /** 近几 tick 内移动过（供低频结算使用，结算间隔内可能错过单 tick 的位移）。 */
    public static boolean movedRecently(MoodleData data) {
        return data.moveCacheTicks > 0;
    }

    private MovementProbe() {}
}
