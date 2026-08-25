package io.github.andrewwwwwwwwwwwwwww.ssc;

import io.github.andrewwwwwwwwwwwwwww.ssc.deathhistory.DeathHistoryData;
import io.github.andrewwwwwwwwwwwwwww.ssc.deathhistory.DeathRecord;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * {@code /deathhistory} — your own recent deaths as chat lines (where, when,
 * how much is in the body, whether it's still there). Operators can pass a
 * player name to view someone else's.
 */
public final class DeathHistoryCommand {
    private DeathHistoryCommand() {}

    public static void init() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(Commands.literal("deathhistory")
                        .executes(ctx -> {
                            ServerPlayer viewer = ctx.getSource().getPlayerOrException();
                            return show(viewer, viewer.getUUID(), viewer.getGameProfile().name());
                        })
                        .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                                .executes(ctx -> {
                                    ServerPlayer viewer = ctx.getSource().getPlayerOrException();
                                    Collection<NameAndId> targets = GameProfileArgument.getGameProfiles(ctx, "player");
                                    int shown = 0;
                                    for (NameAndId target : targets) {
                                        shown += show(viewer, target.id(), target.name());
                                    }
                                    if (targets.isEmpty()) {
                                        ctx.getSource().sendFailure(Component.literal("No such player."));
                                    }
                                    return shown;
                                }))));
    }

    private static int show(ServerPlayer viewer, UUID subject, String subjectName) {
        List<DeathRecord> records = DeathHistoryData.get(viewer.level().getServer()).getFor(subject);
        if (records.isEmpty()) {
            viewer.sendSystemMessage(Component.literal("No recorded deaths for " + subjectName + "."));
            return 0;
        }
        viewer.sendSystemMessage(Component.literal("Deaths of " + subjectName + " (newest first):"));
        for (DeathRecord record : records) {
            String where = record.dimension() + " " + record.pos().getX() + " " + record.pos().getY() + " " + record.pos().getZ();
            String body = record.corpseGone() ? "body gone" : "body still there";
            viewer.sendSystemMessage(Component.literal(
                    " - " + ago(record.time()) + ", " + where + ": "
                            + record.nonEmptyCount() + " items, " + record.xp() + " xp (" + body + ")"));
        }
        return records.size();
    }

    private static String ago(long time) {
        long minutes = Math.max(0, (System.currentTimeMillis() - time) / 60_000);
        if (minutes < 1) {
            return "just now";
        }
        if (minutes < 60) {
            return minutes + "m ago";
        }
        long hours = minutes / 60;
        if (hours < 48) {
            return hours + "h ago";
        }
        return (hours / 24) + "d ago";
    }
}
