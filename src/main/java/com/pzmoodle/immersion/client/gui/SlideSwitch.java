package com.pzmoodle.immersion.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/** 轻量滑动开关：支持二态布尔值，也支持三态强度显示和 B41/B42 切换。 */
public final class SlideSwitch extends AbstractWidget {
    private final IntSupplier state;
    private final IntConsumer setter;
    private final int stateCount;
    private final Supplier<Component> messageSupplier;

    public SlideSwitch(int x, int y, int width, int height, IntSupplier state, int stateCount,
                       IntConsumer setter, Supplier<Component> messageSupplier) {
        super(x, y, width, height, Component.empty());
        this.state = state;
        this.stateCount = Math.max(2, stateCount);
        this.setter = setter;
        this.messageSupplier = messageSupplier;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        int current = Math.max(0, Math.min(stateCount - 1, state.getAsInt()));
        Component message = messageSupplier.get();
        int trackWidth = 34;
        int trackX = getX() + getWidth() - trackWidth;
        int trackY = getY() + 2;
        int textRight = trackX - 5;
        int textColor = isHoveredOrFocused() ? 0xFFFFFFFF : 0xFFE0E0E0;
        graphics.enableScissor(getX(), getY(), textRight, getY() + getHeight());
        graphics.drawString(mc.font, message, getX(), getY() + (getHeight() - mc.font.lineHeight) / 2,
                textColor, false);
        graphics.disableScissor();

        int trackColor = isHoveredOrFocused() ? 0xFF5E5E6B : 0xFF3A3A46;
        graphics.fill(trackX, trackY, trackX + trackWidth, trackY + 14, trackColor);
        graphics.renderOutline(trackX, trackY, trackWidth, 14, 0xFF858593);

        int knobWidth = 12;
        int travel = trackWidth - knobWidth - 4;
        int knobX = trackX + 2 + (int) Math.round(travel * current / (double) (stateCount - 1));
        int knobColor = current == stateCount - 1 ? 0xFF6FC44E : 0xFFD1B66A;
        graphics.fill(knobX, trackY + 2, knobX + knobWidth, trackY + 12, knobColor);
        graphics.renderOutline(knobX, trackY + 2, knobWidth, 10, 0xFF1C1C22);
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        setter.accept((state.getAsInt() + 1) % stateCount);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, messageSupplier.get());
    }
}
