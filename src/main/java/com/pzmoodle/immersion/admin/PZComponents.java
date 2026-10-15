package com.pzmoodle.immersion.admin;

import com.mojang.serialization.Codec;
import com.pzmoodle.immersion.PZMoodleImmersion;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredRegister;

/** 标记由职业或特质配置发放的物品，死亡时统一移除。 */
public final class PZComponents {
    public static final DeferredRegister<DataComponentType<?>> COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, PZMoodleImmersion.MODID);
    public static final java.util.function.Supplier<DataComponentType<Boolean>> GRANTED = COMPONENTS.register(
            "occupation_granted", () -> DataComponentType.<Boolean>builder().persistent(Codec.BOOL).build());

    private PZComponents() {}
}
