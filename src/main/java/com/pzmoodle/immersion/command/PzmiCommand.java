package com.pzmoodle.immersion.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.pzmoodle.immersion.config.CommonConfig;
import com.pzmoodle.immersion.moodle.MoodleAttachments;
import com.pzmoodle.immersion.moodle.MoodleData;
import com.pzmoodle.immersion.net.Packets;
import com.pzmoodle.immersion.trait.Occupation;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * OP 命令：
 * /生存狂 <player>、/拳击手 <player> —— 解锁对应隐藏职业，再次输入则取消解锁（开关）
 * /解锁隐藏职业 <player>             —— 解锁全部隐藏职业
 * /锁定隐藏职业 <player>             —— 锁定全部隐藏职业（不影响已选中的职业，死亡重置后才生效）
 * /pzmi points <player> <value>     —— 单独设置某玩家职业带来的特质点数
 * /pzmi defaultpoints <value>       —— 修改世界默认特质点数基数
 * /pzmi occupation <player> <id>    —— 强制指定职业
 * /pzmi reset <player>              —— 清空职业与特质选择
 */
@EventBusSubscriber(modid = "pz_moodle_immersion", bus = EventBusSubscriber.Bus.GAME)
public final class PzmiCommand {

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();

        // 每个隐藏职业一条中文命令：/生存狂 <玩家>、/拳击手 <玩家>。
        // 再次输入同一命令会取消解锁（开关式）。
        for (String hiddenId : Occupation.HIDDEN) {
            String name = Occupation.HIDDEN_COMMAND_NAMES.getOrDefault(hiddenId, hiddenId);
            d.register(Commands.literal(name)
                    .requires(s -> s.hasPermission(2))
                    .then(Commands.argument("player", EntityArgument.player())
                            .executes(ctx -> toggleHidden(ctx, hiddenId))));
        }

        d.register(Commands.literal("解锁隐藏职业")
                .requires(s -> s.hasPermission(2))
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> setAllHidden(ctx, true))));

        d.register(Commands.literal("锁定隐藏职业")
                .requires(s -> s.hasPermission(2))
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> setAllHidden(ctx, false))));

        d.register(Commands.literal("pzmi")
                .requires(s -> s.hasPermission(2))
                .then(Commands.literal("points")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("value", IntegerArgumentType.integer(-100, 100))
                                        .executes(ctx -> setPoints(ctx)))))
                .then(Commands.literal("defaultpoints")
                        .then(Commands.argument("value", IntegerArgumentType.integer(-100, 100))
                                .executes(ctx -> {
                                    int v = IntegerArgumentType.getInteger(ctx, "value");
                                    CommonConfig.BASE_TRAIT_POINTS.set(v);
                                    CommonConfig.SPEC.save();
                                    ctx.getSource().sendSuccess(() -> Component
                                            .translatable("command.pz_moodle_immersion.defaultpoints", v)
                                            .withStyle(ChatFormatting.GREEN), true);
                                    return 1;
                                })))
                .then(Commands.literal("occupation")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .executes(ctx -> forceOccupation(ctx)))))
                .then(Commands.literal("reset")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> reset(ctx))))
                .then(Commands.literal("admin").executes(ctx -> openAdmin(ctx))));

        d.register(Commands.literal("职业管理")
                .requires(s -> s.hasPermission(2))
                .executes(PzmiCommand::openAdmin));
    }

    /** 开关式解锁：已解锁则取消，未解锁则解锁。 */
    private static int toggleHidden(CommandContext<CommandSourceStack> ctx, String id) {
        try {
            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
            MoodleData data = MoodleAttachments.get(target);
            String name = Occupation.HIDDEN_COMMAND_NAMES.getOrDefault(id, id);
            boolean nowUnlocked;
            if (data.unlockedHidden.contains(id)) {
                data.unlockedHidden.remove(id);
                nowUnlocked = false;
            } else {
                data.unlockedHidden.add(id);
                nowUnlocked = true;
            }
            Packets.sendSync(target);
            ctx.getSource().sendSuccess(() -> Component.literal("已为 " + target.getGameProfile().getName()
                            + (nowUnlocked ? " 解锁" : " 取消解锁") + name + "职业")
                    .withStyle(nowUnlocked ? ChatFormatting.GREEN : ChatFormatting.YELLOW), true);
            return 1;
        } catch (Exception e) {
            return 0;
        }
    }

    /** 批量解锁 / 批量锁定所有隐藏职业。锁定不影响当前已选职业，死亡重置后才生效。 */
    private static int setAllHidden(CommandContext<CommandSourceStack> ctx, boolean unlock) {
        try {
            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
            MoodleData data = MoodleAttachments.get(target);
            if (unlock) data.unlockedHidden.addAll(Occupation.HIDDEN);
            else data.unlockedHidden.clear();
            Packets.sendSync(target);
            String kept = !unlock && Occupation.isHidden(data.occupation)
                    ? "（当前职业 " + data.occupation + " 保留到下次死亡重置）" : "";
            ctx.getSource().sendSuccess(() -> Component.literal((unlock ? "已为 " : "已锁定 ")
                            + target.getGameProfile().getName() + " 的全部隐藏职业" + kept)
                    .withStyle(unlock ? ChatFormatting.GREEN : ChatFormatting.YELLOW), true);
            return 1;
        } catch (Exception e) {
            return 0;
        }
    }

    private static int setPoints(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
            int value = IntegerArgumentType.getInteger(ctx, "value");
            MoodleData data = MoodleAttachments.get(target);
            // 把职业点数存到玩家数据里（覆盖职业默认点数）
            data.traitPoints = value;
            Packets.sendSync(target);
            ctx.getSource().sendSuccess(() -> Component
                    .translatable("command.pz_moodle_immersion.points", target.getGameProfile().getName(), value)
                    .withStyle(ChatFormatting.GREEN), true);
            return 1;
        } catch (Exception e) {
            return 0;
        }
    }

    private static int forceOccupation(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
            String id = StringArgumentType.getString(ctx, "id");
            if (!Occupation.exists(id)) {
                ctx.getSource().sendFailure(Component.literal("Unknown occupation: " + id));
                return 0;
            }
            MoodleData data = MoodleAttachments.get(target);
            data.occupation = id;
            data.occupationChosen = true;
            // 与界面确认一致：新上限立刻生效，生命与体力回满。
            com.pzmoodle.immersion.server.PlayerProfile.restoreAfterProfileChange(target);
            Packets.sendSync(target);
            ctx.getSource().sendSuccess(() -> Component
                    .translatable("command.pz_moodle_immersion.occupation",
                            target.getGameProfile().getName(), id)
                    .withStyle(ChatFormatting.GREEN), true);
            return 1;
        } catch (Exception e) {
            return 0;
        }
    }

    private static int openAdmin(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, new Packets.AdminOpen());
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, new Packets.AdminDefinitions(com.pzmoodle.immersion.admin.DefOverrides.toJson()));
            return 1;
        } catch (Exception e) {
            return 0;
        }
    }

    private static int reset(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
            MoodleData data = MoodleAttachments.get(target);
            data.occupation = "";
            data.occupationChosen = false;
            data.traits.clear();
            data.traitPoints = 0;
            Packets.sendSync(target);
            ctx.getSource().sendSuccess(() -> Component
                    .translatable("command.pz_moodle_immersion.reset", target.getGameProfile().getName())
                    .withStyle(ChatFormatting.GREEN), true);
            return 1;
        } catch (Exception e) {
            return 0;
        }
    }

    private PzmiCommand() {}
}
