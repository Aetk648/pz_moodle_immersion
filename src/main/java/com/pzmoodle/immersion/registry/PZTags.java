package com.pzmoodle.immersion.registry;

import com.pzmoodle.immersion.PZMoodleImmersion;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

public final class PZTags {
    /** 「香烟」物品标签：任何模组或数据包把物品挂上这个标签，烟瘾特质就能靠它减压。 */
    public static final TagKey<Item> CIGARETTE =
            TagKey.create(Registries.ITEM, PZMoodleImmersion.id("cigarettes"));

    private PZTags() {}
}
