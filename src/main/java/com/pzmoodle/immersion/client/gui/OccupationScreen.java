package com.pzmoodle.immersion.client.gui;

import com.pzmoodle.immersion.PZMoodleImmersion;
import com.pzmoodle.immersion.client.ClientState;
import com.pzmoodle.immersion.client.SkillPanel;
import com.pzmoodle.immersion.moodle.Skill;
import com.pzmoodle.immersion.net.Packets;
import com.pzmoodle.immersion.trait.Occupation;
import com.pzmoodle.immersion.trait.OccupationTrait;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 僵毁角色创建式职业/特质选择界面，布局对照 Project Zomboid 原版：
 * 左栏职业列表（图标+名称）、中栏可用特质（正面绿色在上、负面红色在下，右侧点数）、
 * 右栏上半已选特质 + 下半主要数值（力量/体格/速度分段条），底部右侧「可用点数」。
 * 单击高亮一行，「添加特质 >」/「< 移除特质」按钮或双击在两个列表间移动特质。
 * <p>
 * 界面上的点击只改「待确认」的本地草稿，真正生效必须点底部的「确认选择」，
 * 并且由服务端整体校验。确认后锁定，死亡重生才解锁（由服务端 canRechoose 控制）。
 */
public class OccupationScreen extends Screen {
    private static final int OCC_ROW_H = 22;
    private static final int TRAIT_ROW_H = 20;
    private static final int ICON = 18;
    private static final int BTN_H = 14;

    // 配色：贴近僵毁深色面板
    private static final int PANEL_BG = 0xF00D0D12;
    private static final int PANEL_BORDER = 0xFF4A4A55;
    private static final int SUB_TEXT = 0xFF9A9A9A;
    private static final int GREEN_TXT = 0xFF5EC842;
    private static final int RED_TXT = 0xFFD04545;
    private static final int BROWN_SEL = 0xFF6E3A1C;

    private final List<Occupation.Def> occupations = new ArrayList<>();
    private final List<OccupationTrait.Def> positiveTraits = new ArrayList<>();
    private final List<OccupationTrait.Def> negativeTraits = new ArrayList<>();

    // —— 待确认的草稿 ——
    private String pendingOccupation = "";
    private final Set<String> pendingTraits = new LinkedHashSet<>();
    /** 中/右栏里被单击高亮、等待按钮确认移动的一行。 */
    private String hlAvailable = null;
    private String hlChosen = null;
    private long lastClickAt;
    private String lastClickId = "";

    // —— 各栏独立滚动量 ——
    private int occScroll = 0;
    private int availScroll = 0;
    private int chosenScroll = 0;
    private int skillScroll = 0;

    // —— 布局缓存（layout() 计算） ——
    private int occX, occW, availX, availW, rightX, rightW;
    private int occTop, occBottom, availTop, availBottom, chosenTop, chosenBottom;
    private Rect addBtn, removeBtn, confirmBtn, closeBtn;
    private Rect majorHeader;

    private record Rect(int x, int y, int w, int h) {
        boolean contains(double mx, double my) {
            return mx >= x && mx <= x + w && my >= y && my <= y + h;
        }
    }

    public OccupationScreen() {
        super(Component.translatable("screen.pz_moodle_immersion.occupation"));
        for (Occupation.Def def : Occupation.ALL.values()) {
            // 隐藏职业只有管理员解锁后才出现在列表里
            if (!Occupation.isHidden(def.id()) || ClientState.isHiddenUnlocked(def.id())) occupations.add(def);
        }
        for (OccupationTrait.Def def : OccupationTrait.ALL.values()) {
            if (!OccupationTrait.selectable(def.id())) continue;
            (def.positive() ? positiveTraits : negativeTraits).add(def);
        }
    }

    @Override
    protected void init() {
        adoptFromState();
        layout();
    }

    private void layout() {
        int margin = 6;
        int gap = 6;
        int total = width - margin * 2;
        availW = rightW = Math.max(118, Math.min(172, (total - 150 - gap * 2) / 2));
        occW = Math.max(90, total - availW - rightW - gap * 2);
        occX = margin;
        availX = occX + occW + gap;
        rightX = availX + availW + gap;

        occTop = 30;
        occBottom = height - 30;
        availTop = occTop + 12;
        availBottom = occBottom - (BTN_H + 2);
        chosenTop = availTop;
        chosenBottom = chosenTop + (int) ((occBottom - chosenTop) * 0.55f);

        addBtn = new Rect(availX + availW - 66, availBottom + 2, 66, BTN_H);
        removeBtn = new Rect(rightX + rightW - 66, chosenBottom + 2, 66, BTN_H);
        majorHeader = new Rect(rightX, removeBtn.y + BTN_H + 4, rightW, 10);
        confirmBtn = new Rect(width / 2 - 96, height - 22, 92, 16);
        closeBtn = new Rect(width / 2 + 2, height - 22, 64, 16);
    }

    private void adoptFromState() {
        pendingOccupation = ClientState.occupation;
        pendingTraits.clear();
        for (String t : ClientState.TRAITS) {
            if (OccupationTrait.selectable(t)) pendingTraits.add(t);
        }
        hlAvailable = null;
        hlChosen = null;
    }

    /** 是否允许改动：只有在服务端解除锁定时才行（确认后锁定，死亡重生后才解锁）。 */
    private boolean editable() {
        return ClientState.canRechoose;
    }

    private Set<String> pendingEffectiveTraits() {
        Set<String> out = new LinkedHashSet<>(pendingTraits);
        out.addAll(OccupationTrait.occupationTraits(pendingOccupation));
        return out;
    }

    private int pendingPoints() {
        return ClientState.pendingPoints(pendingOccupation, pendingTraits);
    }

    private void confirm() {
        if (!editable()) return;
        PacketDistributor.sendToServer(new Packets.ConfirmProfile(pendingOccupation, String.join(",", pendingTraits)));
        adoptFromState();
    }

    private boolean dirty() {
        Set<String> selected = new LinkedHashSet<>();
        for (String id : ClientState.TRAITS) if (OccupationTrait.selectable(id)) selected.add(id);
        return !pendingOccupation.equals(ClientState.occupation) || !pendingTraits.equals(selected);
    }

    /** 中栏内容：还没被选进右栏的正面特质在上、负面在下（和僵毁一致）。 */
    private List<OccupationTrait.Def> availableList() {
        List<OccupationTrait.Def> out = new ArrayList<>();
        for (OccupationTrait.Def d : positiveTraits) {
            if (!pendingTraits.contains(d.id()) && !OccupationTrait.conflictsWithAny(pendingTraits, d.id())) out.add(d);
        }
        for (OccupationTrait.Def d : negativeTraits) {
            if (!pendingTraits.contains(d.id()) && !OccupationTrait.conflictsWithAny(pendingTraits, d.id())) out.add(d);
        }
        return out;
    }

    private List<OccupationTrait.Def> chosenList() {
        List<OccupationTrait.Def> out = new ArrayList<>();
        for (OccupationTrait.Def d : positiveTraits) if (pendingTraits.contains(d.id())) out.add(d);
        for (OccupationTrait.Def d : negativeTraits) if (pendingTraits.contains(d.id())) out.add(d);
        for (String id : OccupationTrait.occupationTraits(pendingOccupation)) {
            OccupationTrait.Def def = OccupationTrait.ALL.get(id);
            if (def != null) out.add(def);
        }
        return out;
    }

    // ================= 渲染 =================

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 不用 renderBackground：它会触发全屏模糊后处理并重置纹理采样状态，导致界面发糊。
        Backdrop.render(graphics, width, height);

        graphics.drawCenteredString(font, Component.translatable("screen.pz_moodle_immersion.select_title")
                .withStyle(ChatFormatting.WHITE), width / 2, 6, 0xFFFFFF);
        graphics.flush();

        // 三栏标题
        drawText(graphics, Component.translatable("screen.pz_moodle_immersion.professions"), occX, occTop - 10, 0xFFFFFFFF);
        drawText(graphics, Component.translatable("screen.pz_moodle_immersion.available_traits"), availX, occTop - 10, 0xFFFFFFFF);
        drawText(graphics, Component.translatable("screen.pz_moodle_immersion.chosen_traits"), rightX, occTop - 10, 0xFFFFFFFF);

        // 面板底
        graphics.fill(occX, occTop, occX + occW, occBottom, PANEL_BG);
        graphics.renderOutline(occX, occTop, occW, occBottom - occTop, PANEL_BORDER);
        graphics.fill(availX, occTop, availX + availW, availBottom + BTN_H + 2, PANEL_BG);
        graphics.renderOutline(availX, occTop, availW, availBottom + BTN_H + 2 - occTop, PANEL_BORDER);
        graphics.fill(rightX, occTop, rightX + rightW, chosenBottom + BTN_H + 2, PANEL_BG);
        graphics.renderOutline(rightX, occTop, rightW, chosenBottom + BTN_H + 2 - occTop, PANEL_BORDER);

        // 特质两栏的 名称/点数 子表头
        drawListSubHeader(graphics, availX, availW, occTop + 1);
        drawListSubHeader(graphics, rightX, rightW, occTop + 1);
        graphics.flush();

        List<OccupationTrait.Def> avail = availableList();
        List<OccupationTrait.Def> chosen = chosenList();
        clampScrolls(avail.size(), chosen.size());

        renderOccupations(graphics, mouseX, mouseY);
        renderTraitRows(graphics, avail, availX, availTop, availBottom, availScroll, availW, hlAvailable, mouseX, mouseY);
        renderTraitRows(graphics, chosen, rightX, chosenTop, chosenBottom, chosenScroll, rightW, hlChosen, mouseX, mouseY);
        graphics.flush();

        drawScrollbar(graphics, occX + occW - 2, occTop + 1, occBottom, occScroll,
                occupations.size() * OCC_ROW_H, occBottom - occTop);
        drawScrollbar(graphics, availX + availW - 2, availTop + 1, availBottom, availScroll,
                avail.size() * TRAIT_ROW_H, availBottom - availTop);
        drawScrollbar(graphics, rightX + rightW - 2, chosenTop + 1, chosenBottom, chosenScroll,
                chosen.size() * TRAIT_ROW_H, chosenBottom - chosenTop);

        drawPzButton(graphics, addBtn, Component.translatable("screen.pz_moodle_immersion.add_trait"),
                editable() && hlAvailable != null);
        drawPzButton(graphics, removeBtn, Component.translatable("screen.pz_moodle_immersion.remove_trait"),
                editable() && hlChosen != null);
        graphics.flush();

        renderMajorSkills(graphics, mouseX, mouseY);
        renderBottomBar(graphics);
        renderTooltip(graphics, avail, chosen, mouseX, mouseY);
        graphics.flush();
    }

    private void clampScrolls(int availCount, int chosenCount) {
        occScroll = clampScroll(occScroll, occupations.size() * OCC_ROW_H, occBottom - occTop);
        availScroll = clampScroll(availScroll, availCount * TRAIT_ROW_H, availBottom - availTop);
        chosenScroll = clampScroll(chosenScroll, chosenCount * TRAIT_ROW_H, chosenBottom - chosenTop);
    }

    private int clampScroll(int v, int content, int view) {
        return Math.max(0, Math.min(Math.max(0, content - view + 4), v));
    }

    private void drawListSubHeader(GuiGraphics graphics, int x, int w, int y) {
        drawText(graphics, Component.translatable("screen.pz_moodle_immersion.name_col"), x + 3, y, SUB_TEXT);
        drawText(graphics, Component.translatable("screen.pz_moodle_immersion.cost_col"), x + w - 4 - font.width(Component.translatable("screen.pz_moodle_immersion.cost_col")), y, SUB_TEXT);
        graphics.fill(x + 1, y + 10, x + w - 3, y + 11, PANEL_BORDER);
    }

    private void renderOccupations(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.enableScissor(occX + 1, occTop + 1, occX + occW - 3, occBottom);
        int y = occTop + 1 - occScroll;
        for (Occupation.Def def : occupations) {
            if (y + OCC_ROW_H > occTop && y < occBottom) {
                boolean selected = def.id().equals(pendingOccupation);
                if (selected) graphics.fill(occX + 1, y, occX + occW - 3, y + OCC_ROW_H, BROWN_SEL);
                else if (hoverRow(mouseX, mouseY, occX, y, occW, OCC_ROW_H))
                    graphics.fill(occX + 1, y, occX + occW - 3, y + OCC_ROW_H, 0x40FFFFFF);
                ResourceLocation icon = occupationIcon(def.id());
                if (icon != null) {
                    graphics.blit(icon, occX + 3, y + 2, ICON, ICON, 0f, 0f, 18, 18, 18, 18);
                }
                drawText(graphics, Component.translatable(def.nameKey()),
                        icon != null ? occX + ICON + 7 : occX + 4, y + (OCC_ROW_H - font.lineHeight) / 2,
                        selected ? 0xFFFFFFFF : 0xFFD8D8D8);
                String pts = (def.traitPoints() >= 0 ? "+" : "") + def.traitPoints();
                drawText(graphics, Component.literal(pts), occX + occW - 8 - font.width(pts), y + (OCC_ROW_H - font.lineHeight) / 2,
                        def.traitPoints() >= 0 ? GREEN_TXT : RED_TXT);
            }
            y += OCC_ROW_H;
        }
        graphics.disableScissor();
    }

    private void renderTraitRows(GuiGraphics graphics, List<OccupationTrait.Def> list, int x, int top, int bottom,
                                 int scroll, int w, String highlight, int mouseX, int mouseY) {
        graphics.enableScissor(x + 1, top, x + w - 3, bottom);
        int y = top - scroll;
        for (OccupationTrait.Def def : list) {
            if (y + TRAIT_ROW_H > top && y < bottom) {
                boolean hl = def.id().equals(highlight);
                if (hl) graphics.fill(x + 1, y, x + w - 3, y + TRAIT_ROW_H, BROWN_SEL);
                else if (hoverRow(mouseX, mouseY, x, y, w, TRAIT_ROW_H))
                    graphics.fill(x + 1, y, x + w - 3, y + TRAIT_ROW_H, 0x30FFFFFF);
                graphics.blit(def.texture(), x + 3, y + 1, ICON, ICON, 0f, 0f, 18, 18, 18, 18);
                int color = def.positive() ? GREEN_TXT : RED_TXT;
                drawText(graphics, Component.translatable(def.nameKey()), x + ICON + 6, y + (TRAIT_ROW_H - font.lineHeight) / 2, color);
                String cost = (def.cost() > 0 ? "-" : def.cost() < 0 ? "+" : "") + Math.abs(def.cost());
                drawText(graphics, Component.literal(cost), x + w - 8 - font.width(cost), y + (TRAIT_ROW_H - font.lineHeight) / 2, color);
            }
            y += TRAIT_ROW_H;
        }
        graphics.disableScissor();
    }

    /** 右下「主要数值」：按草稿（职业+特质+成长）实时显示完整 B42 技能表。 */
    private void renderMajorSkills(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = rightX, w = rightW;
        int top = majorHeader.y + majorHeader.h + 2;
        int panelBottom = occBottom;
        drawText(graphics, Component.translatable("screen.pz_moodle_immersion.major_skills"), x + 3, majorHeader.y, 0xFFFFFFFF);

        int str = skillValue(Skill.STRENGTH);
        int fit = skillValue(Skill.FITNESS);
        int spr = skillValue(Skill.SPRINTING);
        graphics.fill(x, top - 2, x + w, panelBottom, PANEL_BG);
        graphics.renderOutline(x, top - 2, w, panelBottom - top + 2, PANEL_BORDER);
        drawText(graphics, Component.translatable("screen.pz_moodle_immersion.character_level_short", str + fit + spr),
                x + 4, top + 2, 0xFFFFD35A);

        int listTop = top + 15;
        skillScroll = SkillPanel.clampScroll(skillScroll, panelBottom - listTop);
        SkillPanel.drawList(graphics, font, x + 1, listTop, panelBottom - 1, w - 2, skillScroll,
                new SkillPanel.Values() {
                    public int occupationBase(Skill skill) { return Occupation.stat(pendingOccupation, skill); }
                    public int traitBonus(Skill skill) { return OccupationTrait.traitBonus(pendingEffectiveTraits(), skill); }
                    public int growthLevel(Skill skill) { return ClientState.growthLevel(skill); }
                    public int growthXp(Skill skill) { return ClientState.growthXp(skill); }
                }, mouseX, mouseY);
    }

    private int skillValue(Skill skill) {
        return Occupation.combine(Occupation.stat(pendingOccupation, skill),
                OccupationTrait.traitBonus(pendingEffectiveTraits(), skill), ClientState.growthLevel(skill));
    }

    private void renderBottomBar(GuiGraphics graphics) {
        int pts = pendingPoints();
        // 提示（左）
        Component hint;
        if (!editable()) hint = Component.translatable("screen.pz_moodle_immersion.locked").withStyle(ChatFormatting.RED);
        else if (dirty()) hint = Component.translatable("screen.pz_moodle_immersion.unsaved").withStyle(ChatFormatting.YELLOW);
        else hint = Component.translatable("screen.pz_moodle_immersion.confirmed").withStyle(ChatFormatting.GREEN);
        drawText(graphics, hint, 8, height - 14, 0xFFFFFF);

        // 确认 / 关闭
        drawPzButton(graphics, confirmBtn, Component.translatable("screen.pz_moodle_immersion.confirm"),
                editable() && dirty() && pts >= 0);
        drawPzButton(graphics, closeBtn, Component.translatable("gui.done"), true);

        // 可用点数（右下，数字绿色，和僵毁一致）
        String label = Component.translatable("screen.pz_moodle_immersion.points_to_spend").getString();
        int lx = width - 8 - font.width(label) - 8;
        drawText(graphics, Component.literal(label), lx, height - 14, 0xFFFFFFFF);
        drawText(graphics, Component.literal(Integer.toString(pts)), lx + font.width(label) + 4, height - 14,
                pts < 0 ? RED_TXT : GREEN_TXT);
    }

    private void drawPzButton(GuiGraphics graphics, Rect r, Component text, boolean active) {
        graphics.fill(r.x, r.y, r.x + r.w, r.y + r.h, active ? 0xFF2A2A34 : 0xFF1B1B22);
        graphics.renderOutline(r.x, r.y, r.w, r.h, active ? 0xFF7A7A8A : 0xFF3A3A44);
        int color = active ? 0xFFE8E8E8 : 0xFF707078;
        drawText(graphics, text, r.x + (r.w - font.width(text)) / 2, r.y + (r.h - font.lineHeight) / 2, color);
    }

    private void drawScrollbar(GuiGraphics graphics, int x, int top, int bottom, int scroll, int content, int view) {
        if (content <= view) return;
        graphics.fill(x, top, x + 2, bottom, 0xFF1B1B22);
        int thumbH = Math.max(10, (int) ((long) view * view / content));
        int thumbY = top + (int) ((long) (bottom - top - thumbH) * scroll / Math.max(1, content - view));
        graphics.fill(x, thumbY, x + 2, thumbY + thumbH, 0xFF6A6A7A);
    }

    // ================= 悬停提示 =================

    private void renderTooltip(GuiGraphics graphics, List<OccupationTrait.Def> avail,
                               List<OccupationTrait.Def> chosen, int mouseX, int mouseY) {
        if (mouseX >= occX && mouseX <= occX + occW && mouseY >= occTop && mouseY <= occBottom) {
            int y = occTop + 1 - occScroll;
            for (Occupation.Def def : occupations) {
                if (mouseY >= y && mouseY <= y + OCC_ROW_H) {
                    graphics.renderComponentTooltip(font, occupationTooltip(def), mouseX, mouseY);
                    return;
                }
                y += OCC_ROW_H;
            }
        }
        if (mouseX >= availX && mouseX <= availX + availW && mouseY >= availTop && mouseY <= availBottom) {
            int y = availTop - availScroll;
            for (OccupationTrait.Def def : avail) {
                if (mouseY >= y && mouseY <= y + TRAIT_ROW_H) {
                    graphics.renderComponentTooltip(font, traitTooltip(def), mouseX, mouseY);
                    return;
                }
                y += TRAIT_ROW_H;
            }
        }
        if (mouseX >= rightX && mouseX <= rightX + rightW && mouseY >= chosenTop && mouseY <= chosenBottom) {
            int y = chosenTop - chosenScroll;
            for (OccupationTrait.Def def : chosen) {
                if (mouseY >= y && mouseY <= y + TRAIT_ROW_H) {
                    graphics.renderComponentTooltip(font, traitTooltip(def), mouseX, mouseY);
                    return;
                }
                y += TRAIT_ROW_H;
            }
        }
    }

    /**
     * 职业悬停说明：只列等级 0 以上的技能（六项体能 + 主修技能），
     * 0 级技能一律不显示；每行排若干组「名称 等级」，避免满级职业撑爆提示框。
     */
    private List<Component> occupationTooltip(Occupation.Def def) {
        Occupation.Stats s = def.stats();
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable(def.nameKey()).withStyle(ChatFormatting.WHITE));

        List<Component> entries = new ArrayList<>();
        addSkillEntry(entries, "stat.pz_moodle_immersion.strength", s.strength());
        addSkillEntry(entries, "stat.pz_moodle_immersion.fitness", s.fitness());
        addSkillEntry(entries, "stat.pz_moodle_immersion.sprinting", s.sprinting());
        addSkillEntry(entries, "stat.pz_moodle_immersion.lightfooted", s.lightfooted());
        addSkillEntry(entries, "stat.pz_moodle_immersion.nimble", s.nimble());
        addSkillEntry(entries, "stat.pz_moodle_immersion.aiming", s.aiming());
        for (Skill skill : Skill.ALL) {
            if (Occupation.isStatTrack(skill)) continue;
            addSkillEntry(entries, skill.nameKey(), def.majors().getOrDefault(skill, 0));
        }

        // 按提示框宽度换行：一行尽量多塞几组，超出宽度才另起一行。
        int maxWidth = 150;
        MutableComponent row = Component.empty();
        int rowWidth = 0;
        for (Component entry : entries) {
            int w = font.width(entry);
            if (rowWidth > 0 && rowWidth + 6 + w > maxWidth) {
                lines.add(row);
                row = Component.empty();
                rowWidth = 0;
            }
            if (rowWidth > 0) {
                row.append(Component.literal("  "));
                rowWidth += font.width("  ");
            }
            row.append(entry);
            rowWidth += w;
        }
        if (rowWidth > 0) lines.add(row);

        lines.add(com.pzmoodle.immersion.admin.DefOverrides.occupationDescription(def).withStyle(ChatFormatting.GRAY));
        return lines;
    }

    /** 等级 0 的技能直接跳过；0 级以上渲染成「名称 等级」（等级颜色沿用高绿低红）。 */
    private void addSkillEntry(List<Component> out, String key, int value) {
        if (value <= 0) return;
        out.add(Component.translatable(key).append(Component.literal(" " + value).withStyle(statColor(value))));
    }

    private List<Component> traitTooltip(OccupationTrait.Def def) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable(def.nameKey())
                .withStyle(def.positive() ? ChatFormatting.GREEN : ChatFormatting.RED));
        lines.add(com.pzmoodle.immersion.admin.DefOverrides.traitDescription(def).withStyle(ChatFormatting.GRAY));
        Component statBonus = statBonusLine(def.id());
        if (statBonus != null) {
            lines.add(Component.translatable("screen.pz_moodle_immersion.stat_bonus", statBonus)
                    .withStyle(ChatFormatting.YELLOW));
        }
        lines.add(Component.literal((def.cost() > 0 ? "消耗 " : "返还 ") + Math.abs(def.cost()) + " 点")
                .withStyle(ChatFormatting.DARK_GRAY));
        return lines;
    }

    /** 某个特质自带的人物数值加成，例如「力量 +2  体格 +2」；没有加成返回 null。 */
    private Component statBonusLine(String traitId) {
        Set<String> only = Set.of(traitId);
        MutableComponent c = Component.empty();
        boolean first = true;
        for (Skill skill : Skill.ALL) {
            first = appendBonus(c, skill.nameKey(), OccupationTrait.traitBonus(only, skill), first);
        }
        return c.getString().isEmpty() ? null : c;
    }

    private boolean appendBonus(MutableComponent c, String key, int value, boolean first) {
        if (value == 0) return first;
        if (!first) c.append(Component.literal("  "));
        c.append(Component.translatable(key));
        c.append(Component.literal((value > 0 ? " +" : " ") + value)
                .withStyle(value > 0 ? ChatFormatting.GREEN : ChatFormatting.RED));
        return false;
    }

    private static ChatFormatting statColor(int value) {
        int base = Occupation.BASE_STAT;
        if (value > base) return ChatFormatting.GREEN;
        if (value < base) return ChatFormatting.RED;
        return ChatFormatting.GRAY;
    }

    // ================= 交互 =================

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (confirmBtn.contains(mouseX, mouseY)) {
                if (editable() && dirty() && pendingPoints() >= 0) confirm();
                return true;
            }
            if (closeBtn.contains(mouseX, mouseY)) {
                onClose();
                return true;
            }
            if (!editable()) return super.mouseClicked(mouseX, mouseY, button);
            if (addBtn.contains(mouseX, mouseY)) {
                if (hlAvailable != null) addTrait(hlAvailable);
                return true;
            }
            if (removeBtn.contains(mouseX, mouseY)) {
                if (hlChosen != null && OccupationTrait.selectable(hlChosen)) pendingTraits.remove(hlChosen);
                hlChosen = null;
                return true;
            }
            if (mouseX >= occX && mouseX <= occX + occW && mouseY >= occTop && mouseY <= occBottom) {
                int y = occTop + 1 - occScroll;
                for (Occupation.Def def : occupations) {
                    if (mouseY >= y && mouseY <= y + OCC_ROW_H) {
                        pendingOccupation = def.id();
                        return true;
                    }
                    y += OCC_ROW_H;
                }
            }
            if (mouseX >= availX && mouseX <= availX + availW && mouseY >= availTop && mouseY <= availBottom) {
                List<OccupationTrait.Def> avail = availableList();
                int y = availTop - availScroll;
                for (OccupationTrait.Def def : avail) {
                    if (mouseY >= y && mouseY <= y + TRAIT_ROW_H) {
                        // 双击直接添加，单击先高亮（和僵毁一致）
                        if (isDoubleClick(def.id())) addTrait(def.id());
                        else {
                            hlAvailable = def.id();
                            hlChosen = null;
                        }
                        return true;
                    }
                    y += TRAIT_ROW_H;
                }
            }
            if (mouseX >= rightX && mouseX <= rightX + rightW && mouseY >= chosenTop && mouseY <= chosenBottom) {
                List<OccupationTrait.Def> chosen = chosenList();
                int y = chosenTop - chosenScroll;
                for (OccupationTrait.Def def : chosen) {
                    if (mouseY >= y && mouseY <= y + TRAIT_ROW_H) {
                        if (isDoubleClick(def.id())) {
                            pendingTraits.remove(def.id());
                            hlChosen = null;
                        } else {
                            hlChosen = def.id();
                            hlAvailable = null;
                        }
                        return true;
                    }
                    y += TRAIT_ROW_H;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean isDoubleClick(String id) {
        long now = System.currentTimeMillis();
        boolean dbl = id.equals(lastClickId) && now - lastClickAt < 350;
        lastClickAt = now;
        lastClickId = id;
        return dbl;
    }

    /** 把高亮的特质移进已选列表；剩余点数不够时拒绝（僵毁同样不允许超支）。 */
    private void addTrait(String id) {
        LinkedHashSet<String> trySet = new LinkedHashSet<>(pendingTraits);
        if (OccupationTrait.conflictsWithAny(trySet, id)) return;
        trySet.add(id);
        if (ClientState.pendingPoints(pendingOccupation, trySet) >= 0) {
            pendingTraits.add(id);
        }
        hlAvailable = null;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int step = (int) Math.copySign(24, verticalAmount);
        if (mouseX >= occX && mouseX <= occX + occW && mouseY >= occTop && mouseY <= occBottom) {
            occScroll = clampScroll(occScroll - step, occupations.size() * OCC_ROW_H, occBottom - occTop);
            return true;
        }
        if (mouseX >= availX && mouseX <= availX + availW && mouseY >= availTop && mouseY <= availBottom) {
            availScroll = clampScroll(availScroll - step, availableList().size() * TRAIT_ROW_H, availBottom - availTop);
            return true;
        }
        if (mouseX >= rightX && mouseX <= rightX + rightW && mouseY >= chosenTop && mouseY <= chosenBottom) {
            chosenScroll = clampScroll(chosenScroll - step, chosenList().size() * TRAIT_ROW_H, chosenBottom - chosenTop);
            return true;
        }
        int skillTop = majorHeader.y + majorHeader.h + 2 + 15;
        if (mouseX >= rightX && mouseX <= rightX + rightW
                && mouseY >= skillTop && mouseY <= occBottom) {
            skillScroll = SkillPanel.clampScroll(skillScroll - step, occBottom - skillTop);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private boolean hoverRow(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    /** 统一走 FormattedCharSequence 绘制并显式刷新批次，规避部分渲染模组下延迟批次的缩放问题。 */
    private void drawText(GuiGraphics graphics, Component text, int x, int y, int color) {
        graphics.drawString(font, text.getVisualOrderText(), x, y, color, false);
        graphics.flush();
    }

    /** 无业在僵毁的列表里本来就没有图标，返回 null 跳过绘制。 */
    private static ResourceLocation occupationIcon(String id) {
        if ("unemployed".equals(id)) return null;
        return PZMoodleImmersion.id("textures/gui/occupation/" + id + ".png");
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
