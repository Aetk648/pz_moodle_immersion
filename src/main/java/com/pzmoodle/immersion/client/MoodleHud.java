package com.pzmoodle.immersion.client;

import com.pzmoodle.immersion.PZMoodleImmersion;
import com.pzmoodle.immersion.config.ClientConfig;
import com.pzmoodle.immersion.moodle.Moodle;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.ArrayList;
import java.util.List;

/**
 * 状态图标栏。图标本身是 16x16 的像素方块图，直接叠在屏幕上，
 * 不再使用圆底强度背景；强度靠图标下方的小色条与可选的等级数字表达。
 * 支持两种排布：右上角横排 / 靠右竖排；也支持「只在打开物品栏时显示」。
 * 同时负责按配置开关隐藏原版与联动模组的状态条。
 */
@EventBusSubscriber(modid = PZMoodleImmersion.MODID,
        value = net.neoforged.api.distmarker.Dist.CLIENT,
        bus = EventBusSubscriber.Bus.GAME)
public final class MoodleHud {
    /** 强度色：1 级绿 → 4 级红。 */
    private static final int[] LEVEL_COLORS = {0x00000000, 0xFF6FBF4A, 0xFFE8C33F, 0xFFE88F3F, 0xFFE04A4A};
    private static final int[] SPECIAL_COLOR = {0x00000000, 0xFF6FA8DC, 0x00000000, 0x00000000, 0x00000000};
    private static final ResourceLocation[] INTENSITY_BACKGROUNDS = {
            null,
            PZMoodleImmersion.id("textures/gui/moodle/bg_solid_1.png"),
            PZMoodleImmersion.id("textures/gui/moodle/bg_solid_2.png"),
            PZMoodleImmersion.id("textures/gui/moodle/bg_solid_3.png"),
            PZMoodleImmersion.id("textures/gui/moodle/bg_solid_4.png")
    };

    public static void register(RegisterGuiLayersEvent event) {
        event.registerAboveAll(PZMoodleImmersion.id("moodle_bar"), MoodleHud::render);
    }

    // ————— 原版状态条开关：取消对应 GUI 层即可隐藏 —————
    @SubscribeEvent
    public static void onRenderInventoryMobEffects(ScreenEvent.RenderInventoryMobEffects event) {
        if (!ClientConfig.SHOW_POTION_EFFECTS.get() || !ClientConfig.SHOW_POTION_EFFECTS_INVENTORY.get()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRenderLayerPre(RenderGuiLayerEvent.Pre event) {
        ResourceLocation name = event.getName();
        if (name.equals(VanillaGuiLayers.PLAYER_HEALTH) && !ClientConfig.SHOW_VANILLA_HEALTH.get()) {
            event.setCanceled(true);
        } else if (name.equals(VanillaGuiLayers.ARMOR_LEVEL) && !ClientConfig.SHOW_VANILLA_ARMOR.get()) {
            event.setCanceled(true);
        } else if (name.equals(VanillaGuiLayers.FOOD_LEVEL) && !ClientConfig.SHOW_VANILLA_FOOD.get()) {
            event.setCanceled(true);
        } else if (name.equals(VanillaGuiLayers.EFFECTS) && !ClientConfig.SHOW_POTION_EFFECTS.get()) {
            event.setCanceled(true);
        } else if (isOtherStatusLayer(name)) {
            event.setCanceled(true);
        }
    }

    /** 判断某个 GUI 层是否属于「其他模组的生存状态条」且当前配置要求隐藏。 */
    private static boolean isOtherStatusLayer(ResourceLocation name) {
        String ns = name.getNamespace();
        String path = name.getPath().toLowerCase();
        if (ns.equals(PZMoodleImmersion.MODID) || ns.equals("minecraft") || ns.equals("neoforge")) return false;
        // 冷汗（cold_sweat）：vague_temp / body_temp / world_temp / food_effects
        if (ns.equals("cold_sweat")) {
            return ClientConfig.HIDE_COLD_SWEAT_HUD.get();
        }
        // 口渴模组（Thirst Was Reclaimed）：它把原版苹果皮式的饱和度/疲劳覆盖层
        // 也注册在自己命名空间下（thirst:saturation_overlay / thirst:exhaustion_overlay），
        // 所以口渴开关同时管住口渴条与饱和度条。
        if (ns.equals("thirst") || ns.equals("twa") || ns.equals("appleskin") || path.contains("thirst")) {
            return !ClientConfig.SHOW_THIRST_HUD.get();
        }
        // 饮食均衡（appleseed / cloudworks_api）等按食物营养画状态条的模组
        if (ns.equals("appleseed") || ns.equals("cloudworks_api") || ns.equals("diet")) {
            return !ClientConfig.SHOW_OTHER_STATUS_HUDS.get();
        }
        // 总开关：隐藏其余模组识别得出的状态条
        if (!ClientConfig.SHOW_OTHER_STATUS_HUDS.get()
                && (path.contains("nutrition") || path.contains("hunger") || path.contains("food")
                || path.contains("temp") || path.contains("calorie") || path.contains("diet")
                || path.contains("saturation") || path.contains("exhaustion"))) {
            return true;
        }
        return false;
    }

    /** 收集要显示的图标（按优先级）。生命/饥饿这类原版状态用本地数据兜底，
     *  进图第一刻就能显示；服务端同步来的等级会覆盖同名项。 */
    private static List<Moodle> collectShown(Minecraft mc) {
        List<Moodle> shown = new ArrayList<>();
        for (Moodle m : Moodle.ALL) {
            if (effectiveLevel(mc, m) > 0) shown.add(m);
        }
        return shown;
    }

    /**
     * 图标排布：横排从屏幕右缘向左延伸、竖排从上向下且放不下时换到左边一列。
     * 之前横排是向右排，第二个图标起就被排出了屏幕外——负重和体力恰好排在最后，
     * 所以看起来「永远不显示」。这里统一算好每个图标的位置，渲染与悬停提示共用。
     */
    private static List<int[]> layout(Minecraft mc, List<Moodle> shown) {
        double scale = ClientConfig.ICON_SCALE.get();
        int size = (int) (24 * scale);
        int gap = Math.max(1, (int) (3 * scale));
        boolean vertical = ClientConfig.VERTICAL_LAYOUT.get();
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();
        int x = screenW - size - 4 + ClientConfig.OFFSET_X.get();
        int y = 4 + ClientConfig.OFFSET_Y.get();
        List<int[]> out = new ArrayList<>(shown.size());
        for (Moodle m : shown) {
            if (vertical) {
                if (y + size + 2 > screenH) {   // 这一列放不下：换到左边一列重新排
                    x -= size + gap;
                    y = 4 + ClientConfig.OFFSET_Y.get();
                }
            } else if (x < 0) {                 // 横排贴到左边缘：换到上一行继续排
                x = screenW - size - 4 + ClientConfig.OFFSET_X.get();
                y += size + gap + 4;
            }
            out.add(new int[]{x, y});
            if (vertical) y += size + gap;
            else x -= size + gap;
        }
        return out;
    }

    private static void render(GuiGraphics guiGraphics, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (!ClientConfig.HUD_ENABLED.get() || mc.player == null || mc.options.hideGui) return;
        if (mc.getDebugOverlay().showDebugScreen()) return;

        // 「只在打开物品栏时显示」：没开容器界面就不画
        if (ClientConfig.INVENTORY_ONLY.get() && !isContainerScreenOpen(mc)) return;

        List<Moodle> shown = collectShown(mc);
        if (shown.isEmpty()) return;
        double scale = ClientConfig.ICON_SCALE.get();
        int size = (int) (24 * scale);
        List<int[]> pos = layout(mc, shown);

        for (int i = 0; i < shown.size(); i++) {
            Moodle m = shown.get(i);
            int x = pos.get(i)[0], y = pos.get(i)[1];
            int level = effectiveLevel(mc, m);

            // PZ 原版的强度背景：先画带颜色的圆角底，再画状态图标。
            if (ClientConfig.INTENSITY_DISPLAY.get() == ClientConfig.IntensityDisplay.BACKGROUND && level > 0) {
                int bgIndex = Math.min(level, INTENSITY_BACKGROUNDS.length - 1);
                guiGraphics.blit(INTENSITY_BACKGROUNDS[bgIndex], x, y, size, size, 0f, 0f,
                        48, 48, 48, 48);
            }
            ResourceLocation icon = displayIcon(m, level);
            int textureSize = m.textureSize(ClientConfig.MOODLE_ICON_SET.get());
            guiGraphics.blit(icon, x, y, size, size, 0f, 0f,
                    textureSize, textureSize, textureSize, textureSize);

            // 强度色条：画在图标下方，宽度随等级增长。
            if (ClientConfig.INTENSITY_DISPLAY.get() == ClientConfig.IntensityDisplay.BAR && level > 0) {
                int barColor = m == Moodle.FULL || m == Moodle.WET
                        ? SPECIAL_COLOR[Math.min(level, 1)]
                        : LEVEL_COLORS[Math.min(level, 4)];
                int barW = Math.max(2, size * Math.min(level, m.maxLevel) / Math.max(1, m.maxLevel));
                guiGraphics.fill(x, y + size, x + barW, y + size + 2, barColor);
            }

            // 等级数字
            if (ClientConfig.SHOW_LEVEL_NUMBERS.get() && level > 1 && m.maxLevel > 1) {
                String s = Integer.toString(level);
                int tx = x + size - mc.font.width(s) - 1;
                int ty = y + size - mc.font.lineHeight - 1;
                guiGraphics.drawString(mc.font, s, tx + 1, ty + 1, 0xAA000000, false);
                guiGraphics.drawString(mc.font, s, tx, ty, 0xFFFFFFFF, false);
            }
        }

        // 没开界面时提示直接画在 HUD 上；开界面时交给 ScreenEvent 画在界面之上，
        // 否则提示会被界面盖住，看起来像「悬停不灵敏」。
        if (mc.screen == null) {
            double mouseX = mc.mouseHandler.xpos() / mc.getWindow().getGuiScale();
            double mouseY = mc.mouseHandler.ypos() / mc.getWindow().getGuiScale();
            renderHoverTooltip(guiGraphics, mc, (int) mouseX, (int) mouseY);
        }
    }

    /** 打开任意界面时，在界面之上响应状态图标的悬停提示。 */
    @SubscribeEvent
    public static void onScreenRender(ScreenEvent.Render.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (!ClientConfig.HUD_ENABLED.get() || mc.player == null || mc.options.hideGui) return;
        if (mc.getDebugOverlay().showDebugScreen()) return;
        if (ClientConfig.INVENTORY_ONLY.get() && !(event.getScreen() instanceof AbstractContainerScreen<?>)) return;
        renderHoverTooltip(event.getGuiGraphics(), mc, event.getMouseX(), event.getMouseY());
    }

    /** 命中范围与图标本身一致（再加上下方的强度色条），悬停即出描述。 */
    private static void renderHoverTooltip(GuiGraphics guiGraphics, Minecraft mc, int mouseX, int mouseY) {
        List<Moodle> shown = collectShown(mc);
        if (shown.isEmpty()) return;
        List<int[]> pos = layout(mc, shown);

        for (int i = 0; i < shown.size(); i++) {
            Moodle m = shown.get(i);
            int x = pos.get(i)[0], y = pos.get(i)[1];
            if (mouseX >= x && mouseX <= x + size(mc) && mouseY >= y && mouseY <= y + size(mc) + 2) {
                List<Component> lines = new ArrayList<>();
                lines.add(Component.translatable("moodle.pz_moodle_immersion." + m.id + ".name")
                        .withStyle(ChatFormatting.WHITE));
                lines.add(Component.translatable("moodle.pz_moodle_immersion." + m.id + ".level."
                        + effectiveLevel(mc, m)).withStyle(ChatFormatting.GRAY));
                if (m == Moodle.STAMINA) {
                    lines.add(Component.literal(String.format(java.util.Locale.ROOT, "体力 %.0f%%", ClientState.stamina / ClientState.staminaCap * 100f))
                            .withStyle(ChatFormatting.DARK_AQUA));
                } else if (m == Moodle.HEAVY_LOAD) {
                    lines.add(Component.literal("负重程度 " + effectiveLevel(mc, m) + "/4")
                            .withStyle(ChatFormatting.DARK_AQUA));
                }
                guiGraphics.renderComponentTooltip(mc.font, lines, mouseX, mouseY);
                return;
            }
        }
    }

    private static int size(Minecraft mc) {
        return (int) (24 * ClientConfig.ICON_SCALE.get());
    }

    /** 玩家是否正开着物品栏/箱子这类容器界面。 */
    private static boolean isContainerScreenOpen(Minecraft mc) {
        return mc.screen instanceof AbstractContainerScreen<?>;
    }

    /** 服务端同步的等级优先；没有同步数据时用客户端本地状态兜底，保证进图就有显示。 */
    private static int effectiveLevel(Minecraft mc, Moodle m) {
        int synced = ClientState.level(m);
        return switch (m) {
            case HUNGER -> Math.max(synced, hungerLevel(mc));
            case PAIN -> Math.max(synced, painLevel(mc));
            case STAMINA -> Math.max(synced, com.pzmoodle.immersion.moodle.Stamina.iconLevel(
                    com.pzmoodle.immersion.moodle.Stamina.percent(ClientState.stamina, ClientState.staminaCap)));
            case HEAVY_LOAD -> Math.max(synced, ClientState.encumbranceLevel);
            default -> synced;
        };
    }

    private static ResourceLocation displayIcon(Moodle moodle, int level) {
        ClientConfig.IconSet set = ClientConfig.MOODLE_ICON_SET.get();
        if (moodle == Moodle.STAMINA && set == ClientConfig.IconSet.B42 && level >= 4) {
            return PZMoodleImmersion.id("textures/gui/moodle/stamina_exhausted.png");
        }
        if (moodle == Moodle.HEAVY_LOAD && set == ClientConfig.IconSet.B42) {
            return PZMoodleImmersion.id("textures/gui/moodle/status_heavy_load_b42.png");
        }
        return moodle.iconFor(set);
    }

    private static int hungerLevel(Minecraft mc) {
        int sat = mc.player.getFoodData().getFoodLevel();
        if (sat <= 0) return 4;
        if (sat <= 4) return 3;
        if (sat <= 8) return 2;
        if (sat <= 14) return 1;
        return 0;
    }

    private static int painLevel(Minecraft mc) {
        float max = Math.max(1f, mc.player.getMaxHealth());
        float missing = (1f - mc.player.getHealth() / max) * 100f;
        if (missing <= 0f) return 0;
        if (missing >= 75f) return 4;
        if (missing >= 50f) return 3;
        if (missing >= 25f) return 2;
        return 1;
    }

    private MoodleHud() {}
}
