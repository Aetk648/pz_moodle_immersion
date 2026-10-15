package com.pzmoodle.immersion;

import com.pzmoodle.immersion.admin.DefOverrides;
import com.pzmoodle.immersion.admin.PZComponents;
import com.pzmoodle.immersion.config.ClientConfig;
import com.pzmoodle.immersion.config.CommonConfig;
import com.pzmoodle.immersion.moodle.MoodleAttachments;
import com.pzmoodle.immersion.net.Packets;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

/**
 * PZ Moodle Immersion —— 把《僵尸毁灭工程》的 Moodle 状态系统移植到 Minecraft。
 * 沉浸优先：右上角只显示僵毁风格图标，不显示具体数值。
 */
@Mod(PZMoodleImmersion.MODID)
public class PZMoodleImmersion {
    public static final String MODID = "pz_moodle_immersion";

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }

    public PZMoodleImmersion(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, CommonConfig.SPEC, "pz_moodle_immersion-common.toml");
        container.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC, "pz_moodle_immersion-client.toml");
        MoodleAttachments.ATTACHMENTS.register(modBus);
        PZComponents.COMPONENTS.register(modBus);
        DefOverrides.load();
        modBus.addListener(Packets::register);
    }
}
