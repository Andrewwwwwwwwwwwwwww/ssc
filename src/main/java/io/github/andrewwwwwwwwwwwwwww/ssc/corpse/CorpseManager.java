package io.github.andrewwwwwwwwwwwwwww.ssc.corpse;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import io.github.andrewwwwwwwwwwwwwww.ssc.CorpseConfig;
import io.github.andrewwwwwwwwwwwwwww.ssc.ServerSidedCorpse;
import io.github.andrewwwwwwwwwwwwwww.ssc.deathhistory.DeathHistoryData;
import io.github.andrewwwwwwwwwwwwwww.ssc.deathhistory.DeathRecord;
import io.github.andrewwwwwwwwwwwwwww.ssc.menu.CorpseChestMenu;
import io.github.andrewwwwwwwwwwwwwww.ssc.mixin.InteractionInvoker;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.Containers;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;

/**
 * Server-side corpse lifecycle: creation on death, the interaction-anchor
 * hitbox, looting, aging and despawn. The world-visible body is drawn by
 * {@link CorpseVisuals}; the persistent state lives in {@link CorpseIndex}.
 */
public final class CorpseManager {
    private CorpseManager() {}

    /** Command tag on the anchor entity identifying it as a corpse hitbox. */
    public static final String ANCHOR_TAG = "ssc_corpse";

    /**
     * The rendered body's feet sit at the fake player's position and the body
     * extends ~1.8 blocks toward the corpse's facing. Interaction hitboxes are
     * always square, so one box long enough for the body would be 2 blocks
     * wide; two small squares in a row (legs + torso/head) hug the body instead.
     */
    private static final double ANCHOR_OFFSET_LEGS = 0.45;
    private static final double ANCHOR_OFFSET_TORSO = 1.35;
    private static final float ANCHOR_WIDTH = 0.9f;
    private static final float ANCHOR_HEIGHT = 0.5f;

    /**
     * Create a corpse for a dying player. Returns false when nothing was
     * created (caller lets vanilla death proceed).
     */
    public static boolean createFromDeath(ServerLevel level, Player player,
                                          NonNullList<ItemStack> byIndex, int experience) {
        // Hazard deaths (pool/void) are placed and pinned immediately; a normal
        // death starts at the death spot and FALLS to the ground in tick().
        CorpsePlacement.RestSpot rest = CorpsePlacement.computeRestSpot(level, player.position());

        GameProfile profile = player.getGameProfile();
        String skinValue = "";
        String skinSignature = "";
        Collection<Property> textures = profile.properties().get("textures");
        if (!textures.isEmpty()) {
            Property texture = textures.iterator().next();
            skinValue = texture.value();
            skinSignature = texture.signature() == null ? "" : texture.signature();
        }

        // The anchor: a real (vanilla-type, vanilla-client-safe) interaction
        // entity giving the body its clickable hitbox, centered on the lying body.
        Interaction anchor = newAnchor(level);
        Interaction anchor2 = newAnchor(level);
        float yaw = Direction.fromYRot(player.getYRot()).toYRot(); // body lies along a cardinal; keep hitbox aligned

        Corpse corpse = new Corpse(anchor.getUUID(), java.util.Optional.of(anchor2.getUUID()),
                player.getUUID(),
                profile.name() == null ? "" : profile.name(),
                skinValue, skinSignature,
                level.dimension().identifier(),
                rest.x(), rest.y(), rest.z(), yaw, rest.pin(),
                0L, CorpseConfig.get().keepExperience ? experience : 0,
                byIndex);
        placeAnchors(anchor, anchor2, corpse);

        // Register the record BEFORE the anchors enter the world: adding an
        // entity fires start-tracking for nearby players synchronously, and the
        // tracking handler treats a tagged anchor with no record as an orphan
        // and discards it.
        MinecraftServer server = level.getServer();
        CorpseIndex.get(server).put(corpse);
        if (!level.addFreshEntity(anchor) || !level.addFreshEntity(anchor2)) {
            CorpseIndex.get(server).remove(corpse.anchorId);
            anchor.discard();
            anchor2.discard();
            ServerSidedCorpse.LOGGER.warn("[SSC] could not spawn corpse anchor for {}", profile.name());
            return false;
        }
        ServerSidedCorpse.LOGGER.info("[SSC] corpse of {} at {} {} {} in {}",
                profile.name(), (int) rest.x(), (int) rest.y(), (int) rest.z(), corpse.dimension);
        DeathHistoryData.get(server).record(
                player.getUUID(),
                new DeathRecord(corpse.anchorId, System.currentTimeMillis(), corpse.dimension,
                        BlockPos.containing(rest.x(), rest.y(), rest.z()),
                        corpse.itemSnapshot(), corpse.storedXp, false),
                CorpseConfig.get().deathHistorySize);
        return true;
    }

    private static Interaction newAnchor(ServerLevel level) {
        Interaction anchor = new Interaction(EntityTypes.INTERACTION, level);
        ((InteractionInvoker) anchor).ssc$setWidth(ANCHOR_WIDTH);
        ((InteractionInvoker) anchor).ssc$setHeight(ANCHOR_HEIGHT);
        anchor.addTag(ANCHOR_TAG);
        return anchor;
    }

    /** Keep both hitbox anchors on the lying body at the corpse's current height. */
    private static void placeAnchors(Entity anchor, Entity anchor2, Corpse corpse) {
        Direction facing = Direction.fromYRot(corpse.yaw);
        if (anchor != null) {
            anchor.setPos(corpse.x + ANCHOR_OFFSET_LEGS * facing.getStepX(),
                    corpse.y,
                    corpse.z + ANCHOR_OFFSET_LEGS * facing.getStepZ());
        }
        if (anchor2 != null) {
            anchor2.setPos(corpse.x + ANCHOR_OFFSET_TORSO * facing.getStepX(),
                    corpse.y,
                    corpse.z + ANCHOR_OFFSET_TORSO * facing.getStepZ());
        }
    }

    // --- anchor tracking → per-viewer visuals -------------------------------

    public static void onStartTracking(Entity entity, ServerPlayer viewer) {
        if (!(entity instanceof Interaction) || !entity.entityTags().contains(ANCHOR_TAG)) {
            return;
        }
        Corpse corpse = CorpseIndex.get(viewer.level().getServer()).byAnyAnchor(entity.getUUID());
        if (corpse == null) {
            entity.discard(); // orphaned anchor (data file gone) — never leave invisible hitboxes around
            return;
        }
        // Only the primary anchor drives the body's visuals, or two anchors
        // coming into view would draw the fake player twice.
        if (entity.getUUID().equals(corpse.anchorId)) {
            CorpseVisuals.spawnFor(viewer, corpse);
        }
    }

    public static void onStopTracking(Entity entity, ServerPlayer viewer) {
        if (!(entity instanceof Interaction) || !entity.entityTags().contains(ANCHOR_TAG)) {
            return;
        }
        Corpse corpse = CorpseIndex.get(viewer.level().getServer()).byAnyAnchor(entity.getUUID());
        if (corpse != null && entity.getUUID().equals(corpse.anchorId)) {
            CorpseVisuals.despawnFor(viewer, corpse);
        }
    }

    // --- looting ------------------------------------------------------------

    /** Handle a right-click on a corpse anchor. Returns true when it was one. */
    public static boolean onUse(Player player, Level level, Entity entity) {
        if (!(entity instanceof Interaction) || !entity.entityTags().contains(ANCHOR_TAG)) {
            return false;
        }
        if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer serverPlayer)) {
            return true; // it IS a corpse; just nothing to do off-server
        }
        Corpse corpse = CorpseIndex.get(server.getServer()).byAnyAnchor(entity.getUUID());
        if (corpse == null) {
            entity.discard();
            return false;
        }
        if (!canLoot(server, corpse, serverPlayer)) {
            serverPlayer.sendSystemMessage(
                    Component.literal("This corpse still belongs to " + corpse.displayOwner() + "."), true);
            return true;
        }
        if (serverPlayer.isShiftKeyDown()) {
            transferAllTo(corpse, serverPlayer);
        } else {
            openMenu(corpse, (Interaction) entity, serverPlayer);
        }
        CorpseIndex.get(server.getServer()).setDirty();
        return true;
    }

    private static boolean canLoot(ServerLevel server, Corpse corpse, ServerPlayer player) {
        CorpseConfig cfg = CorpseConfig.get();
        if (player.getUUID().equals(corpse.ownerId)) {
            return true;
        }
        if (cfg.opsBypassProtection
                && server.getServer().getPlayerList().isOp(new NameAndId(player.getGameProfile()))) {
            return true;
        }
        // Locked to the owner until the body ages into a skeleton — then anyone may loot it.
        return cfg.skeletonStageIsPublic && corpse.isSkeleton();
    }

    private static void openMenu(Corpse corpse, Interaction anchor, ServerPlayer player) {
        awardExperience(corpse, player);
        player.openMenu(new SimpleMenuProvider(
                (id, inv, p) -> new CorpseChestMenu(id, inv, corpse, anchor),
                Component.literal("Corpse of " + corpse.displayOwner())));
    }

    /** Hand every stored item back to its original inventory slot; a full inventory keeps the rest in the body. */
    public static void transferAllTo(Corpse corpse, Player player) {
        Inventory inv = player.getInventory();
        for (int i = 0; i < Corpse.CONTAINER_SIZE; i++) {
            ItemStack stack = corpse.contents.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (i < inv.getContainerSize() && inv.getItem(i).isEmpty()) {
                inv.setItem(i, stack); // original slot free — put it right back
                corpse.contents.setItem(i, ItemStack.EMPTY);
            } else {
                inv.add(stack); // mutates to whatever didn't fit
                // Keep the remainder in the body rather than dropping it, so a
                // full inventory never spills loot (into lava/void, say).
                corpse.contents.setItem(i, stack.isEmpty() ? ItemStack.EMPTY : stack);
            }
        }
        awardExperience(corpse, player);
    }

    private static void awardExperience(Corpse corpse, Player player) {
        if (corpse.storedXp > 0 && CorpseConfig.get().keepExperience) {
            player.giveExperiencePoints(corpse.storedXp);
            corpse.storedXp = 0;
        }
    }

    // --- lifecycle ----------------------------------------------------------

    /** Once per server tick: age loaded corpses, despawn old ones, clean out empties. */
    public static void tick(MinecraftServer server) {
        CorpseIndex index = CorpseIndex.get(server);
        boolean dirty = false;
        for (Corpse corpse : index.all()) {
            ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, corpse.dimension));
            if (level == null) {
                continue;
            }
            Entity anchor = level.getEntity(corpse.anchorId);
            if (anchor == null) {
                continue; // chunk not loaded — a corpse only ages while its chunk does
            }
            Entity anchor2 = corpse.anchorId2 == null ? null : level.getEntity(corpse.anchorId2);
            corpse.age++;
            dirty = true;
            applyGravity(level, corpse, anchor, anchor2);
            long despawn = CorpseConfig.get().despawnTicks();
            if (despawn > 0 && corpse.age >= despawn) {
                dropEverything(level, corpse, anchor);
                remove(server, level, corpse, anchor);
                continue;
            }
            if (corpse.isLootEmpty() && corpse.storedXp <= 0) {
                remove(server, level, corpse, anchor);
            }
        }
        if (dirty) {
            index.setDirty();
        }
    }

    /**
     * The original Fallen gravity, driven from the tick loop since there is no
     * body entity: a still pool floats the body on its surface (pinned), open
     * air pulls it down with the same acceleration/terminal speed as the
     * original, and breaking the block under a resting body makes it fall
     * again. Pinned bodies (floated / void-held) stay put.
     */
    private static void applyGravity(ServerLevel level, Corpse corpse, Entity anchor, Entity anchor2) {
        if (corpse.pinned) {
            corpse.fallVelocity = 0.0;
            return;
        }
        if (CorpsePlacement.inSourceFluid(level, corpse.x, corpse.y, corpse.z)) {
            corpse.y = CorpsePlacement.fluidSurfaceY(level,
                    net.minecraft.util.Mth.floor(corpse.x), net.minecraft.util.Mth.floor(corpse.z),
                    net.minecraft.util.Mth.floor(corpse.y));
            corpse.pinned = true; // floating — hold it on the surface
            placeAnchors(anchor, anchor2, corpse);
            CorpseVisuals.syncPosition(level, corpse);
            return;
        }
        Double surface = CorpsePlacement.surfaceBelowOrNull(level, corpse.x, corpse.y, corpse.z);
        double target = surface != null ? surface : level.getMinY() + 1;
        if (corpse.y <= target + 1.0E-3) {
            corpse.fallVelocity = 0.0;
            if (corpse.y < target - 1.0E-3) {
                return; // buried by a placed block — stay put rather than teleport up
            }
            return; // resting
        }
        corpse.fallVelocity = Math.min(corpse.fallVelocity + 0.08, 3.0);
        corpse.y = Math.max(target, corpse.y - corpse.fallVelocity);
        if (corpse.y <= target + 1.0E-3) {
            corpse.y = target;
            corpse.fallVelocity = 0.0;
            if (surface == null) {
                corpse.pinned = true; // fell into the open void — hold just inside the world
            }
        }
        placeAnchors(anchor, anchor2, corpse);
        CorpseVisuals.syncPosition(level, corpse);
    }

    private static void dropEverything(ServerLevel level, Corpse corpse, Entity anchor) {
        BlockPos pos = anchor.blockPosition();
        Containers.dropContents(level, pos, corpse.contents);
        if (corpse.storedXp > 0 && CorpseConfig.get().keepExperience) {
            ExperienceOrb.award(level, Vec3.atCenterOf(pos), corpse.storedXp);
            corpse.storedXp = 0;
        }
    }

    private static void remove(MinecraftServer server, ServerLevel level, Corpse corpse, Entity anchor) {
        if (anchor != null) {
            anchor.discard();
        }
        if (corpse.anchorId2 != null) {
            Entity anchor2 = level.getEntity(corpse.anchorId2);
            if (anchor2 != null) {
                anchor2.discard();
            }
        }
        CorpseVisuals.removeEverywhere(level, corpse);
        CorpseIndex.get(server).remove(corpse.anchorId);
        DeathHistoryData.get(server).markCorpseGone(corpse.ownerId, corpse.anchorId);
    }
}
