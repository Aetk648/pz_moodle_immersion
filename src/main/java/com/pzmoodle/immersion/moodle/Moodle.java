package com.pzmoodle.immersion.moodle;

import com.pzmoodle.immersion.PZMoodleImmersion;
import com.pzmoodle.immersion.config.ClientConfig;
import net.minecraft.resources.ResourceLocation;

/**
 * 所有 Moodle 状态。声明顺序即 HUD 优先级（从左到右）：
 * 流血 > 口渴 > 饥饿 > 恐慌 > 无聊 > 湿漉漉 > 疼痛 > 不开心 > 压力 > 累了，
 * 后面追加的是吃饱与联动状态（醉酒/低温/高温）。
 * 贴图一律使用《僵尸毁灭工程》B41 原版 Moodle 图。
 */
public enum Moodle {
    BLEEDING("status_bleeding", 4),
    THIRST("status_thirst", 4),
    HUNGER("status_hunger", 4),
    FULL("status_full", 1),
    PANIC("mood_panicked", 4),
    BORED("mood_bored", 4),
    WET("status_wet", 1),
    PAIN("mood_pained", 4),
    UNHAPPY("mood_sad", 4),
    STRESS("mood_stressed", 4),
    TIRED("mood_exhausted", 4),
    DRUNK("mood_drunk", 4),
    COLD("status_temperature_low", 4),
    HOT("status_temperature_hot", 4),
    STAMINA("stamina_endurance", 4),
    HEAVY_LOAD("status_heavy_load", 4);

    public static final Moodle[] ALL = values();

    public final String id;
    public final int maxLevel;
    public final ResourceLocation icon;

    Moodle(String texture, int maxLevel) {
        this.id = name().toLowerCase();
        this.maxLevel = maxLevel;
        this.icon = PZMoodleImmersion.id("textures/gui/moodle/" + texture + ".png");
    }

    public ResourceLocation iconFor(ClientConfig.IconSet set) {
        if (set == ClientConfig.IconSet.B41) {
            return PZMoodleImmersion.id("textures/gui/moodle/b41/" + b41Name() + ".png");
        }
        return icon;
    }

    /** 当前图标文件的像素尺寸，GuiGraphics 采样时不能把 32 像素图当成 48 像素图。 */
    public int textureSize(ClientConfig.IconSet set) {
        if (set == ClientConfig.IconSet.B41) return 32;
        return switch (this) {
            case FULL, STAMINA, HEAVY_LOAD -> 32;
            default -> 48;
        };
    }

    private String b41Name() {
        return switch (this) {
            case PANIC -> "mood_panicked";
            case BORED -> "mood_bored";
            case HUNGER -> "status_hunger";
            case THIRST -> "status_thirst";
            case BLEEDING -> "status_bleeding";
            case PAIN -> "mood_pained";
            case STRESS -> "mood_stressed";
            case TIRED -> "mood_exhausted";
            case UNHAPPY -> "mood_sad";
            case WET -> "status_wet";
            case COLD -> "status_temperature_low";
            case HOT -> "status_temperature_hot";
            case DRUNK -> "mood_drunk";
            case FULL -> "status_full";
            case STAMINA -> "moodle_icon_endurance";
            case HEAVY_LOAD -> "moodle_icon_heavyload";
        };
    }

    public static int index(Moodle m) {
        return m.ordinal();
    }
}
