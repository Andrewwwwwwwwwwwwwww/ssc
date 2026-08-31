package io.github.andrewwwwwwwwwwwwwww.ssc;

import com.mojang.brigadier.arguments.BoolArgumentType;
import io.github.andrewwwwwwwwwwwwwww.ssc.corpse.Corpse;
import io.github.andrewwwwwwwwwwwwwww.ssc.corpse.CorpseManager;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * {@code /ssc} — operator diagnostics. Because the body is drawn with packets
 * rather than being a real entity, "my corpse is invisible" can't be checked
 * with {@code /data} or the F3 entity list the way a normal entity could; these
 * subcommands are the way to see what the server thinks it has sent and to
 * force a re-send.
 */
public final class SscCommand {
    private SscCommand() {}

    public static void init() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(Commands.literal("ssc")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.literal("list")
                                .executes(ctx -> list(ctx.getSource())))
                        .then(Commands.literal("resend")
                                .executes(ctx -> resend(ctx.getSource(), ctx.getSource().getPlayerOrException()))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> resend(ctx.getSource(),
                                                EntityArgument.getPlayer(ctx, "player")))))
                        .then(Commands.literal("debug")
                                .then(Commands.argument("enabled", BoolArgumentType.bool())
                                        .executes(ctx -> debug(ctx.getSource(),
                                                BoolArgumentType.getBool(ctx, "enabled")))))));
    }

    private static int list(net.minecraft.commands.CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        List<Corpse> corpses = CorpseManager.all(server);
        if (corpses.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No bodies in the world."), false);
            return 0;
        }
        source.sendSuccess(() -> Component.literal(corpses.size() + " bodies:"), false);
        for (Corpse corpse : corpses) {
            int items = 0;
            for (int i = 0; i < corpse.contents.getContainerSize(); i++) {
                if (!corpse.contents.getItem(i).isEmpty()) {
                    items++;
                }
            }
            // shownTo is the whole point of this listing: a body that everyone
            // else can see but one player can't shows up as a missing name here.
            String seenBy = corpse.shownTo.isEmpty() ? "nobody" : String.valueOf(corpse.shownTo.size());
            String line = String.format(" - %s at %d %d %d in %s | %d items, %d xp | %d min old | shown to %s",
                    corpse.displayOwner(), (int) corpse.x, (int) corpse.y, (int) corpse.z,
                    corpse.dimension, items, corpse.storedXp, corpse.age / 1200L, seenBy);
            source.sendSuccess(() -> Component.literal(line), false);
        }
        return corpses.size();
    }

    /**
     * Drop everything the server believes {@code target}'s client has been
     * shown, so the next resync tick re-sends every body in range. This is both
     * the fix and the test for a client that has silently lost a body.
     */
    private static int resend(net.minecraft.commands.CommandSourceStack source, ServerPlayer target) {
        CorpseManager.forgetPlayer(source.getServer(), target.getUUID());
        source.sendSuccess(() -> Component.literal(
                "Cleared body visibility for " + target.getGameProfile().name()
                        + " — bodies in range will be re-sent within 2 seconds."), true);
        return 1;
    }

    private static int debug(net.minecraft.commands.CommandSourceStack source, boolean enabled) {
        CorpseConfig.get().debugLogging = enabled;
        CorpseConfig.save();
        source.sendSuccess(() -> Component.literal(
                "SSC debug logging " + (enabled ? "ON — death decisions will be logged to the server console."
                        : "OFF.")), true);
        return 1;
    }
}
