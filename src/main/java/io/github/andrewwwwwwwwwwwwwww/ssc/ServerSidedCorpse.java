package io.github.andrewwwwwwwwwwwwwww.ssc;

import io.github.andrewwwwwwwwwwwwwww.ssc.corpse.CorpseManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
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

    @Override
    public void onInitialize() {
        DeathHistoryCommand.init();

        // Config is per-installation; (re)load it as each server starts so
        // dedicated servers and singleplayer worlds both pick up edits.
        ServerLifecycleEvents.SERVER_STARTING.register(server -> CorpseConfig.load());

        // The fake body is drawn per viewer, driven by who can see the anchor.
        EntityTrackingEvents.START_TRACKING.register(CorpseManager::onStartTracking);
        EntityTrackingEvents.STOP_TRACKING.register(CorpseManager::onStopTracking);

        UseEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
            if (hand != InteractionHand.MAIN_HAND) {
                return InteractionResult.PASS;
            }
            return CorpseManager.onUse(player, level, entity)
                    ? InteractionResult.SUCCESS
                    : InteractionResult.PASS;
        });

        ServerTickEvents.END_SERVER_TICK.register(CorpseManager::tick);

        LOGGER.info("[SSC] ready — corpses will be shown to vanilla clients");
    }
}
