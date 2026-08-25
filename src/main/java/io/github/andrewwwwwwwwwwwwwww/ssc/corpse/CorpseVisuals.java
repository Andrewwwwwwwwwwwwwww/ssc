package io.github.andrewwwwwwwwwwwwwww.ssc.corpse;

import com.google.common.collect.ImmutableMultimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import io.github.andrewwwwwwwwwwwwwww.ssc.mixin.AvatarDataAccessor;
import io.github.andrewwwwwwwwwwwwwww.ssc.mixin.EntityPoseAccessor;
import io.github.andrewwwwwwwwwwwwwww.ssc.mixin.PlayerInfoPacketAccessor;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team;

import java.util.EnumSet;
import java.util.List;

/**
 * The visible body, drawn entirely with packets so vanilla clients render it
 * with nothing installed: an unlisted player-info entry carrying the dead
 * player's signed skin, a {@code minecraft:player} entity in the SLEEPING pose
 * (which lies flat without a bed), and a packet-only scoreboard team that hides
 * the fake profile's nametag. No entity exists server-side for any of this —
 * the clickable hitbox is the separate interaction anchor.
 */
public final class CorpseVisuals {
    private CorpseVisuals() {}

    /** All skin overlay layers on (jacket, sleeves, hat, ...), so the body matches the player. */
    private static final byte ALL_SKIN_LAYERS = 0x7F;

    /**
     * Packet-only team that suppresses the fake players' nametags. It lives on
     * a private scoreboard that is never attached to the server, so it can't
     * collide with real scoreboard state; members accumulate as corpses spawn.
     */
    private static final PlayerTeam TEAM;

    static {
        TEAM = new PlayerTeam(new Scoreboard(), "fs_bodies");
        TEAM.setNameTagVisibility(Team.Visibility.NEVER);
    }

    /** Show this corpse's body to one viewer (called when they start tracking the anchor). */
    public static void spawnFor(ServerPlayer viewer, Corpse corpse) {
        // GameProfile is a record in current authlib and its property map is
        // immutable — the textures have to go in through the constructor.
        PropertyMap properties = new PropertyMap(corpse.skinValue.isEmpty()
                ? ImmutableMultimap.of()
                : ImmutableMultimap.of("textures", corpse.skinSignature.isEmpty()
                        ? new Property("textures", corpse.skinValue)
                        : new Property("textures", corpse.skinValue, corpse.skinSignature)));
        GameProfile profile = new GameProfile(corpse.fakeUuid, corpse.fakeName(), properties);

        // The public constructors only build entries from real ServerPlayers, so
        // create the packet empty and swap in a hand-built entry. UPDATE_LISTED
        // with listed=false keeps the profile out of tab while still letting the
        // client resolve its skin for as long as the corpse exists.
        var info = new ClientboundPlayerInfoUpdatePacket(
                EnumSet.of(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER,
                        ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LISTED),
                List.of());
        ((PlayerInfoPacketAccessor) info).ssc$setEntries(List.of(
                new ClientboundPlayerInfoUpdatePacket.Entry(
                        corpse.fakeUuid, profile, false, 0, GameType.SURVIVAL, null, false, 0, null)));
        viewer.connection.send(info);

        synchronized (TEAM) {
            TEAM.getPlayers().add(corpse.fakeName());
            // true = create; the packet carries the full member list, so re-sending
            // it for each corpse keeps every viewer's copy of the team consistent.
            viewer.connection.send(ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(TEAM, true));
        }

        // A bed-less sleeping player renders with its feet at the entity
        // position and its head extending toward (90° − bodyRot); the client
        // initialises bodyRot from the packet's HEAD rotation. So sending
        // 90 − yaw lays the body out head-first toward the corpse's facing,
        // exactly like the original Fallen renderer.
        viewer.connection.send(new ClientboundAddEntityPacket(
                corpse.fakeId, corpse.fakeUuid,
                corpse.x, corpse.y, corpse.z,
                0.0f, 90.0f - corpse.yaw,
                EntityTypes.PLAYER, 0, Vec3.ZERO, 90.0f - corpse.yaw));

        viewer.connection.send(new ClientboundSetEntityDataPacket(corpse.fakeId, List.of(
                SynchedEntityData.DataValue.create(EntityPoseAccessor.ssc$dataPose(), Pose.SLEEPING),
                SynchedEntityData.DataValue.create(AvatarDataAccessor.ssc$skinCustomisation(), ALL_SKIN_LAYERS))));
    }

    /** Re-position the fake body for everyone in the level (used while the body falls). */
    public static void syncPosition(ServerLevel level, Corpse corpse) {
        var packet = new ClientboundEntityPositionSyncPacket(corpse.fakeId,
                new PositionMoveRotation(new Vec3(corpse.x, corpse.y, corpse.z), Vec3.ZERO,
                        90.0f - corpse.yaw, 0.0f),
                false);
        for (ServerPlayer player : level.players()) {
            player.connection.send(packet); // unknown-id packets are ignored by non-tracking clients
        }
    }

    /** Remove this corpse's body from one viewer (stop-tracking or corpse removal). */
    public static void despawnFor(ServerPlayer viewer, Corpse corpse) {
        viewer.connection.send(new ClientboundRemoveEntitiesPacket(corpse.fakeId));
        viewer.connection.send(new ClientboundPlayerInfoRemovePacket(List.of(corpse.fakeUuid)));
    }

    /** Tear the body down everywhere — used when the corpse itself is removed. */
    public static void removeEverywhere(ServerLevel level, Corpse corpse) {
        synchronized (TEAM) {
            TEAM.getPlayers().remove(corpse.fakeName());
        }
        for (ServerPlayer player : level.players()) {
            despawnFor(player, corpse);
            player.connection.send(ClientboundSetPlayerTeamPacket.createPlayerPacket(
                    TEAM, corpse.fakeName(), ClientboundSetPlayerTeamPacket.Action.REMOVE));
        }
    }
}
