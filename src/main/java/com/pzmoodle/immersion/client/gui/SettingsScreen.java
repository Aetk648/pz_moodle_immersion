package com.pzmoodle.immersion.client.gui;

import com.pzmoodle.immersion.config.ClientConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** 游戏内客户端设置界面：HUD 外观、状态强度样式与 B41/B42 资源切换。 */
public class SettingsScreen extends Screen {
    private EditBox scale;
    private EditBox offsetX;
    private EditBox offsetY;
    private EditBox traitIconScale;

    public SettingsScreen() {
        super(Component.translatable("screen.pz_moodle_immersion.settings"));
    }

    @Override
    protected void init() {
        int cx = width / 2;
        addRenderableWidget(label("screen.pz_moodle_immersion.scale", cx - 80, 22));
        scale = new EditBox(font, cx - 80, 34, 160, 18, Component.translatable("screen.pz_moodle_immersion.scale"));
        scale.setValue(String.format(java.util.Locale.ROOT, "%.2f", ClientConfig.ICON_SCALE.get()));
        addRenderableWidget(scale);
        addRenderableWidget(label("screen.pz_moodle_immersion.offset_x", cx - 80, 56));
        offsetX = new EditBox(font, cx - 80, 68, 160, 18, Component.translatable("screen.pz_moodle_immersion.offset_x"));
        offsetX.setValue(Integer.toString(ClientConfig.OFFSET_X.get()));
        addRenderableWidget(offsetX);
        addRenderableWidget(label("screen.pz_moodle_immersion.offset_y", cx - 80, 90));
        offsetY = new EditBox(font, cx - 80, 102, 160, 18, Component.translatable("screen.pz_moodle_immersion.offset_y"));
        offsetY.setValue(Integer.toString(ClientConfig.OFFSET_Y.get()));
        addRenderableWidget(offsetY);
        addRenderableWidget(label("screen.pz_moodle_immersion.trait_icon_scale", cx - 80, 122));
        traitIconScale = new EditBox(font, cx - 80, 134, 160, 18,
                Component.translatable("screen.pz_moodle_immersion.trait_icon_scale"));
        traitIconScale.setValue(String.format(java.util.Locale.ROOT, "%.2f", ClientConfig.TRAIT_ICON_SCALE.get()));
        addRenderableWidget(traitIconScale);

        int col1 = cx - 154;
        int col2 = cx + 4;
        addRenderableWidget(header("screen.pz_moodle_immersion.section_hud", col1, 160));

        int y = 174;
        toggle(col1, y, "screen.pz_moodle_immersion.toggle_hud", ClientConfig.HUD_ENABLED);
        toggle(col2, y, "screen.pz_moodle_immersion.toggle_numbers", ClientConfig.SHOW_LEVEL_NUMBERS);
        y += 20;
        cycleIntensity(col1, y);
        toggle(col2, y, "screen.pz_moodle_immersion.toggle_vertical", ClientConfig.VERTICAL_LAYOUT);
        y += 20;
        toggle(col1, y, "screen.pz_moodle_immersion.toggle_inventory_only", ClientConfig.INVENTORY_ONLY);
        toggle(col2, y, "screen.pz_moodle_immersion.toggle_trait_icons", ClientConfig.SHOW_TRAIT_ICONS_INVENTORY);
        y += 20;
        toggle(col1, y, "screen.pz_moodle_immersion.toggle_potion", ClientConfig.SHOW_POTION_EFFECTS);
        toggle(col2, y, "screen.pz_moodle_immersion.toggle_potion_inventory", ClientConfig.SHOW_POTION_EFFECTS_INVENTORY);
        y += 20;
        cycleIconSet(col1, y);
        toggle(col2, y, "screen.pz_moodle_immersion.toggle_coldsweat", ClientConfig.HIDE_COLD_SWEAT_HUD);
        y += 20;
        toggle(col1, y, "screen.pz_moodle_immersion.toggle_health", ClientConfig.SHOW_VANILLA_HEALTH);
        toggle(col2, y, "screen.pz_moodle_immersion.toggle_armor", ClientConfig.SHOW_VANILLA_ARMOR);
        y += 20;
        toggle(col1, y, "screen.pz_moodle_immersion.toggle_food", ClientConfig.SHOW_VANILLA_FOOD);
        toggle(col2, y, "screen.pz_moodle_immersion.toggle_experience", ClientConfig.SHOW_VANILLA_EXPERIENCE);
        y += 20;
        toggle(col1, y, "screen.pz_moodle_immersion.toggle_thirst", ClientConfig.SHOW_THIRST_HUD);
        toggle(col2, y, "screen.pz_moodle_immersion.toggle_other", ClientConfig.SHOW_OTHER_STATUS_HUDS);

        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> {
            save();
            onClose();
        }).bounds(cx - 60, height - 26, 120, 20).build());
    }

    private StringWidget header(String key, int x, int y) {
        StringWidget w = new StringWidget(Component.translatable(key), font).alignLeft().setColor(0xFFD35A);
        w.setPosition(x, y);
        return w;
    }

    private StringWidget label(String key, int x, int y) {
        StringWidget w = new StringWidget(Component.translatable(key), font).alignLeft().setColor(0xE0E0E0);
        w.setPosition(x, y);
        return w;
    }

    private void toggle(int x, int y, String key, net.neoforged.neoforge.common.ModConfigSpec.BooleanValue value) {
        addRenderableWidget(new SlideSwitch(x, y, 150, 18,
                () -> value.get() ? 1 : 0, 2,
                next -> {
                    value.set(next == 1);
                    ClientConfig.SPEC.save();
                }, () -> onOff(key, value.get())));
    }

    private void cycleIntensity(int x, int y) {
        addRenderableWidget(new SlideSwitch(x, y, 150, 18,
                () -> ClientConfig.INTENSITY_DISPLAY.get().ordinal(), ClientConfig.IntensityDisplay.values().length,
                next -> {
                    ClientConfig.INTENSITY_DISPLAY.set(ClientConfig.IntensityDisplay.values()[next]);
                    ClientConfig.SPEC.save();
                }, SettingsScreen::intensityLabel));
    }

    private void cycleIconSet(int x, int y) {
        addRenderableWidget(new SlideSwitch(x, y, 150, 18,
                () -> ClientConfig.MOODLE_ICON_SET.get().ordinal(), ClientConfig.IconSet.values().length,
                next -> {
                    ClientConfig.MOODLE_ICON_SET.set(ClientConfig.IconSet.values()[next]);
                    ClientConfig.SPEC.save();
                }, SettingsScreen::iconSetLabel));
    }

    private static Component intensityLabel() {
        return Component.translatable("screen.pz_moodle_immersion.toggle_intensity",
                Component.translatable("screen.pz_moodle_immersion.intensity_" + ClientConfig.INTENSITY_DISPLAY.get().name().toLowerCase())
                        .withStyle(ChatFormatting.GOLD));
    }

    private static Component iconSetLabel() {
        return Component.translatable("screen.pz_moodle_immersion.toggle_icon_set",
                Component.translatable("screen.pz_moodle_immersion.icon_set_" + ClientConfig.MOODLE_ICON_SET.get().name().toLowerCase())
                        .withStyle(ChatFormatting.GOLD));
    }

    private static Component onOff(String key, boolean on) {
        return Component.translatable(key, Component.translatable(on ? "options.on" : "options.off")
                .withStyle(on ? ChatFormatting.GREEN : ChatFormatting.RED));
    }

    private void save() {
        try {
            double s = Math.max(0.5, Math.min(2.0, Double.parseDouble(scale.getValue())));
            double traitScale = Math.max(0.5, Math.min(2.0, Double.parseDouble(traitIconScale.getValue())));
            int x = Math.max(-500, Math.min(500, Integer.parseInt(offsetX.getValue())));
            int y = Math.max(-500, Math.min(500, Integer.parseInt(offsetY.getValue())));
            ClientConfig.ICON_SCALE.set(s);
            ClientConfig.TRAIT_ICON_SCALE.set(traitScale);
            ClientConfig.OFFSET_X.set(x);
            ClientConfig.OFFSET_Y.set(y);
            ClientConfig.SPEC.save();
        } catch (NumberFormatException ignored) {
            // 输入不合法时保留原值，不让配置界面崩溃。
        }
    }

    @Override
    public void onClose() {
        save();
        super.onClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 不用 renderBackground：它会触发全屏模糊后处理并重置纹理采样状态，导致界面发糊。
        Backdrop.render(graphics, width, height);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
