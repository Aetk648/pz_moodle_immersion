package com.pzmoodle.immersion.moodle;

/**
 * 负重（Heavy Load）按背包槽位计算，全程渐进、没有台阶：
 * 力量 0 级从 8 格开始受影响，5 级 18 格，10 级装满 36 格也不受影响。
 * 起负之后每多放 1 格物品，移速就多降一点，背包装满（36 格）时达到最高负重，
 * 中间按线性插值，不会出现「突然一下就负重」的跳变。
 */
public final class Encumbrance {
    /** 背包主栏位总数（物品栏装满 36 格 = 最高负重）。 */
    public static final int INVENTORY_SLOTS = 36;

    public record State(int occupied, int start, int fullSlots, float ratio) {
        public boolean active() { return occupied > start; }
        public boolean isFullyLoaded() { return occupied >= fullSlots; }
    }

    public static State state(int occupied, int strength) {
        int level = Math.max(0, Math.min(10, strength));
        int start = Math.round(8.0f + level * 2.0f); // 0级8，5级18，10级28（高等级另按不受影响处理）
        int full = INVENTORY_SLOTS;                  // 背包装满 36 格达到最高负重
        if (level >= 10) return new State(occupied, 36, Integer.MAX_VALUE, 0f);
        float ratio = full <= start ? 0f : Math.max(0f, Math.min(1f, (occupied - start) / (float) (full - start)));
        return new State(occupied, start, full, ratio);
    }

    /** 完全负重时移动速度降到走步速度（0.25 倍冲刺速度），中间线性减速。 */
    public static double speedMultiplier(State state) {
        return 1.0 - state.ratio * 0.75;
    }

    /** 负重状态图标等级，0 表示不显示。 */
    public static int iconLevel(State state) {
        if (!state.active()) return 0;
        if (state.ratio >= 0.9f) return 4;
        if (state.ratio >= 0.6f) return 3;
        if (state.ratio >= 0.3f) return 2;
        return 1;
    }

    private Encumbrance() {}
}
