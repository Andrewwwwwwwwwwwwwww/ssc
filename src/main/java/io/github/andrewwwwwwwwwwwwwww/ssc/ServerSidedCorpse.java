package io.github.andrewwwwwwwwwwwwwww.ssc;

import io.github.andrewwwwwwwwwwwwwww.ssc.corpse.CorpseManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fully server-side corpses: vanilla clients see and loot dead players' bodies
 * with nothing installed. Entry point wires the death capture (mixin), the
 * anchor-tracking visuals, looting, and the aging tick together.
 */
public class ServerSidedCorpse implements ModInitializer {
    public static final String MOD_ID = "ssc";
    public static final Logger LOGGER = LoggerFactory.getLogger("ServerSidedCorpse");

    /**
     * Diagnostic logging, off unless {@code debugLogging} is set. Everything the
     * death handler decides — including every reason it declines to make a body —
     * goes through here, so a server owner chasing a missing corpse can turn the
     * reasoning on rather than guess at it.
     */
    public static void debug(String format, Object... args) {
        if (CorpseConfig.get().debugLogging) {
            LOGGER.info("[SSC] " + format, args);
        }
    }

    @Override
    public void onInitialize() {
        DeathHistoryCommand.init();
        SscCommand.init();

        // Config is per-installation; (re)load it as each server starts so
        // dedicated servers and singleplayer worlds both pick up edits.
        ServerLifecycleEvents.SERVER_STARTING.register(server -> CorpseConfig.load());

        // The fake body is drawn per viewer, driven by who can see the anchor.
        EntityTrackingEvents.START_TRACKING.register(CorpseManager::onStartTracking);
        EntityTrackingEvents.STOP_TRACKING.register(CorpseManager::onStopTracking);

        // A client that rebuilds its world drops the packet-only bodies without
        // telling the server, and start-tracking won't fire again for anything
        // already in range. Forget what these players were shown so the resync
        // tick re-sends it (see CorpseManager.resyncVisuals).
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            if (newPlayer.level().getServer() != null) {
                CorpseManager.forgetPlayer(newPlayer.level().getServer(), newPlayer.getUUID());
            }
        });
        ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, origin, destination) ->
                CorpseManager.forgetPlayer(destination.getServer(), player.getUUID()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                CorpseManager.forgetPlayer(server, handler.getPlayer().getUUID()));

        UseEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
            if (hand != InteractionHand.MAIN_HAND) {
                return InteractionResult.PASS;
            }
            return CorpseManager.onUse(player, level, entity)
                    ? InteractionResult.SUCCESS
                    : InteractionResult.PASS;
        });

        ServerTickEvents.END_SERVER_TICK.register(CorpseManager::tick);

        // Claim mods protect entity interaction, and the corpse hitbox is an
        // ordinary interaction entity to them — so looting a body inside a
        // claim is blocked until the server owner allows it. Say so once at
        // startup rather than leaving it to be discovered as a lost inventory.
        if (FabricLoader.getInstance().isModLoaded("openpartiesandclaims")) {
            LOGGER.info("[SSC] Open Parties and Claims detected. To let players loot bodies inside "
                    + "claims, add \"anything$#ssc:corpses\" to forcedEntityProtectionExceptionList in "
                    + "<world>/serverconfig/openpartiesandclaims-server.toml (server must be stopped to edit).");
        }

        LOGGER.info("[SSC] ready — corpses will be shown to vanilla clients");
    }
}
