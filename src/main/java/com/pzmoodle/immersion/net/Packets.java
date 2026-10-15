package com.pzmoodle.immersion.net;

import com.pzmoodle.immersion.PZMoodleImmersion;
import com.pzmoodle.immersion.client.ClientState;
import com.pzmoodle.immersion.moodle.Moodle;
import com.pzmoodle.immersion.moodle.MoodleAttachments;
import com.pzmoodle.immersion.moodle.MoodleData;
import com.pzmoodle.immersion.admin.DefOverrides;
import com.pzmoodle.immersion.config.CommonConfig;
import com.pzmoodle.immersion.server.PlayerProfile;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * 只同步显示所需的最小数据：每个 Moodle 的等级 + 职业/特质选择状态。
 * 具体隐藏数值永远留在服务端。
 */
public final class Packets {
    /** 管理员配置 JSON 上限：完整 ItemStack 组件可能较大，但仍低于原版自定义包 1MiB 限制。 */
    private static final int MAX_JSON = 262144;

    public record AdminRequest() implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<AdminRequest> TYPE = new CustomPacketPayload.Type<>(PZMoodleImmersion.id("admin_request"));
        public static final StreamCodec<FriendlyByteBuf, AdminRequest> STREAM_CODEC = StreamCodec.unit(new AdminRequest());
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record AdminOpen() implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<AdminOpen> TYPE = new CustomPacketPayload.Type<>(PZMoodleImmersion.id("admin_open"));
        public static final StreamCodec<FriendlyByteBuf, AdminOpen> STREAM_CODEC = StreamCodec.unit(new AdminOpen());
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record AdminDefinitions(String json) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<AdminDefinitions> TYPE = new CustomPacketPayload.Type<>(PZMoodleImmersion.id("admin_definitions"));
        public static final StreamCodec<FriendlyByteBuf, AdminDefinitions> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> buf.writeUtf(p.json(), MAX_JSON), buf -> new AdminDefinitions(buf.readUtf(MAX_JSON)));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record AdminSave(String json) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<AdminSave> TYPE = new CustomPacketPayload.Type<>(PZMoodleImmersion.id("admin_save"));
        public static final StreamCodec<FriendlyByteBuf, AdminSave> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> buf.writeUtf(p.json(), MAX_JSON), buf -> new AdminSave(buf.readUtf(MAX_JSON)));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // ————— 服务端 → 客户端：Moodle 显示同步 —————
    public record MoodleSync(byte[] levels, String occupation, String traits, int points,
                             boolean canRechoose, String hiddenUnlocked, int[] growthLevels, int[] growthXp,
                             long zombieKills, long daysAlive, float[] rawValues,
                             float stamina, float staminaCap, int encumbranceLevel,
                             boolean allowLowFoodSprint)
            implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<MoodleSync> TYPE =
                new CustomPacketPayload.Type<>(PZMoodleImmersion.id("moodle_sync"));

        public static final StreamCodec<FriendlyByteBuf, MoodleSync> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeByte(p.levels.length);
                    buf.writeBytes(p.levels);
                    buf.writeUtf(p.occupation);
                    buf.writeUtf(p.traits);
                    buf.writeVarInt(p.points);
                    buf.writeBoolean(p.canRechoose);
                    buf.writeUtf(p.hiddenUnlocked);
                    writeIvars(buf, p.growthLevels);
                    writeIvars(buf, p.growthXp);
                    buf.writeLong(p.zombieKills);
                    buf.writeLong(p.daysAlive);
                    buf.writeVarInt(p.rawValues.length);
                    for (float value : p.rawValues) buf.writeFloat(value);
                    buf.writeFloat(p.stamina);
                    buf.writeFloat(p.staminaCap);
                    buf.writeVarInt(p.encumbranceLevel);
                    buf.writeBoolean(p.allowLowFoodSprint);
                },
                (buf) -> {
                    byte[] levels = new byte[buf.readByte() & 0xFF];
                    buf.readBytes(levels);
                    String occupation = buf.readUtf();
                    String traits = buf.readUtf();
                    int points = buf.readVarInt();
                    boolean canRechoose = buf.readBoolean();
                    String hiddenUnlocked = buf.readUtf();
                    int[] growthLevels = readIvars(buf);
                    int[] growthXp = readIvars(buf);
                    long zombieKills = buf.readLong();
                    long daysAlive = buf.readLong();
                    int rawCount = buf.readVarInt();
                    float[] rawValues = new float[rawCount];
                    for (int i = 0; i < rawCount; i++) rawValues[i] = buf.readFloat();
                    float stamina = buf.readFloat();
                    float staminaCap = buf.readFloat();
                    int encumbranceLevel = buf.readVarInt();
                    boolean allowLowFoodSprint = buf.readBoolean();
                    return new MoodleSync(levels, occupation, traits, points, canRechoose,
                            hiddenUnlocked, growthLevels, growthXp, zombieKills, daysAlive, rawValues,
                            stamina, staminaCap, encumbranceLevel, allowLowFoodSprint);
                });

        private static void writeIvars(FriendlyByteBuf buf, int[] values) {
            buf.writeVarInt(values.length);
            for (int v : values) buf.writeVarInt(v);
        }

        private static int[] readIvars(FriendlyByteBuf buf) {
            int[] out = new int[buf.readVarInt()];
            for (int i = 0; i < out.length; i++) out[i] = buf.readVarInt();
            return out;
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // ————— 客户端 → 服务端：确认整套职业与特质 —————
    /**
     * 只有点「确认」时才会发送。职业与特质一次性校验、一次性生效，
     * 避免玩家在界面上随手点一下就改了职业。
     */
    public record ConfirmProfile(String occupation, String traits) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<ConfirmProfile> TYPE =
                new CustomPacketPayload.Type<>(PZMoodleImmersion.id("confirm_profile"));

        public static final StreamCodec<FriendlyByteBuf, ConfirmProfile> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeUtf(p.occupation);
                    buf.writeUtf(p.traits);
                },
                (buf) -> new ConfirmProfile(buf.readUtf(), buf.readUtf()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("4");
        registrar.playToClient(AdminOpen.TYPE, AdminOpen.STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> com.pzmoodle.immersion.client.AdminScreen.open()));
        registrar.playToClient(AdminDefinitions.TYPE, AdminDefinitions.STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> {
                    DefOverrides.fromJson(payload.json());
                    com.pzmoodle.immersion.client.AdminScreen.refreshDefinitions();
                }));
        registrar.playToServer(AdminRequest.TYPE, AdminRequest.STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> {
                    if (context.player() instanceof net.minecraft.server.level.ServerPlayer sp) {
                        if (sp.hasPermissions(2)) {
                            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(sp, new AdminOpen());
                            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(sp, new AdminDefinitions(DefOverrides.toJson()));
                        } else {
                            sp.sendSystemMessage(net.minecraft.network.chat.Component.translatable("message.pz_moodle_immersion.admin_denied"));
                        }
                    }
                }));
        registrar.playToServer(AdminSave.TYPE, AdminSave.STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> {
                    if (context.player() instanceof net.minecraft.server.level.ServerPlayer sp && sp.hasPermissions(2)) {
                        try {
                            DefOverrides.applyEditJson(payload.json());
                            DefOverrides.save();
                            if (sp.getServer() != null) com.pzmoodle.immersion.admin.GrantItems.regrantOnline(sp.getServer());
                            net.neoforged.neoforge.network.PacketDistributor.sendToAllPlayers(new AdminDefinitions(DefOverrides.toJson()));
                        } catch (RuntimeException ignored) {
                            sp.sendSystemMessage(net.minecraft.network.chat.Component.translatable("message.pz_moodle_immersion.admin_invalid"));
                        }
                    }
                }));
        registrar.playToClient(MoodleSync.TYPE, MoodleSync.STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> {
                    if (context.player() != null && !context.player().level().isClientSide) return;
                    ClientState.applySync(payload);
                }));
        registrar.playToServer(ConfirmProfile.TYPE, ConfirmProfile.STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> {
                    if (context.player() instanceof net.minecraft.server.level.ServerPlayer sp) {
                        PlayerProfile.confirmProfile(sp, payload.occupation(), payload.traits());
                    }
                }));
    }

    /** 服务端：把某玩家的显示状态推给客户端。 */
    public static void sendSync(net.minecraft.server.level.ServerPlayer player) {
        MoodleData data = MoodleAttachments.get(player);
        byte[] levels = new byte[Moodle.ALL.length];
        for (Moodle m : Moodle.ALL) levels[Moodle.index(m)] = (byte) data.displayLevels[Moodle.index(m)];
        String traits = String.join(",", data.traits);
        int points = PlayerProfile.availablePoints(player);
        boolean canRechoose = !data.occupationChosen
                || com.pzmoodle.immersion.config.CommonConfig.ALLOW_RECHOOSE.get();
        com.pzmoodle.immersion.moodle.StatData stats = MoodleAttachments.stats(player);
        int[] growthLevels = new int[com.pzmoodle.immersion.moodle.Skill.COUNT];
        int[] growthXp = new int[growthLevels.length];
        for (com.pzmoodle.immersion.moodle.Skill skill : com.pzmoodle.immersion.moodle.Skill.ALL) {
            growthLevels[skill.ordinal()] = stats.level(skill);
            growthXp[skill.ordinal()] = stats.xp(skill);
        }
        float[] rawValues = player.hasPermissions(2) ? rawValues(player, data) : new float[0];
        int fitness = com.pzmoodle.immersion.moodle.MoodleSystem.effectiveLevel(player, com.pzmoodle.immersion.moodle.Skill.FITNESS);
        float staminaCap = com.pzmoodle.immersion.moodle.Stamina.cap(fitness);
        int loadLevel = com.pzmoodle.immersion.moodle.Encumbrance.iconLevel(
                com.pzmoodle.immersion.moodle.StaminaSystem.loadState(player));
        CustomPacketPayload payload = new MoodleSync(levels, data.occupation, traits, points, canRechoose,
                String.join(",", data.unlockedHidden), growthLevels, growthXp, data.zombieKills, data.daysAlive, rawValues,
                data.stamina < 0f ? staminaCap : data.stamina, staminaCap, loadLevel,
                CommonConfig.HUNGER_DISABLE_VANILLA_DRAIN.get());
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, payload);
    }

    /**
     * 管理员专用的 0-100 状态读数；普通客户端收到空数组，不暴露隐藏数值。
     * 能拿到连续来源的一律给连续值（湿漉漉按剩余时间、吃饱按饱食+饱和度、
     * 低温/高温按真实体温偏差），不再出现「一下从 0 蹦到 25/100」的台阶；
     * 只有流血/醉酒这类来源本身就是整数等级（药水等级）的才保持分级。
     */
    private static float[] rawValues(net.minecraft.server.level.ServerPlayer player, MoodleData data) {
        float[] values = new float[Moodle.ALL.length];
        values[Moodle.index(Moodle.BLEEDING)] = data.displayLevels[Moodle.index(Moodle.BLEEDING)] * 25f;
        int food = player.getFoodData().getFoodLevel();
        values[Moodle.index(Moodle.HUNGER)] = Math.max(0f, (20f - food) * 5f);
        // 吃饱：18 格起步、饱和度补足到 100，连续爬坡。
        values[Moodle.index(Moodle.FULL)] = Math.max(0f, Math.min(100f,
                (food - 17f) * 50f + player.getFoodData().getSaturationLevel() * 5f));
        values[Moodle.index(Moodle.PANIC)] = data.panic;
        values[Moodle.index(Moodle.BORED)] = data.bored;
        // 湿漉漉：剩余时间占比，出雨天后随时间平滑退去。
        int wetTotal = CommonConfig.WET_LINGER_SECONDS.get() * 20;
        values[Moodle.index(Moodle.WET)] = wetTotal > 0 && data.wetTicks > 0
                ? Math.min(100f, data.wetTicks * 100f / wetTotal) : 0f;
        float maxHealth = Math.max(1f, player.getMaxHealth());
        values[Moodle.index(Moodle.PAIN)] = Math.max(0f, (1f - player.getHealth() / maxHealth) * 100f);
        values[Moodle.index(Moodle.UNHAPPY)] = data.unhappy;
        values[Moodle.index(Moodle.STRESS)] = data.stress;
        values[Moodle.index(Moodle.TIRED)] = Math.min(100f, data.fatigue / 120f * 100f);
        int thirst = com.pzmoodle.immersion.compat.Compat.thirstValue(player);
        values[Moodle.index(Moodle.THIRST)] = thirst < 0 ? 0f : Math.max(0f, Math.min(100f, (20f - thirst) * 5f));
        values[Moodle.index(Moodle.DRUNK)] = data.displayLevels[Moodle.index(Moodle.DRUNK)] * 25f;
        // 低温/高温：直接读真实体温偏差（每 75 度偏差对应 4 级满值），连续变化；
        // 联动模组没装或读不到时退回图标等级 ×25。
        double temp = CommonConfig.COMPAT_COLD_SWEAT.get()
                ? com.pzmoodle.immersion.compat.Compat.bodyTemperature(player) : Double.NaN;
        if (!Double.isNaN(temp)) {
            double coldOver = CommonConfig.COLD_THRESHOLD.get() - temp;
            double hotOver = temp - CommonConfig.HOT_THRESHOLD.get();
            values[Moodle.index(Moodle.COLD)] = coldOver > 0 ? (float) Math.min(100.0, coldOver * 100.0 / 75.0) : 0f;
            values[Moodle.index(Moodle.HOT)] = hotOver > 0 ? (float) Math.min(100.0, hotOver * 100.0 / 75.0) : 0f;
        } else {
            values[Moodle.index(Moodle.COLD)] = data.displayLevels[Moodle.index(Moodle.COLD)] * 25f;
            values[Moodle.index(Moodle.HOT)] = data.displayLevels[Moodle.index(Moodle.HOT)] * 25f;
        }
        values[Moodle.index(Moodle.STAMINA)] = com.pzmoodle.immersion.moodle.StaminaSystem.currentPercent(player);
        // 负重给平滑比例（0-100），而不是图标等级 ×25，管理员界面就不会出现 25/50/100 的台阶。
        values[Moodle.index(Moodle.HEAVY_LOAD)] =
                com.pzmoodle.immersion.moodle.StaminaSystem.loadState(player).ratio() * 100f;
        return values;
    }

    private Packets() {}
}
