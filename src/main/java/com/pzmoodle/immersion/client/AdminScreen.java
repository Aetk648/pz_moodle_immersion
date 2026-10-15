package com.pzmoodle.immersion.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.pzmoodle.immersion.admin.DefOverrides;
import com.pzmoodle.immersion.client.gui.Backdrop;
import com.pzmoodle.immersion.config.ClientConfig;
import com.pzmoodle.immersion.moodle.Moodle;
import com.pzmoodle.immersion.net.Packets;
import com.pzmoodle.immersion.trait.Occupation;
import com.pzmoodle.immersion.trait.OccupationTrait;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * 管理员职业/特质数据编辑界面。
 * 布局会根据窗口大小自适应：左右两栏是职业/特质列表，中间是编辑卡片，
 * 所有文字都带阴影、所有面板都用不透明深色底，避免压不住游戏画面看不清。
 */
public final class AdminScreen extends Screen {
    private static AdminScreen INSTANCE;

    private static final int TOP = 22;          // 面板顶部
    private static final int GAP = 6;           // 面板之间的间距
    private static final int LIST_HEAD = 16;    // 列表标题栏高度
    private static final int ROW_H = 22;        // 列表每行高度
    private static final int GRID_ROW_H = 26;   // 数值区每行高度

    // 全部改成不透明色：面板下面不再透出被模糊的游戏画面，文字对比度最高。
    private static final int COL_PANEL = 0xFF121218;
    private static final int COL_CARD = 0xFF15151C;
    private static final int COL_BORDER = 0xFF6A6A7A;
    private static final int COL_TITLE = 0xFFFFD35A;
    private static final int COL_TEXT = 0xFFF2F2F6;
    private static final int COL_LABEL = 0xFFD8D8E2;
    private static final int COL_SLOT = 0xFF23232C;
    private static final int COL_SLOT_HOVER = 0xFF3A3A46;
    private static final int COL_ACTIVE = 0xFF7A4020;
    private static final int COL_ACTIVE_EDGE = 0xFFFFD35A;

    private String kind = "occupation";
    private String selected = "unemployed";
    private int selectedSlot = -1;
    private int occScroll;
    private int traitScroll;
    private boolean pickerOpen;
    /** 鼠标正悬停在「状态值」区的第几个图标上（-1 表示没有）。 */
    private int hoverStatus = -1;
    private final List<EditBox> fields = new ArrayList<>();
    private EditBox description;
    private EditBox mechanics;
    private final ItemStack[] grants = new ItemStack[6];
    private int mechY;
    private int hintY;
    private int hintLineCount = 1;
    private int contentTop;
    private int viewportBottom;
    private int contentScroll;
    private int contentHeight;

    // 「机制数值大全」弹层
    private boolean guideOpen;
    private int guideScroll;
    private int guidePanelX;
    private int guidePanelY;
    private int guidePanelW;
    private int guidePanelH;

    /** 一条可填入的机制参数模板：条目（职业或特质）+ 默认参数 + 说明。 */
    private record GuideEntry(String kind, String id, String template, String name, String effect) {}

    // 由 layout() 计算出来的几何数据
    private int panelW;
    private int leftX;
    private int rightX;
    private int editX;
    private int editW;
    private int editBottom;
    private int cols;
    private int rows;
    private int cellW;
    private int gridX;
    private int gridY;
    private int descY;
    private int itemStep;
    private int pickerX;
    private int pickerY;
    private int pickerW;
    private int pickerH;
    private int pickerCell;

    private AdminScreen() { super(Component.translatable("screen.pz_moodle_immersion.admin")); }

    public static void open() {
        Minecraft.getInstance().setScreen(INSTANCE = new AdminScreen());
    }

    @Override protected void init() {
        layout();
        rebuildEditor();
        addRenderableWidget(Button.builder(Component.translatable("screen.pz_moodle_immersion.admin.save"), b -> save())
                .bounds(width / 2 - 55, height - 26, 52, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(width / 2 + 3, height - 26, 52, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.pz_moodle_immersion.admin.guide"), b -> openGuide())
                .bounds(editX + editW - 64, mechY, 60, 16).build());
    }

    /** 按当前窗口大小算出所有面板的位置；编辑卡下半部分全部放进一个可滚动的裁剪区域，任何窗口大小都不会互相遮挡。 */
    private void layout() {
        editBottom = height - 32;
        panelW = Mth.clamp(width / 5, 62, 96);
        leftX = 6;
        rightX = width - 6 - panelW;
        editX = leftX + panelW + GAP;
        editW = Math.max(96, rightX - GAP - editX);

        cols = Mth.clamp(editW / 54, 2, 4);
        rows = (7 + cols - 1) / cols;
        cellW = Math.min(50, editW / cols - 4);
        gridX = editX + 4;
        gridY = TOP + 30;   // 留出数值标签的行高，不再压到标题分隔线

        int gridBottom = gridY + rows * GRID_ROW_H - 4;
        descY = gridBottom + 14;   // 描述输入框（整行）
        mechY = descY + 24;        // 机制参数输入框（整行，右侧留出「数值大全」按钮）
        hintY = mechY + 18;        // 机制框下方的格式提示（自动换行，可能占多行）
        List<String> hintLines = wrapLines(
                Component.translatable("screen.pz_moodle_immersion.admin.mechanics.hint").getString(),
                editW - 12);
        hintLineCount = Math.max(1, hintLines.size());
        contentTop = hintY + hintLineCount * 10 + 4;   // 滚动内容区顶部
        viewportBottom = editBottom - 4;
        contentScroll = 0;

        itemStep = Mth.clamp((editW - 8) / 6, 12, 20);

        pickerCell = Mth.clamp(width / 12, 12, 18);
        pickerW = pickerCell * 9 + 4;
        pickerH = pickerCell * 4 + 20;
        pickerX = (width - pickerW) / 2;
        pickerY = TOP + Math.max(4, (editBottom - TOP - pickerH) / 2);
    }

    private void rebuildEditor() {
        if (editW <= 0) layout();
        loadGrants();
        for (EditBox field : fields) removeWidget(field);
        fields.clear();
        if (description != null) removeWidget(description);
        if (mechanics != null) removeWidget(mechanics);
        for (int i = 0; i < 7; i++) {
            int x = gridX + (i % cols) * (cellW + 4);
            int y = gridY + (i / cols) * GRID_ROW_H;
            EditBox field = new EditBox(font, x, y, cellW, 16,
                    Component.translatable("screen.pz_moodle_immersion.admin.value"));
            field.setValue(Integer.toString(value(i)));
            field.setMaxLength(5);
            field.setTextColor(0xFFFFFFFF);
            fields.add(addRenderableWidget(field));
        }
        description = addRenderableWidget(new EditBox(font, editX + 4, descY, editW - 8, 16,
                Component.translatable("screen.pz_moodle_immersion.admin.description")));
        description.setMaxLength(200);
        description.setValue(descriptionValue());
        description.setTextColor(0xFFFFFFFF);
        mechanics = addRenderableWidget(new EditBox(font, editX + 4, mechY, editW - 70, 16,
                Component.translatable("screen.pz_moodle_immersion.admin.mechanics")));
        mechanics.setMaxLength(240);
        mechanics.setValue(DefOverrides.mechanicsText(kind, selected));
        mechanics.setTextColor(0xFFFFFFFF);
    }

    private int value(int index) {
        if ("occupation".equals(kind)) {
            Occupation.Def d = Occupation.ALL.get(selected);
            int[] values = d == null ? new int[6] : new int[]{d.traitPoints(), d.stats().strength(), d.stats().fitness(), d.stats().sprinting(), d.stats().lightfooted(), d.stats().nimble(), d.stats().aiming()};
            return index < values.length ? values[index] : 0;
        }
        OccupationTrait.Def d = OccupationTrait.ALL.get(selected);
        int[] values = new int[7];
        values[0] = d == null ? 0 : d.cost();
        int[] bonus = OccupationTrait.statBonus(selected);
        System.arraycopy(bonus, 0, values, 1, Math.min(6, bonus.length));
        return values[index];
    }

    private String descriptionValue() {
        if ("occupation".equals(kind)) return DefOverrides.occupation(selected).description;
        return DefOverrides.trait(selected).description;
    }

    private String labelKey(int index) {
        String base = "screen.pz_moodle_immersion.admin.";
        if (index == 0) return base + ("occupation".equals(kind) ? "points" : "cost");
        return base + switch (index) {
            case 1 -> "strength";
            case 2 -> "fitness";
            case 3 -> "sprinting";
            case 4 -> "lightfooted";
            case 5 -> "nimble";
            default -> "aiming";
        };
    }

    private void loadGrants() {
        java.util.Arrays.fill(grants, ItemStack.EMPTY);
        List<DefOverrides.ItemGrant> values = "occupation".equals(kind)
                ? DefOverrides.itemsForOccupation(selected) : DefOverrides.itemsForTrait(selected);
        net.minecraft.core.HolderLookup.Provider registries =
                minecraft.player == null ? null : minecraft.player.registryAccess();
        for (int i = 0; i < Math.min(grants.length, values.size()); i++) {
            ItemStack resolved = values.get(i).resolve(registries);
            if (!resolved.isEmpty()) grants[i] = resolved;
        }
    }

    private void save() {
        int[] values = new int[6];
        int points = parse(fields.get(0).getValue(), value(0));
        for (int i = 0; i < 6; i++) values[i] = parse(fields.get(i + 1).getValue(), value(i + 1));
        List<DefOverrides.ItemGrant> items = new ArrayList<>();
        net.minecraft.core.HolderLookup.Provider registries =
                minecraft.player == null ? null : minecraft.player.registryAccess();
        for (ItemStack stack : grants) if (stack != null && !stack.isEmpty()) {
            items.add(DefOverrides.ItemGrant.of(stack, registries));
        }
        String json = DefOverrides.editJson(kind, selected, points, values, description.getValue(), mechanics.getValue(), items);
        PacketDistributor.sendToServer(new Packets.AdminSave(json));
    }

    private int parse(String value, int fallback) { try { return Integer.parseInt(value); } catch (NumberFormatException e) { return fallback; } }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // 数值大全是严格模态弹层：打开时不绘制底层控件和文字，避免批次渲染顺序导致内容透出。
        if (guideOpen) {
            drawGuide(g, mouseX, mouseY);
            return;
        }
        // 这里不调用 renderBackground()，避免原版菜单背景模糊影响界面。
        // 自绘文字和图标统一在控件之后作为前景层绘制，并立即提交。
        Backdrop.render(g, width, height);
        g.flush();

        g.fill(width / 2 - 88, 3, width / 2 + 88, 17, COL_PANEL);
        g.renderOutline(width / 2 - 88, 3, 176, 14, COL_BORDER);
        g.flush();

        drawListBackground(g, leftX, "occupation");
        drawListBackground(g, rightX, "trait");
        drawEditorBackground(g);
        g.flush();

        // 原版控件在这里绘制输入框和按钮。自绘文字/图标放到它之后，成为最后一层。
        super.render(g, mouseX, mouseY, partialTick);
        g.flush();

        g.drawCenteredString(font, title, width / 2, 6, 0xFFFFFFFF);
        drawListForeground(g, leftX, "occupation", mouseX, mouseY);
        drawListForeground(g, rightX, "trait", mouseX, mouseY);
        drawEditorForeground(g, mouseX, mouseY);
        g.flush();

        if (pickerOpen) {
            drawPicker(g, mouseX, mouseY);
        } else if (slotAt(mouseX, mouseY) >= 0) {
            g.renderTooltip(font, Component.translatable("screen.pz_moodle_immersion.admin.items.hint"), mouseX, mouseY);
        }
        if (guideOpen) drawGuide(g, mouseX, mouseY);
    }

    private void drawListBackground(GuiGraphics g, int x, String panelKind) {
        int w = panelW;
        int h = editBottom - TOP;
        g.fill(x, TOP, x + w, TOP + h, COL_PANEL);
        g.renderOutline(x, TOP, w, h, COL_BORDER);
        g.fill(x + 1, TOP + 12, x + w - 1, TOP + 13, COL_BORDER);
        g.flush();
    }

    private void drawListForeground(GuiGraphics g, int x, String panelKind, int mouseX, int mouseY) {
        int w = panelW;
        int h = editBottom - TOP;
        g.drawCenteredString(font, Component.translatable("screen.pz_moodle_immersion.admin." + panelKind), x + w / 2, TOP + 2, COL_TITLE);

        List<String> ids = new ArrayList<>();
        if ("occupation".equals(panelKind)) ids.addAll(Occupation.ALL.keySet()); else ids.addAll(OccupationTrait.ALL.keySet());
        int scroll = "occupation".equals(panelKind) ? occScroll : traitScroll;
        int listTop = TOP + LIST_HEAD;
        int listBottom = TOP + h - 3;

        g.enableScissor(x + 1, listTop, x + w - 1, listBottom);
        for (int row = 0; row < ids.size(); row++) {
            String id = ids.get(row);
            int yy = listTop + row * ROW_H - scroll;
            if (yy + ROW_H <= listTop || yy >= listBottom) continue;
            boolean active = id.equals(selected) && kind.equals(panelKind);
            boolean hover = mouseX >= x + 1 && mouseX < x + w - 1
                    && mouseY >= Math.max(yy, listTop) && mouseY < Math.min(yy + ROW_H, listBottom);
            if (active) {
                g.fill(x + 2, yy + 1, x + w - 2, yy + ROW_H - 1, COL_ACTIVE);
                g.fill(x + 2, yy + 1, x + 3, yy + ROW_H - 1, COL_ACTIVE_EDGE);
            } else if (hover) {
                g.fill(x + 2, yy + 1, x + w - 2, yy + ROW_H - 1, COL_SLOT_HOVER);
            }
            ResourceLocation icon = "occupation".equals(panelKind) && !"unemployed".equals(id)
                    ? ResourceLocation.fromNamespaceAndPath("pz_moodle_immersion", "textures/gui/occupation/" + id + ".png")
                    : "trait".equals(panelKind) ? OccupationTrait.ALL.get(id).texture() : null;
            g.fill(x + 2, yy + 2, x + 21, yy + 21, 0xFF101018);
            if (icon != null) g.blit(icon, x + 3, yy + 2, 18, 18, 0, 0, 18, 18, 18, 18);
            g.drawString(font, displayName(panelKind, id), x + 24, yy + 7, active ? 0xFFFFE9B0 : COL_TEXT, false);
        }
        g.disableScissor();

        int contentH = ids.size() * ROW_H;
        int viewH = listBottom - listTop;
        if (contentH > viewH) {
            int barH = Math.max(12, viewH * viewH / contentH);
            int barY = listTop + (int) ((viewH - barH) * (scroll / (float) (contentH - viewH)));
            g.fill(x + w - 4, barY, x + w - 2, barY + barH, 0xFF8A8A9A);
        }
        g.flush();
    }

    private String displayName(String panelKind, String id) {
        String key = "occupation".equals(panelKind)
                ? "occupation.pz_moodle_immersion." + id
                : "trait.pz_moodle_immersion." + id;
        return Component.translatable(key).getString();
    }

    private void drawEditorBackground(GuiGraphics g) {
        int h = editBottom - TOP;
        g.fill(editX, TOP, editX + editW, TOP + h, COL_CARD);
        g.renderOutline(editX, TOP, editW, h, COL_BORDER);
        g.fill(editX + 1, TOP + 12, editX + editW - 1, TOP + 13, COL_BORDER);
        g.flush();
    }

    private void drawEditorForeground(GuiGraphics g, int mouseX, int mouseY) {
        g.drawCenteredString(font, Component.translatable("screen.pz_moodle_immersion.admin.editor", displayName(kind, selected)),
                editX + editW / 2, TOP + 2, COL_TITLE);

        for (int i = 0; i < 7; i++) {
            int cx = gridX + (i % cols) * (cellW + 4);
            int cy = gridY + (i / cols) * GRID_ROW_H;
            g.drawCenteredString(font, Component.translatable(labelKey(i)), cx + cellW / 2, cy - 10, COL_LABEL);
        }

        g.drawString(font, Component.translatable("screen.pz_moodle_immersion.admin.description"), editX + 4, descY - 10, COL_LABEL, false);
        g.drawString(font, Component.translatable("screen.pz_moodle_immersion.admin.mechanics"), editX + 4, mechY - 10, COL_LABEL, false);
        // 格式提示自动换行，长文字不再被截断
        int hy = hintY;
        for (String line : wrapLines(Component.translatable("screen.pz_moodle_immersion.admin.mechanics.hint").getString(), editW - 12)) {
            g.drawString(font, line, editX + 4, hy, 0xFF8A8A96, false);
            hy += 10;
        }

        drawContent(g, mouseX, mouseY);
    }

    /** 编辑卡下方的滚动内容：携带物品槽 → 详细机制说明 → 状态值，统一裁剪 + 滚轮，不再互相遮挡。 */
    private void drawContent(GuiGraphics g, int mouseX, int mouseY) {
        int innerX = editX + 4;
        int innerRight = editX + editW - 5;

        int[] stats = new int[6];
        for (int i = 0; i < 6; i++) stats[i] = parse(fields.get(i + 1).getValue(), value(i + 1));
        String text;
        try {
            text = com.pzmoodle.immersion.admin.Mechanics.detailText(kind, selected, stats,
                    mechanics == null ? null : mechanics.getValue());
        } catch (RuntimeException e) {
            // 说明文字生成出错时兜底显示，绝不闪退游戏
            text = "详细说明生成出错，请把当前条目截图反馈给作者。";
        }
        List<String> detailLines = wrapLines(text, innerW());

        int slotsH = 29;   // 物品标签行 + 槽位行
        int statusH = 12 + ((Moodle.ALL.length + 2) / 3) * 11;
        contentHeight = slotsH + detailLines.size() * 10 + 4 + statusH;
        int viewH = Math.max(1, viewportBottom - contentTop);
        contentScroll = Mth.clamp(contentScroll, 0, Math.max(0, contentHeight - viewH));

        g.enableScissor(editX + 1, contentTop, innerRight, viewportBottom);
        int y = contentTop - contentScroll;
        g.drawString(font, Component.translatable("screen.pz_moodle_immersion.admin.items"), innerX, y, COL_LABEL, false);
        y += 11;
        int slotY = y;
        int hovered = slotAt(mouseX, mouseY);
        for (int i = 0; i < grants.length; i++) {
            int sx = innerX + i * itemStep;
            int fill = selectedSlot == i ? COL_ACTIVE : (hovered == i ? COL_SLOT_HOVER : COL_SLOT);
            g.fill(sx, slotY, sx + 18, slotY + 18, fill);
            g.renderOutline(sx, slotY, 18, 18, selectedSlot == i ? COL_ACTIVE_EDGE : 0xFF777783);
            if (grants[i] != null && !grants[i].isEmpty()) {
                g.renderItem(grants[i], sx + 1, slotY + 1);
                g.renderItemDecorations(font, grants[i], sx + 1, slotY + 1);
            }
        }
        y += 22;
        for (String line : detailLines) {
            if (y + 9 >= contentTop && y <= viewportBottom) {
                g.drawString(font, line, innerX, y, COL_LABEL, false);
            }
            y += 10;
        }
        y += 4;
        g.drawString(font, Component.translatable("screen.pz_moodle_immersion.admin.status_values"), innerX, y, COL_TITLE, false);
        y += 12;
        int colWidth = Math.max(62, (editW - 8) / 3);
        hoverStatus = -1;
        for (int i = 0; i < Moodle.ALL.length; i++) {
            int col = i % 3;
            int row = i / 3;
            float v = ClientState.HAS_RAW_VALUES ? ClientState.RAW_VALUES[i] : ClientState.level(Moodle.ALL[i]) * 25f;
            int x = innerX + col * colWidth;
            int ly = y + row * 11;
            if (ly + 9 >= contentTop && ly <= viewportBottom) {
                Moodle m = Moodle.ALL[i];
                // 状态名称换成状态图标（跟随玩家选的图标风格），数值仍然显示在右侧。
                ClientConfig.IconSet set = ClientConfig.MOODLE_ICON_SET.get();
                int texSize = m.textureSize(set);
                g.blit(m.iconFor(set), x, ly, 10, 10, 0f, 0f, texSize, texSize, texSize, texSize);
                g.drawString(font, String.format(java.util.Locale.ROOT, "%.1f/100", v), x + colWidth - 42, ly,
                        v >= 99.9f ? COL_TITLE : COL_LABEL, false);
                if (hoverStatus < 0 && mouseX >= x && mouseX <= x + colWidth - 2
                        && mouseY >= ly - 1 && mouseY < ly + 10) {
                    hoverStatus = i;
                }
            }
        }
        g.disableScissor();

        // 悬停状态图标：显示状态名称与当前数值（裁剪区外绘制，不会被面板切掉）
        if (hoverStatus >= 0) {
            Moodle m = Moodle.ALL[hoverStatus];
            float v = ClientState.HAS_RAW_VALUES ? ClientState.RAW_VALUES[hoverStatus] : ClientState.level(m) * 25f;
            g.renderComponentTooltip(font, List.of(
                    Component.translatable("moodle.pz_moodle_immersion." + m.id + ".name").withStyle(net.minecraft.ChatFormatting.WHITE),
                    Component.literal(String.format(java.util.Locale.ROOT, "%.1f/100", v)).withStyle(net.minecraft.ChatFormatting.GRAY)),
                    mouseX, mouseY);
        }

        if (contentHeight > viewH) {
            int barH = Math.max(12, viewH * viewH / contentHeight);
            int barY = contentTop + (int) ((viewH - barH) * (contentScroll / (float) (contentHeight - viewH)));
            g.fill(editX + editW - 3, barY, editX + editW - 1, barY + barH, 0xFF8A8A9A);
        }
        g.flush();
    }

    private int innerW() { return editW - 12; }

    // ————— 机制数值大全弹层 —————

    /** 收集所有带机制参数的职业与特质，供「数值大全」弹层展示。 */
    private void openGuide() {
        List<GuideEntry> list = new ArrayList<>();
        for (String id : Occupation.ALL.keySet()) {
            String tpl = com.pzmoodle.immersion.admin.Mechanics.defaultParameters("occupation", id);
            if (!tpl.isBlank()) list.add(new GuideEntry("occupation", id, tpl,
                    displayName("occupation", id), com.pzmoodle.immersion.admin.Mechanics.effectSummary("occupation", id)));
        }
        for (String id : OccupationTrait.ALL.keySet()) {
            String tpl = com.pzmoodle.immersion.admin.Mechanics.defaultParameters("trait", id);
            if (!tpl.isBlank()) list.add(new GuideEntry("trait", id, tpl,
                    displayName("trait", id), com.pzmoodle.immersion.admin.Mechanics.effectSummary("trait", id)));
        }
        guideEntries = list;
        guideOpen = true;
        guideScroll = 0;
        setFocused(null);
    }

    private List<GuideEntry> guideEntries = List.of();

    private int guideEntryHeight(GuideEntry e) {
        int textWidth = Math.max(40, guidePanelW - 20);
        int templateLines = Math.max(1, wrapLines(e.template(), textWidth).size());
        int effectLines = Math.max(1, wrapLines(e.effect(), textWidth).size());
        return 10 + templateLines * 10 + effectLines * 10 + 6;
    }

    private int guideContentHeight() {
        int h = 0;
        for (GuideEntry e : guideEntries) h += guideEntryHeight(e);
        return h;
    }

    private void drawGuide(GuiGraphics g, int mouseX, int mouseY) {
        // 先计算几何，再绘制；弹层完全不透明并覆盖底层控件，避免文字“透出来”造成层级冲突。
        guidePanelW = Math.min(270, width - 30);
        guidePanelH = Math.max(80, height - 44);
        guidePanelX = (width - guidePanelW) / 2;
        guidePanelY = 20;
        int headerBottom = guidePanelY + 20;
        int footerTop = guidePanelY + guidePanelH - 17;
        int contentTopGuide = headerBottom + 2;
        int contentBottomGuide = footerTop - 2;

        g.fill(0, 0, width, height, 0xFF000000);
        g.fill(guidePanelX, guidePanelY, guidePanelX + guidePanelW, guidePanelY + guidePanelH, 0xFF101116);
        g.renderOutline(guidePanelX, guidePanelY, guidePanelW, guidePanelH, COL_ACTIVE_EDGE);
        g.fill(guidePanelX + 1, headerBottom - 1, guidePanelX + guidePanelW - 1, headerBottom, COL_BORDER);
        g.fill(guidePanelX + 1, footerTop, guidePanelX + guidePanelW - 1, footerTop + 1, COL_BORDER);
        g.drawCenteredString(font, Component.translatable("screen.pz_moodle_immersion.admin.guide.title"),
                guidePanelX + guidePanelW / 2, guidePanelY + 5, COL_TITLE);
        g.flush();

        int textWidth = Math.max(40, guidePanelW - 20);
        g.enableScissor(guidePanelX + 2, contentTopGuide, guidePanelX + guidePanelW - 2, contentBottomGuide);
        int y = contentTopGuide + 2 - guideScroll;
        for (GuideEntry e : guideEntries) {
            int h = guideEntryHeight(e);
            boolean active = e.kind().equals(kind) && e.id().equals(selected);
            boolean hover = mouseX >= guidePanelX + 2 && mouseX < guidePanelX + guidePanelW - 2
                    && mouseY >= y && mouseY < y + h;
            if (hover && y + h > contentTopGuide && y < contentBottomGuide) {
                g.fill(guidePanelX + 2, y, guidePanelX + guidePanelW - 2, y + h, active ? COL_ACTIVE : 0xFF23232C);
            } else if (active) {
                g.fill(guidePanelX + 2, y, guidePanelX + guidePanelW - 2, y + h, COL_ACTIVE);
            }
            if (y + h >= contentTopGuide && y <= contentBottomGuide) {
                g.drawString(font, e.name(), guidePanelX + 6, y, active ? 0xFFFFE9B0 : COL_TITLE, false);
                int ly = y + 10;
                for (String line : wrapLines(e.template(), textWidth)) {
                    g.drawString(font, line, guidePanelX + 6, ly, 0xFF9FE89F, false);
                    ly += 10;
                }
                for (String line : wrapLines(e.effect(), textWidth)) {
                    g.drawString(font, line, guidePanelX + 6, ly, COL_LABEL, false);
                    ly += 10;
                }
            }
            y += h;
        }
        g.disableScissor();

        int viewH = contentBottomGuide - contentTopGuide;
        int contentH = guideContentHeight();
        if (contentH > viewH) {
            int barH = Math.max(12, viewH * viewH / contentH);
            int maxScroll = Math.max(0, contentH - viewH);
            guideScroll = Mth.clamp(guideScroll, 0, maxScroll);
            int barY = contentTopGuide + (int) ((viewH - barH) * (guideScroll / (float) maxScroll));
            g.fill(guidePanelX + guidePanelW - 3, barY, guidePanelX + guidePanelW - 1, barY + barH, 0xFF8A8A9A);
        }
        g.drawCenteredString(font, Component.translatable("screen.pz_moodle_immersion.admin.guide.close"),
                guidePanelX + guidePanelW / 2, footerTop + 4, 0xFFB0B0BA);
        g.flush();
    }

    /** 点击弹层条目：切换到该职业/特质并自动填入默认参数。 */
    private boolean clickGuide(double mouseX, double mouseY) {
        boolean inside = mouseX >= guidePanelX && mouseX < guidePanelX + guidePanelW
                && mouseY >= guidePanelY && mouseY < guidePanelY + guidePanelH;
        if (!inside) return false;
        int y = guidePanelY + 22 - guideScroll;
        for (GuideEntry e : guideEntries) {
            int h = guideEntryHeight(e);
            if (mouseY >= y && mouseY < y + h) {
                kind = e.kind();
                selected = e.id();
                selectedSlot = -1;
                pickerOpen = false;
                rebuildEditor();
                mechanics.setValue(e.template());
                guideOpen = false;
                return true;
            }
            y += h;
        }
        return true;   // 点在面板空白处：只吞掉点击，不关闭
    }

    /** 把一段带换行的说明文字按可用宽度折行。 */
    private List<String> wrapLines(String text, int width) {
        List<String> out = new ArrayList<>();
        if (text == null) return out;
        for (String raw : text.split("\n", -1)) {
            String remaining = raw;
            boolean empty = true;
            while (!remaining.isBlank()) {
                String part = font.plainSubstrByWidth(remaining, width);
                if (part.isBlank()) break;
                out.add(part);
                remaining = remaining.substring(part.length()).stripLeading();
                empty = false;
            }
            if (empty) out.add("");
        }
        return out;
    }

    private void drawPicker(GuiGraphics g, int mouseX, int mouseY) {
        g.fill(0, 0, width, height, 0xD8000000);
        g.fill(pickerX, pickerY, pickerX + pickerW, pickerY + pickerH, 0xFA14141A);
        g.renderOutline(pickerX, pickerY, pickerW, pickerH, COL_ACTIVE_EDGE);
        g.drawCenteredString(font, Component.translatable("screen.pz_moodle_immersion.admin.picker"),
                pickerX + pickerW / 2, pickerY + 5, 0xFFFFFFFF);
        g.flush();
        int index = pickerIndexAt(mouseX, mouseY);
        for (int i = 0; i < 36; i++) {
            int col = i % 9, row = i / 9;
            int sx = pickerX + 2 + col * pickerCell;
            int sy = pickerY + 18 + row * pickerCell;
            boolean hover = index == i;
            g.fill(sx, sy, sx + pickerCell - 2, sy + pickerCell - 2, hover ? COL_SLOT_HOVER : COL_SLOT);
            g.renderOutline(sx, sy, pickerCell - 2, pickerCell - 2, hover ? COL_ACTIVE_EDGE : 0xFF555563);
            ItemStack stack = minecraft.player.getInventory().getItem(i);
            if (!stack.isEmpty()) {
                g.flush();
                g.renderItem(stack, sx + 1, sy + 1);
                g.flush();
                g.renderItemDecorations(font, stack, sx + 1, sy + 1);
                g.flush();
            }
        }
        g.flush();
    }

    /** 鼠标指向的携带物品槽（考虑内容区滚动偏移），没有则返回 -1。 */
    private int slotAt(double mouseX, double mouseY) {
        if (pickerOpen) return -1;
        int slotY = contentTop + 11 - contentScroll;
        if (mouseY < slotY || mouseY >= slotY + 18) return -1;
        int innerX = editX + 4;
        int slot = (int) ((mouseX - innerX) / itemStep);
        if (slot < 0 || slot >= grants.length) return -1;
        return mouseX >= innerX + slot * itemStep && mouseX < innerX + slot * itemStep + 18 ? slot : -1;
    }

    /** 弹层里鼠标指向的背包格子，没有则返回 -1。 */
    private int pickerIndexAt(double mouseX, double mouseY) {
        int localX = (int) (mouseX - pickerX - 2);
        int localY = (int) (mouseY - pickerY - 18);
        if (localX < 0 || localY < 0) return -1;
        int col = localX / pickerCell, row = localY / pickerCell;
        if (col > 8 || row > 3) return -1;
        if (localX % pickerCell >= pickerCell - 2 || localY % pickerCell >= pickerCell - 2) return -1;
        return row * 9 + col;
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (guideOpen) {
            if (!clickGuide(mouseX, mouseY)) guideOpen = false;   // 点面板外关闭
            return true;
        }
        if (pickerOpen) {
            if (button == 0 && selectedSlot >= 0) {
                int index = pickerIndexAt(mouseX, mouseY);
                if (index >= 0) {
                    ItemStack stack = minecraft.player.getInventory().getItem(index);
                    grants[selectedSlot] = stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
                }
            }
            pickerOpen = false;
            return true;
        }
        if (button == 0) {
            if (clickList(mouseX, mouseY, leftX, "occupation") || clickList(mouseX, mouseY, rightX, "trait")) return true;
            int slot = slotAt(mouseX, mouseY);
            if (slot >= 0) {
                selectedSlot = slot;
                pickerOpen = true;
                return true;
            }
        } else if (button == 1) {
            int slot = slotAt(mouseX, mouseY);
            if (slot >= 0) {
                grants[slot] = ItemStack.EMPTY;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean clickList(double mouseX, double mouseY, int x, String panelKind) {
        if (mouseX < x || mouseX >= x + panelW || mouseY < TOP + LIST_HEAD || mouseY >= editBottom - 3) return false;
        int row = (int) ((mouseY - (TOP + LIST_HEAD) + ("occupation".equals(panelKind) ? occScroll : traitScroll)) / ROW_H);
        List<String> ids = new ArrayList<>();
        if ("occupation".equals(panelKind)) ids.addAll(Occupation.ALL.keySet()); else ids.addAll(OccupationTrait.ALL.keySet());
        if (row < 0 || row >= ids.size()) return true;
        kind = panelKind; selected = ids.get(row); selectedSlot = -1; pickerOpen = false; rebuildEditor(); return true;
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        // 「数值大全」弹层的滚动
        if (guideOpen) {
            int maxScroll = Math.max(0, guideContentHeight() - Math.max(1, guidePanelH - 39));
            guideScroll = Mth.clamp(guideScroll - (int) vertical * 12, 0, maxScroll);
            return true;
        }
        // 编辑卡下半部分的滚动内容区（物品/说明/状态值）
        if (mouseX >= editX && mouseX < editX + editW && mouseY >= contentTop - 8 && mouseY < viewportBottom) {
            contentScroll = Math.max(0, contentScroll - (int) vertical * 12);
            return true;
        }
        int step = (int) vertical * ROW_H;
        int viewH = editBottom - 3 - (TOP + LIST_HEAD);
        if (mouseX < leftX + panelW + GAP / 2.0) {
            occScroll = Mth.clamp(occScroll - step, 0, Math.max(0, Occupation.ALL.size() * ROW_H - viewH));
        } else if (mouseX >= rightX - GAP / 2.0) {
            traitScroll = Mth.clamp(traitScroll - step, 0, Math.max(0, OccupationTrait.ALL.size() * ROW_H - viewH));
        }
        return true;
    }

    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (guideOpen && keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            guideOpen = false;
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    public static void refreshDefinitions() {
        if (INSTANCE != null) INSTANCE.rebuildEditor();
    }
}
