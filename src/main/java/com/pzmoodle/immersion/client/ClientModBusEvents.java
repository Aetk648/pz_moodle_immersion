package com.pzmoodle.immersion.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.pzmoodle.immersion.PZMoodleImmersion;
import net.minecraft.client.KeyMapping;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

/** 模组总线上的客户端注册（按键/HUD 层）。 */
@EventBusSubscriber(modid = PZMoodleImmersion.MODID,
        value = net.neoforged.api.distmarker.Dist.CLIENT,
        bus = EventBusSubscriber.Bus.MOD)
public final class ClientModBusEvents {

    public static final KeyMapping OPEN_OCCUPATION = new KeyMapping(
            "key.pz_moodle_immersion.occupation",
            InputConstants.UNKNOWN.getValue(), // 默认未绑定，建议玩家绑 O
            "key.categories.pz_moodle_immersion");
    public static final KeyMapping OPEN_ADMIN = new KeyMapping(
            "key.pz_moodle_immersion.admin",
            InputConstants.UNKNOWN.getValue(),
            "key.categories.pz_moodle_immersion");
    public static final KeyMapping OPEN_SETTINGS = new KeyMapping(
            "key.pz_moodle_immersion.settings",
            InputConstants.UNKNOWN.getValue(),
            "key.categories.pz_moodle_immersion");

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(OPEN_OCCUPATION);
        event.register(OPEN_ADMIN);
        event.register(OPEN_SETTINGS);
    }

    @SubscribeEvent
    public static void registerHud(RegisterGuiLayersEvent event) {
        MoodleHud.register(event);
    }

    private ClientModBusEvents() {}
}
