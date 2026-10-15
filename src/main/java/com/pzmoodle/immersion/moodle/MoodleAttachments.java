package com.pzmoodle.immersion.moodle;

import com.pzmoodle.immersion.PZMoodleImmersion;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

public final class MoodleAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, PZMoodleImmersion.MODID);

    /** 玩家 Moodle 数据；死亡后由 Clone 事件主动重置职业与特质。 */
    public static final Supplier<AttachmentType<MoodleData>> MOODLE = ATTACHMENTS.register(
            "moodle",
            () -> AttachmentType.serializable(MoodleData::new).copyOnDeath().build());

    /** 玩家成长数据；死亡后保留等级与经验。 */
    public static final Supplier<AttachmentType<StatData>> STATS = ATTACHMENTS.register(
            "stats",
            () -> AttachmentType.serializable(StatData::new).copyOnDeath().build());


    public static MoodleData get(Entity entity) {
        return entity.getData(MOODLE.get());
    }

    public static StatData stats(Entity entity) {
        return entity.getData(STATS.get());
    }

    public static boolean has(Entity entity) {
        return entity.hasData(MOODLE.get());
    }

    private MoodleAttachments() {}
}
