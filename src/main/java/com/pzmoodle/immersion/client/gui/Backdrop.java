package com.pzmoodle.immersion.client.gui;

import net.minecraft.client.gui.GuiGraphics;

/**
 * 界面底衬：铺一层半透明深色渐变，替代原版的 {@code Screen#renderBackground}。
 *
 * <p><b>为什么不用原版的 renderBackground：</b>
 * 1.21 的 {@code Screen#renderBackground} 会走 {@code renderBlurredBackground}
 * → {@code GameRenderer#processBlurEffect}，把整个主渲染画面做多级模糊后处理，
 * 同时通过 {@code PostChain#setFilterMode} 重新设置主渲染目标的纹理过滤和纹理绑定。
 * 这些残留的采样/绑定状态会让随后绘制的界面元素（尤其是字体图集上的文字和图标）发糊。
 *
 * <p>这里仍然避开那套后处理，但保留半透明底色，让配置界面后面的游戏画面和状态条可见。
 * 界面控件自身使用不透明颜色，因此文字和开关仍保持清晰。
 */
public final class Backdrop {
    /** 顶部色，约 60% 不透明。 */
    private static final int TOP = 0x9912121A;
    /** 底部色，约 65% 不透明。 */
    private static final int BOTTOM = 0xA608080C;

    private Backdrop() {}

    /** 把整屏铺成半透明深色渐变，同时保留后面的游戏画面。 */
    public static void render(GuiGraphics graphics, int width, int height) {
        graphics.fillGradient(0, 0, width, height, TOP, BOTTOM);
    }
}
