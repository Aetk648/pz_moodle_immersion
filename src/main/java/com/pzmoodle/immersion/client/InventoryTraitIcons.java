package com.pzmoodle.immersion.client;

import com.pzmoodle.immersion.PZMoodleImmersion;
import com.pzmoodle.immersion.config.ClientConfig;
import com.pzmoodle.immersion.moodle.Skill;
import com.pzmoodle.immersion.moodle.StatData;
import com.pzmoodle.immersion.trait.Occupation;
import com.pzmoodle.immersion.trait.OccupationTrait;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.util.ArrayList;
import java.util.List;

/**
 * 物品栏中的职业入口：副手槽上方显示职业图标。
 * 悬停弹出职业介绍框（最后绘制、盖在所有界面元素之上，背景做毛玻璃模糊），
 * 框内以缩小尺寸展示已获得特质图标（不响应悬停说明）；
 * 左键在角色左侧展开可滚动的技能面板。
 */
@EventBusSubscriber(modid = PZMoodleImmersion.MODID,
        value = net.neoforged.api.distmarker.Dist.CLIENT,
        bus = EventBusSubscriber.Bus.GAME)
public final class InventoryTraitIcons {
    private static final int OCCUPATION_ICON = 18;
    private static final int HOVER_TRAIT_ICON = 12;
    private static final int PANEL_WIDTH = 154;
    private static final int PANEL_SLIDE_SPEED = 5;
    private static final int PANEL_BG = 0xE80B0B10;
    private static final int HOVER_BG = 0xB4101018;
    private static final int PANEL_BORDER = 0xFF5B5B67;
    private static final int PANEL_INNER = 0xFF22222A;
    private static final int GOLD = 0xFFFFD35A;
    private static final int LABEL = 0xFFB8B8C0;
    private static final int VALUE = 0xFFF2F2F2;
    private static final int DESCRIPTION = 0xFFB0B0B8;
    private static final int MUTED = 0xFF858590;
    private static final int HINT = 0xFF787882;
    private static final int TEXT_WRAP = 150;
    private static final int MIN_INNER = 96;

    private static boolean expanded;
    private static float animation;
    private static long lastFrameNanos;
    private static int skillScroll;
    private static int lastScreenHash;
    private static int lastListTop;
    private static int lastListBottom;
    private static int lastListX;
    private static int lastListWidth;
    private static TextureTarget blurStage1;
    private static TextureTarget blurStage2;

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRender(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof InventoryScreen screen)) {
            resetState();
            return;
        }

        if (lastScreenHash != System.identityHashCode(screen)) {
            lastScreenHash = System.identityHashCode(screen);
            skillScroll = 0;
        }

        animation = advanceAnimation();
        boolean panelVisible = animation > 0.01F;
        boolean interactive = animation > 0.75F;
        GuiGraphics graphics = event.getGuiGraphics();
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        int mouseX = event.getMouseX();
        int mouseY = event.getMouseY();

        if (panelVisible) {
            drawPanel(graphics, font, screen, animation, interactive, mouseX, mouseY);
        }

        IconBounds icon = occupationIconBounds(screen);
        drawOccupationIcon(graphics, icon);
        boolean overIcon = contains(icon, mouseX, mouseY);
        drawOccupationHoverBox(graphics, font, icon, overIcon, panelVisible);
    }

    @SubscribeEvent
    public static void onMouseClicked(ScreenEvent.MouseButtonPressed.Pre event) {
        if (!(event.getScreen() instanceof InventoryScreen screen) || event.getButton() != 0) return;
        IconBounds icon = occupationIconBounds(screen);
        if (contains(icon, event.getMouseX(), event.getMouseY())) {
            expanded = !expanded;
            if (!expanded) skillScroll = 0;
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onMouseScrolled(ScreenEvent.MouseScrolled.Pre event) {
        if (!(event.getScreen() instanceof InventoryScreen screen) || animation <= 0.75F) return;
        PanelBounds panel = panelBounds(screen, animation);
        if (!contains(lastListX, lastListTop, lastListWidth, lastListBottom - lastListTop,
                (int) event.getMouseX(), (int) event.getMouseY())) {
            return;
        }
        int step = (int) Math.copySign(24, event.getScrollDeltaY());
        skillScroll = SkillPanel.clampScroll(skillScroll - step, lastListBottom - lastListTop);
        event.setCanceled(true);
    }

    private static float advanceAnimation() {
        long now = System.nanoTime();
        float delta = lastFrameNanos == 0 ? 1F / 60F : Math.min(0.1F, (now - lastFrameNanos) / 1_000_000_000F);
        lastFrameNanos = now;
        float target = expanded ? 1F : 0F;
        float speed = PANEL_SLIDE_SPEED * delta;
        if (Math.abs(target - animation) <= speed) return target;
        return animation + Math.signum(target - animation) * speed;
    }

    private static void resetState() {
        expanded = false;
        animation = 0F;
        lastScreenHash = 0;
        lastFrameNanos = 0;
        lastListTop = 0;
        lastListBottom = 0;
    }

    private static void drawOccupationIcon(GuiGraphics graphics, IconBounds icon) {
        Occupation.Def occupation = currentOccupation();
        ResourceLocation texture = occupation == null ? null : occupationIcon(occupation.id());
        if (texture != null) {
            graphics.blit(texture, icon.x(), icon.y(), icon.width(), icon.height(),
                    0F, 0F, 18, 18, 18, 18);
            drawBevel(graphics, icon.x(), icon.y(), icon.width());
        } else {
            graphics.fill(icon.x(), icon.y(), icon.x() + icon.width(), icon.y() + icon.height(), PANEL_INNER);
        }
        graphics.renderOutline(icon.x(), icon.y(), icon.width(), icon.height(),
                expanded ? 0xFFFFD35A : 0xFF5B5B67);
        if (texture == null) {
            Component question = Component.literal("?");
            graphics.drawString(Minecraft.getInstance().font, question,
                    icon.x() + (icon.width() - Minecraft.getInstance().font.width(question)) / 2,
                    icon.y() + 5, VALUE, true);
        }
    }

    private static IconBounds occupationIconBounds(InventoryScreen screen) {
        Slot offhand = null;
        for (Slot slot : screen.getMenu().slots) {
            if (slot != null && slot.container instanceof Inventory
                    && slot.getContainerSlot() == Inventory.SLOT_OFFHAND) {
                offhand = slot;
                break;
            }
        }
        int slotX = screen.getGuiLeft() + (offhand == null ? -24 : offhand.x);
        int slotY = screen.getGuiTop() + (offhand == null ? 26 : offhand.y);
        int x = slotX + (16 - OCCUPATION_ICON) / 2;
        int y = slotY - OCCUPATION_ICON - 3;
        return new IconBounds(x, y, OCCUPATION_ICON, OCCUPATION_ICON);
    }

    private static void drawOccupationHoverBox(GuiGraphics graphics, Font font, IconBounds icon,
                                               boolean overIcon, boolean panelVisible) {
        if (panelVisible || !overIcon) {
            return;
        }

        Occupation.Def occupation = currentOccupation();
        boolean showTraits = ClientConfig.SHOW_TRAIT_ICONS_INVENTORY.get() && !ClientState.TRAITS.isEmpty();
        List<OccupationTrait.Def> owned = showTraits ? ownedTraits() : List.of();

        List<FormattedCharSequence> lines = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        if (occupation == null) {
            lines.add(Component.translatable("screen.pz_moodle_immersion.panel.no_occupation").getVisualOrderText());
            colors.add(0xFFE1B84A);
        } else {
            lines.add(Component.translatable(occupation.nameKey()).getVisualOrderText());
            colors.add(GOLD);
            for (var line : font.split(com.pzmoodle.immersion.admin.DefOverrides.occupationDescription(occupation), TEXT_WRAP)) {
                lines.add(line);
                colors.add(DESCRIPTION);
            }
        }
        if (showTraits) {
            lines.add(Component.translatable("screen.pz_moodle_immersion.panel.hover_traits", owned.size()).getVisualOrderText());
            colors.add(0xFF55FFFF);
        }
        lines.add(Component.translatable("screen.pz_moodle_immersion.panel.click_hint").getVisualOrderText());
        colors.add(HINT);

        int textWidth = 0;
        for (FormattedCharSequence line : lines) textWidth = Math.max(textWidth, font.width(line));
        int size = HOVER_TRAIT_ICON;
        int gap = 2;
        int innerWidth = Math.max(textWidth, MIN_INNER);
        int perRow = showTraits ? Math.max(1, Math.min(10, (innerWidth + gap) / (size + gap))) : 1;
        int gridWidth = showTraits ? Math.min(owned.size(), perRow) * (size + gap) - gap : 0;
        int rows = showTraits ? (owned.size() + perRow - 1) / perRow : 0;
        int boxWidth = Math.max(innerWidth, gridWidth) + 8;
        int boxHeight = lines.size() * 10 + (showTraits ? rows * (size + gap) + 3 : 0) + 6;

        int screenW = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int screenH = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        int boxX = Math.max(2, Math.min(icon.x(), screenW - boxWidth - 2));
        int boxY = icon.y() + icon.height() + 3;
        if (boxY + boxHeight > screenH - 2) boxY = Math.max(2, screenH - boxHeight - 2);

        drawBlurredBackdrop(graphics, boxX, boxY, boxWidth, boxHeight);
        graphics.fill(boxX, boxY, boxX + boxWidth, boxY + boxHeight, HOVER_BG);
        graphics.renderOutline(boxX, boxY, boxWidth, boxHeight, PANEL_BORDER);

        int cursorY = boxY + 4;
        for (int i = 0; i < lines.size(); i++) {
            graphics.drawString(font, lines.get(i), boxX + 4, cursorY, colors.get(i), true);
            cursorY += 10;
        }

        if (showTraits) {
            cursorY += 1;
            for (int i = 0; i < owned.size(); i++) {
                int col = i % perRow;
                int row = i / perRow;
                int x = boxX + 4 + col * (size + gap);
                int y = cursorY + row * (size + gap);
                drawTraitIcon(graphics, owned.get(i), x, y, size);
            }
        }
    }

    /**
     * 毛玻璃：把当前屏幕两次降采样到小纹理，再以线性滤波画回介绍框区域，
     * 使被框盖住的物品栏/背景呈现真实模糊。失败时静默退回普通半透明底。
     */
    private static void drawBlurredBackdrop(GuiGraphics graphics, int x, int y, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        RenderTarget main = mc.getMainRenderTarget();
        try {
            graphics.flush();
            int w1 = Math.max(1, main.width / 2);
            int h1 = Math.max(1, main.height / 2);
            int w2 = Math.max(1, w1 / 4);
            int h2 = Math.max(1, h1 / 4);
            if (blurStage1 == null || blurStage1.width != w1 || blurStage1.height != h1) {
                releaseBlurTargets();
                blurStage1 = new TextureTarget(w1, h1, false, false);
                blurStage1.setFilterMode(GL11.GL_LINEAR);
                blurStage2 = new TextureTarget(w2, h2, false, false);
                blurStage2.setFilterMode(GL11.GL_LINEAR);
            }
            blitColor(main, blurStage1);
            blitColor(blurStage1, blurStage2);
            main.bindWrite(true);

            double guiScale = mc.getWindow().getGuiScale();
            float u0 = clamp01(x * guiScale / blurStage2.width);
            float u1 = clamp01((x + width) * guiScale / blurStage2.width);
            float vTop = clamp01(1F - y * guiScale / blurStage2.height);
            float vBottom = clamp01(1F - (y + height) * guiScale / blurStage2.height);

            RenderSystem.setShader(GameRenderer::getPositionTexShader);
            RenderSystem.setShaderTexture(0, blurStage2.getColorTextureId());
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableCull();
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            PoseStack.Pose pose = graphics.pose().last();
            BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
            builder.addVertex(pose.pose(), x, y, 0.0F).setUv(u0, vTop);
            builder.addVertex(pose.pose(), x + width, y, 0.0F).setUv(u1, vTop);
            builder.addVertex(pose.pose(), x + width, y + height, 0.0F).setUv(u1, vBottom);
            builder.addVertex(pose.pose(), x, y + height, 0.0F).setUv(u0, vBottom);
            BufferUploader.drawWithShader(builder.build());
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
        } catch (Throwable ignored) {
            // 驱动或后端不支持时直接跳过模糊，只画半透明底
        } finally {
            main.bindWrite(true);
        }
    }

    private static void blitColor(RenderTarget from, RenderTarget to) {
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, from.frameBufferId);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, to.frameBufferId);
        GlStateManager._glBlitFrameBuffer(0, 0, from.width, from.height,
                0, 0, to.width, to.height, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_LINEAR);
    }

    private static float clamp01(double value) {
        return (float) Math.min(1.0, Math.max(0.0, value));
    }

    private static void releaseBlurTargets() {
        if (blurStage1 != null) {
            blurStage1.destroyBuffers();
            blurStage1 = null;
        }
        if (blurStage2 != null) {
            blurStage2.destroyBuffers();
            blurStage2 = null;
        }
    }

    private static void drawPanel(GuiGraphics graphics, Font font, InventoryScreen screen,
                                  float progress, boolean interactive, int mouseX, int mouseY) {
        PanelBounds panel = panelBounds(screen, progress);
        graphics.fill(panel.x(), panel.y(), panel.x() + panel.width(), panel.y() + panel.height(), PANEL_BG);
        graphics.renderOutline(panel.x(), panel.y(), panel.width(), panel.height(), PANEL_BORDER);
        int cursorY = drawPanelHeader(graphics, font, panel, interactive ? mouseX : -1, interactive ? mouseY : -1);

        int listTop = cursorY + 2;
        int listBottom = panel.y() + panel.height() - 5;
        lastListX = panel.x() + 4;
        lastListWidth = panel.width() - 8;
        lastListTop = listTop;
        lastListBottom = listBottom;
        if (listBottom <= listTop) return;
        SkillPanel.drawList(graphics, font, lastListX, listTop, listBottom, lastListWidth, skillScroll,
                new SkillPanel.Values() {
                    @Override
                    public int occupationBase(Skill skill) {
                        return Occupation.stat(ClientState.occupation, skill);
                    }

                    @Override
                    public int traitBonus(Skill skill) {
                        return OccupationTrait.traitBonus(ClientState.TRAITS, skill);
                    }

                    @Override
                    public int growthLevel(Skill skill) {
                        return ClientState.growthLevel(skill);
                    }

                    @Override
                    public int growthXp(Skill skill) {
                        return ClientState.growthXp(skill);
                    }
                }, interactive ? mouseX : -1, interactive ? mouseY : -1);
    }

    private static int drawPanelHeader(GuiGraphics graphics, Font font, PanelBounds panel,
                                       int mouseX, int mouseY) {
        int x = panel.x() + 5;
        int y = panel.y() + 5;
        int contentRight = panel.x() + panel.width() - 5;
        Occupation.Def occupation = currentOccupation();

        graphics.fill(x, y, contentRight, y + 22, PANEL_INNER);
        ResourceLocation icon = occupation == null ? null : occupationIcon(occupation.id());
        if (icon != null) {
            graphics.blit(icon, x + 2, y + 2, 18, 18, 0F, 0F, 18, 18, 18, 18);
            drawBevel(graphics, x + 2, y + 2, 18);
            graphics.renderOutline(x + 2, y + 2, 18, 18, 0xFF45454F);
        } else {
            graphics.renderOutline(x + 2, y + 2, 18, 18, 0xFF6E6E78);
            Component fallback = Component.literal("?");
            graphics.drawString(font, fallback, x + 8, y + 6, VALUE, true);
        }
        Component title = occupation == null
                ? Component.translatable("screen.pz_moodle_immersion.panel.no_occupation")
                : Component.translatable(occupation.nameKey());
        graphics.drawString(font, title, x + 24, y + 3, occupation == null ? 0xFFE1B84A : GOLD, true);
        Component level = Component.translatable("screen.pz_moodle_immersion.character_level_inv",
                ClientState.characterLevel(), ClientState.characterMaxLevel());
        graphics.drawString(font, level, x + 24, y + 13, VALUE, true);
        y += 26;

        Component kills = Component.translatable("screen.pz_moodle_immersion.panel.zombie_kills", ClientState.zombieKills);
        Component days = Component.translatable("screen.pz_moodle_immersion.panel.days_alive", ClientState.daysAlive);
        graphics.drawString(font, kills, x, y, LABEL, true);
        graphics.drawString(font, days, Math.max(x + font.width(kills) + 12, contentRight - font.width(days)),
                y, LABEL, true);
        y += 12;

        if (occupation != null) {
            for (var line : font.split(com.pzmoodle.immersion.admin.DefOverrides.occupationDescription(occupation), panel.width() - 10)) {
                graphics.drawString(font, line, x, y, DESCRIPTION, true);
                y += 10;
            }
        }
        y += 2;

        if (ClientConfig.SHOW_TRAIT_ICONS_INVENTORY.get() && !ClientState.TRAITS.isEmpty()) {
            Component traitsLabel = Component.translatable("screen.pz_moodle_immersion.panel.traits");
            graphics.drawString(font, traitsLabel, x, y, MUTED, true);
            y += 11;
            int size = traitIconSize();
            int gap = Math.max(2, Math.round(2 * ClientConfig.TRAIT_ICON_SCALE.get().floatValue()));
            int perRow = Math.max(1, (contentRight - x + gap) / (size + gap));
            List<OccupationTrait.Def> owned = ownedTraits();
            for (int i = 0; i < owned.size(); i++) {
                int col = i % perRow;
                int row = i / perRow;
                int iconX = x + col * (size + gap);
                int iconY = y + row * (size + gap);
                drawTraitIcon(graphics, owned.get(i), iconX, iconY, size);
                if (contains(iconX, iconY, size, size, mouseX, mouseY)) {
                    drawTraitTooltip(graphics, font, owned.get(i), mouseX, mouseY);
                }
            }
            int rows = (owned.size() + perRow - 1) / perRow;
            y += rows * (size + gap) + 1;
        }

        graphics.fill(x, y, contentRight, y + 1, 0xFF42424D);
        return y + 3;
    }

    private static void drawTraitIcon(GuiGraphics graphics, OccupationTrait.Def trait, int x, int y, int size) {
        graphics.blit(trait.texture(), x, y, size, size, 0F, 0F, 18, 18, 18, 18);
        drawBevel(graphics, x, y, size);
        graphics.renderOutline(x, y, size, size, 0xFF45454F);
    }

    /** 仿原版物品图标的浮雕：左上受光发亮、右下背光发暗，让平面贴图看起来凸起。 */
    private static void drawBevel(GuiGraphics graphics, int x, int y, int size) {
        graphics.fill(x, y, x + size, y + 1, 0x50FFFFFF);                       // 顶：亮
        graphics.fill(x, y, x + 1, y + size, 0x38FFFFFF);                       // 左：次亮
        graphics.fill(x, y + size - 1, x + size, y + size, 0x80000000);        // 底：暗
        graphics.fill(x + size - 1, y, x + size, y + size, 0x60000000);        // 右：次暗
    }

    private static void drawTraitTooltip(GuiGraphics graphics, Font font, OccupationTrait.Def trait,
                                         int mouseX, int mouseY) {
        List<Component> tooltip = new ArrayList<>();
        tooltip.add(Component.translatable(trait.nameKey())
                .withStyle(trait.positive() ? ChatFormatting.GREEN : ChatFormatting.RED));
        tooltip.add(com.pzmoodle.immersion.admin.DefOverrides.traitDescription(trait).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal((trait.cost() > 0 ? "消耗 " : "返还 ") + Math.abs(trait.cost()) + " 点")
                .withStyle(ChatFormatting.DARK_GRAY));
        graphics.renderComponentTooltip(font, tooltip, mouseX, mouseY);
    }

    private static List<OccupationTrait.Def> ownedTraits() {
        List<OccupationTrait.Def> owned = new ArrayList<>();
        for (OccupationTrait.Def def : OccupationTrait.ALL.values()) {
            if (ClientState.TRAITS.contains(def.id())) owned.add(def);
        }
        return owned;
    }

    private static Occupation.Def currentOccupation() {
        return Occupation.ALL.get(ClientState.occupation);
    }

    private static ResourceLocation occupationIcon(String id) {
        if ("unemployed".equals(id)) return null;
        return PZMoodleImmersion.id("textures/gui/occupation/" + id + ".png");
    }

    private static int traitIconSize() {
        return Math.max(9, Math.min(36, Math.round(OCCUPATION_ICON * ClientConfig.TRAIT_ICON_SCALE.get().floatValue())));
    }

    private static PanelBounds panelBounds(InventoryScreen screen, float progress) {
        int finalRight = screen.getGuiLeft() - 6;
        int finalX = Math.max(2, finalRight - PANEL_WIDTH);
        int height = Math.max(120, Math.min(212, Minecraft.getInstance().getWindow().getGuiScaledHeight() - 28));
        int y = Math.max(4, Math.min(screen.getGuiTop() - 12,
                Minecraft.getInstance().getWindow().getGuiScaledHeight() - height - 4));
        float eased = 1F - (1F - progress) * (1F - progress);
        int x = finalX - Math.round((1F - eased) * (PANEL_WIDTH + 12));
        return new PanelBounds(x, y, PANEL_WIDTH, height);
    }

    private static boolean contains(IconBounds bounds, double mouseX, double mouseY) {
        return contains(bounds.x(), bounds.y(), bounds.width(), bounds.height(), mouseX, mouseY);
    }

    private static boolean contains(int x, int y, int width, int height, double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private record IconBounds(int x, int y, int width, int height) {}

    private record PanelBounds(int x, int y, int width, int height) {}

    private InventoryTraitIcons() {}
}
