package com.pzmoodle.immersion.client;

import com.pzmoodle.immersion.PZMoodleImmersion;
import com.pzmoodle.immersion.client.gui.OccupationScreen;
import com.pzmoodle.immersion.client.gui.SettingsScreen;
import com.pzmoodle.immersion.config.ClientConfig;
import com.pzmoodle.immersion.moodle.Moodle;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/** 游戏总线上的客户端事件：按键响应、压力抖动、暂停菜单入口。 */
@EventBusSubscriber(modid = PZMoodleImmersion.MODID,
        value = net.neoforged.api.distmarker.Dist.CLIENT,
        bus = EventBusSubscriber.Bus.GAME)
public final class ClientEvents {

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        if (ClientModBusEvents.OPEN_OCCUPATION.consumeClick()) {
            mc.setScreen(new OccupationScreen());
        } else if (ClientModBusEvents.OPEN_ADMIN.consumeClick()) {
            net.neoforged.neoforge.network.PacketDistributor.sendToServer(new com.pzmoodle.immersion.net.Packets.AdminRequest());
        } else if (ClientModBusEvents.OPEN_SETTINGS.consumeClick()) {
            mc.setScreen(new SettingsScreen());
        }
    }

    /** 压力 4 级：轻微视野抖动。 */
    @SubscribeEvent
    public static void onCamera(ViewportEvent.ComputeCameraAngles event) {
        if (!ClientConfig.STRESS_CAMERA_SHAKE.get()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return;
        if (ClientState.level(Moodle.STRESS) < 4) return;
        float t = mc.player.tickCount + (float) event.getPartialTick();
        event.setRoll(event.getRoll() + (float) Math.sin(t * 0.35f) * 0.8f);
        event.setPitch(event.getPitch() + (float) Math.sin(t * 0.22f + 1.7f) * 0.4f);
    }

    /** 暂停菜单加入口按钮（新手友好）。 */
    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof PauseScreen)) return;
        int cx = event.getScreen().width / 2;
        int cy = event.getScreen().height / 2;
        event.addListener(Button.builder(Component.translatable("gui.pz_moodle_immersion.open")
                        .withStyle(ChatFormatting.YELLOW), b ->
                Minecraft.getInstance().setScreen(new OccupationScreen()))
                .bounds(cx - 100, cy + 96, 98, 20).build());
        event.addListener(Button.builder(Component.translatable("gui.pz_moodle_immersion.settings"), b ->
                        Minecraft.getInstance().setScreen(new SettingsScreen()))
                .bounds(cx + 2, cy + 96, 98, 20).build());
    }

    private ClientEvents() {}
}
