package com.pzmoodle.immersion.client;

import com.pzmoodle.immersion.moodle.Skill;
import com.pzmoodle.immersion.moodle.StatData;
import com.pzmoodle.immersion.trait.Occupation;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** B42 技能窗口风格的共用技能列表。 */
public final class SkillPanel {
    /** 6 像素等级格配 1 像素间隔，窄面板也能看清，宽面板不会留下大空洞。 */
    public static final int BOX = 6;
    public static final int BOX_GAP = 1;
    public static final int BOXES_W = BOX * Skill.MAX_LEVEL + BOX_GAP * (Skill.MAX_LEVEL - 1);
    public static final int ROW_H = 12;
    public static final int PANEL_GAP = 4;

    private static final int PANEL_BG = 0xB00A0A0E;
    private static final int PANEL_BORDER = 0xFF4A4A55;
    private static final int GROUP_TEXT = 0xFF9A9A9A;
    private static final int NAME_TEXT = 0xFFD8D8D8;
    private static final int FULL = 0xFFB29A62;
    private static final int EMPTY = 0xFF16161C;
    private static final int BORDER = 0xFF5A5A66;

    public interface Values {
        int occupationBase(Skill skill);
        int traitBonus(Skill skill);
        int growthLevel(Skill skill);
        int growthXp(Skill skill);

        default int effective(Skill skill) {
            return Occupation.combine(occupationBase(skill), traitBonus(skill), growthLevel(skill));
        }
    }

    public static int rowCount() {
        int count = 0;
        for (Skill.Group ignored : Skill.Group.values()) {
            count++;
            for (Skill skill : Skill.ALL) if (skill.group == ignored) count++;
        }
        return count;
    }

    public static int contentHeight() {
        return rowCount() * ROW_H;
    }

    public static int clampScroll(int scroll, int viewHeight) {
        return Math.max(0, Math.min(Math.max(0, contentHeight() - viewHeight), scroll));
    }

    /**
     * 绘制一个可滚动技能列表。返回鼠标当前悬停的技能，没有则返回 null。
     */
    public static Skill drawList(GuiGraphics graphics, Font font, int x, int top, int bottom, int width,
                                 int scroll, Values values, int mouseX, int mouseY) {
        graphics.fill(x, top, x + width, bottom, PANEL_BG);
        graphics.renderOutline(x, top, width, bottom - top, PANEL_BORDER);

        int innerTop = top + 2;
        int innerBottom = bottom - 2;
        int boxX = x + width - 4 - BOXES_W;
        Skill hovered = null;

        graphics.enableScissor(x + 2, innerTop, x + width - 3, innerBottom);
        int row = 0;
        for (Skill.Group group : Skill.Group.values()) {
            int y = innerTop + row * ROW_H - scroll;
            drawGroup(graphics, font, group, x, boxX, width, y);
            row++;
            for (Skill skill : Skill.ALL) {
                if (skill.group != group) continue;
                y = innerTop + row * ROW_H - scroll;
                if (y + ROW_H > innerTop && y < innerBottom) {
                    drawSkill(graphics, font, skill, values, boxX, y, x, width);
                    if (mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY < y + ROW_H) {
                        hovered = skill;
                    }
                }
                row++;
            }
        }
        graphics.disableScissor();

        if (contentHeight() > innerBottom - innerTop) {
            int sx = x + width - 2;
            int sh = Math.max(12, (int) ((long) (innerBottom - innerTop) * (innerBottom - innerTop) / contentHeight()));
            int sy = innerTop + (int) ((long) (innerBottom - innerTop - sh) * scroll
                    / Math.max(1, contentHeight() - (innerBottom - innerTop)));
            graphics.fill(sx, innerTop, sx + 2, innerBottom, 0xFF17171D);
            graphics.fill(sx, sy, sx + 2, sy + sh, 0xFF777783);
        }

        if (hovered != null && mouseX >= x && mouseX <= x + width && mouseY >= innerTop && mouseY < innerBottom) {
            List<Component> tip = new ArrayList<>();
            int level = values.effective(hovered);
            tip.add(Component.translatable(hovered.nameKey())
                    .append(Component.literal("  " + level + "/" + Skill.MAX_LEVEL))
                    .withStyle(ChatFormatting.GOLD));
            tip.add(Component.translatable("tooltip.pz_moodle_immersion.skill_breakdown",
                    signed(values.occupationBase(hovered)), signed(values.traitBonus(hovered)),
                    Component.literal("+" + values.growthLevel(hovered)).withStyle(ChatFormatting.WHITE))
                    .withStyle(ChatFormatting.GRAY));
            int growth = values.growthLevel(hovered);
            if (growth >= Skill.MAX_LEVEL) {
                tip.add(Component.translatable("tooltip.pz_moodle_immersion.stat_xp_max").withStyle(ChatFormatting.GREEN));
            } else {
                int into = StatData.xpIntoLevel(hovered, growth, values.growthXp(hovered));
                int range = StatData.xpRangeForLevel(hovered, growth);
                tip.add(Component.translatable("tooltip.pz_moodle_immersion.stat_xp", into, range)
                        .withStyle(ChatFormatting.DARK_GRAY));
            }
            tip.add(Component.translatable(hovered.descKey()).withStyle(ChatFormatting.GRAY));
            graphics.renderComponentTooltip(font, tip, mouseX, mouseY);
        }
        return hovered;
    }

    private static void drawGroup(GuiGraphics graphics, Font font, Skill.Group group,
                                  int x, int boxX, int width, int y) {
        Component text = Component.translatable(group.key);
        int right = boxX - PANEL_GAP;
        int tx = Math.max(x + 3, right - font.width(text));
        graphics.drawString(font, text, tx, y + 1, GROUP_TEXT, true);
    }

    private static void drawSkill(GuiGraphics graphics, Font font, Skill skill, Values values,
                                  int boxX, int y, int panelX, int width) {
        Component full = Component.translatable(skill.nameKey());
        int maxNameWidth = Math.max(8, boxX - panelX - 7);
        Component label = full;
        if (font.width(label) > maxNameWidth) {
            Component shortLabel = Component.translatable(skill.shortKey());
            if (!shortLabel.getString().equals(skill.shortKey()) && font.width(shortLabel) <= maxNameWidth) {
                label = shortLabel;
            }
        }
        int nameX = Math.max(panelX + 3, boxX - 3 - font.width(label));
        graphics.drawString(font, label, nameX, y + 1, NAME_TEXT, true);

        int level = values.effective(skill);
        int growth = values.growthLevel(skill);
        int into = StatData.xpIntoLevel(skill, growth, values.growthXp(skill));
        int range = StatData.xpRangeForLevel(skill, growth);
        for (int i = 0; i < Skill.MAX_LEVEL; i++) {
            int bx = boxX + i * (BOX + BOX_GAP);
            if (i < level) {
                graphics.fill(bx, y + 2, bx + BOX, y + 2 + BOX, FULL);
            } else {
                graphics.fill(bx, y + 2, bx + BOX, y + 2 + BOX, EMPTY);
                if (i == level && level < Skill.MAX_LEVEL && into > 0) {
                    int h = Math.max(1, Math.round(into / (float) range * BOX));
                    graphics.fill(bx, y + 2 + BOX - h, bx + BOX, y + 2 + BOX, FULL);
                }
            }
            graphics.renderOutline(bx, y + 2, BOX, BOX, BORDER);
        }
    }

    private static Component signed(int value) {
        ChatFormatting color = value > 0 ? ChatFormatting.GREEN : value < 0 ? ChatFormatting.RED : ChatFormatting.GRAY;
        return Component.literal((value > 0 ? "+" : "") + value).withStyle(color);
    }

    private SkillPanel() {}
}
