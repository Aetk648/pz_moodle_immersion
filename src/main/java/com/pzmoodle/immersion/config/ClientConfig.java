package com.pzmoodle.immersion.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 客户端配置：HUD 外观与显示开关。
 */
public final class ClientConfig {
    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.BooleanValue HUD_ENABLED;
    public static final ModConfigSpec.DoubleValue ICON_SCALE;
    public static final ModConfigSpec.IntValue OFFSET_X;
    public static final ModConfigSpec.IntValue OFFSET_Y;
    public static final ModConfigSpec.BooleanValue SHOW_LEVEL_NUMBERS;
    public static final ModConfigSpec.BooleanValue VERTICAL_LAYOUT;
    public static final ModConfigSpec.BooleanValue INVENTORY_ONLY;
    public static final ModConfigSpec.EnumValue<IntensityDisplay> INTENSITY_DISPLAY;
    public static final ModConfigSpec.BooleanValue SHOW_POTION_EFFECTS;
    public static final ModConfigSpec.BooleanValue SHOW_POTION_EFFECTS_INVENTORY;
    public static final ModConfigSpec.BooleanValue HIDE_COLD_SWEAT_HUD;
    public static final ModConfigSpec.BooleanValue SHOW_VANILLA_HEALTH;
    public static final ModConfigSpec.BooleanValue SHOW_VANILLA_ARMOR;
    public static final ModConfigSpec.BooleanValue SHOW_VANILLA_FOOD;
    public static final ModConfigSpec.BooleanValue SHOW_VANILLA_EXPERIENCE;
    public static final ModConfigSpec.BooleanValue SHOW_THIRST_HUD;
    public static final ModConfigSpec.BooleanValue SHOW_OTHER_STATUS_HUDS;
    public static final ModConfigSpec.BooleanValue STRESS_CAMERA_SHAKE;
    public static final ModConfigSpec.BooleanValue SHOW_TRAIT_ICONS_INVENTORY;
    public static final ModConfigSpec.DoubleValue TRAIT_ICON_SCALE;
    public static final ModConfigSpec.EnumValue<IconSet> MOODLE_ICON_SET;

    /** 强度可视化三态开关：色条、PZ 原版背景色、全部关闭。 */
    public enum IntensityDisplay { BAR, BACKGROUND, OFF }
    public enum IconSet { B42, B41 }

    static {
        B.comment("僵毁状态沉浸 - 客户端 HUD 设置").push("hud");
        HUD_ENABLED = B.comment("状态栏总开关").define("enabled", true);
        ICON_SCALE = B.comment("图标大小缩放 0.5 - 2.0").defineInRange("scale", 1.0, 0.5, 2.0);
        OFFSET_X = B.comment("图标栏 X 偏移（像素，负数向左）").defineInRange("offsetX", 0, -500, 500);
        OFFSET_Y = B.comment("图标栏 Y 偏移（像素，正数向下）").defineInRange("offsetY", 0, -500, 500);
        SHOW_LEVEL_NUMBERS = B.comment("在图标右下角显示等级数字").define("showLevelNumbers", true);
        VERTICAL_LAYOUT = B.comment("状态图标靠右竖着排列（关闭时改为右上角横排）").define("verticalLayout", false);
        INVENTORY_ONLY = B.comment("只在打开物品栏等界面时显示状态图标").define("inventoryOnly", false);
        INTENSITY_DISPLAY = B.comment("状态强度显示：色条、背景色或全部关闭")
                .defineEnum("intensityDisplay", IntensityDisplay.BAR);
        B.pop();

        B.comment("显示细节").push("display");
        SHOW_POTION_EFFECTS = B.comment("显示 HUD 上的药水状态图标（原版与其他模组的增益/减益图标）").define("showPotionEffects", true);
        SHOW_POTION_EFFECTS_INVENTORY = B.comment("打开物品栏时显示右侧药水状态图标").define("showPotionEffectsInInventory", true);
        HIDE_COLD_SWEAT_HUD = B.comment("安装冷汗模组时，屏蔽它自带的温度 HUD").define("hideColdSweatHud", true);
        SHOW_VANILLA_HEALTH = B.comment("显示原版生命值条").define("showVanillaHealth", true);
        SHOW_VANILLA_ARMOR = B.comment("显示原版护甲值条").define("showVanillaArmor", true);
        SHOW_VANILLA_FOOD = B.comment("显示原版饥饿值条").define("showVanillaFood", true);
        SHOW_VANILLA_EXPERIENCE = B.comment("显示原版经验条").define("showVanillaExperience", true);
        SHOW_THIRST_HUD = B.comment("显示口渴模组（Thirst Was Reclaimed）的口渴条与饱和度条").define("showThirstHud", true);
        SHOW_OTHER_STATUS_HUDS = B.comment("显示其他模组的状态 HUD").define("showOtherStatusHuds", true);
        STRESS_CAMERA_SHAKE = B.comment("压力 4 级时轻微视野抖动").define("stressCameraShake", true);
        SHOW_TRAIT_ICONS_INVENTORY = B.comment("物品栏职业面板中显示已获得特质图标").define("traitIconsInInventory", true);
        TRAIT_ICON_SCALE = B.comment("物品栏职业面板中特质图标大小缩放 0.5 - 2.0").defineInRange("traitIconScale", 1.0, 0.5, 2.0);
        MOODLE_ICON_SET = B.comment("状态图标版本：B42 新版或 B41 老版 PNG").defineEnum("moodleIconSet", IconSet.B41);
        B.pop();

        SPEC = B.build();
    }

    private ClientConfig() {}
}
