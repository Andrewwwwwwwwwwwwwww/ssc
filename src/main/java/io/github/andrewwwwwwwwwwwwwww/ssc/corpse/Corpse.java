package io.github.andrewwwwwwwwwwwwwww.ssc.corpse;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.NonNullList;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * One corpse: everything a dead player left behind, tied to the invisible
 * {@code minecraft:interaction} anchor entity that gives the body its clickable
 * hitbox in the world. The visible "body" is not an entity at all — it's a
 * packet-only fake player drawn per viewer (see {@link CorpseVisuals}).
 */
public final class Corpse {
    /** Player inventory container size in 26.2 (0-35 main, 36-39 armor, 40 offhand, 41-42 body/saddle). */
    public static final int CONTAINER_SIZE = 43;

    /**
     * Network entity ids for the fake body players. Vanilla allocates ids
     * counting up from 1, so a far-negative range can never collide with a real
     * entity on the client.
     */
    private static final AtomicInteger FAKE_IDS = new AtomicInteger(-100_000);

    /** UUID of the primary interaction anchor entity; also identifies the corpse itself. */
    public final UUID anchorId;
    /** Second hitbox anchor covering the upper body (two small squares hug the body better than one big one). */
    public final UUID anchorId2;
    public final UUID ownerId;
    public final String ownerName;
    /** The owner's signed skin textures property (empty when the account had none). */
    public final String skinValue;
    public final String skinSignature;
    public final Identifier dimension;
    /** Feet position of the lying body (the fake player's entity position). */
    public final double x;
    public final double z;
    /** Mutable: the body falls (see CorpseManager's gravity tick) until it rests. */
    public double y;
    public final float yaw;
    /**
     * Pinned bodies stay put where they were placed — floating on a pool, or
     * held just inside the world over the void — instead of falling.
     */
    public boolean pinned;

    public long age;
    public int storedXp;
    /** Downward speed of the manual fall; reset once the body lands. */
    public double fallVelocity;
    /** Index-aligned with the dead player's inventory, so items go back to their original slots. */
    public final SimpleContainer contents = new SimpleContainer(CONTAINER_SIZE);

    // Runtime-only identity of the packet-side fake player; re-rolled each server run.
    public final int fakeId = FAKE_IDS.decrementAndGet();
    public final UUID fakeUuid = UUID.randomUUID();

    /**
     * Players whose client currently has this body drawn. Runtime only: a
     * client that reloads its world (respawn, dimension change, reconnect)
     * silently drops the packet-only body, so this is what lets the resync
     * tick notice and re-send it.
     */
    public final java.util.Set<UUID> shownTo = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public Corpse(UUID anchorId, java.util.Optional<UUID> anchorId2,
                  UUID ownerId, String ownerName, String skinValue, String skinSignature,
                  Identifier dimension, double x, double y, double z, float yaw, boolean pinned,
                  long age, int storedXp, List<ItemStack> items) {
        this.anchorId = anchorId;
        this.anchorId2 = anchorId2.orElse(null);
        this.ownerId = ownerId;
        this.ownerName = ownerName;
        this.skinValue = skinValue;
        this.skinSignature = skinSignature;
        this.dimension = dimension;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pinned = pinned;
        this.age = age;
        this.storedXp = storedXp;
        for (int i = 0; i < CONTAINER_SIZE && i < items.size(); i++) {
            contents.setItem(i, items.get(i).copy());
        }
    }

    public static final Codec<Corpse> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("anchor").forGetter(c -> c.anchorId),
            UUIDUtil.CODEC.optionalFieldOf("anchor2").forGetter(c -> java.util.Optional.ofNullable(c.anchorId2)),
            UUIDUtil.CODEC.fieldOf("owner").forGetter(c -> c.ownerId),
            Codec.STRING.fieldOf("ownerName").forGetter(c -> c.ownerName),
            Codec.STRING.optionalFieldOf("skinValue", "").forGetter(c -> c.skinValue),
            Codec.STRING.optionalFieldOf("skinSignature", "").forGetter(c -> c.skinSignature),
            Identifier.CODEC.fieldOf("dimension").forGetter(c -> c.dimension),
            Codec.DOUBLE.fieldOf("x").forGetter(c -> c.x),
            Codec.DOUBLE.fieldOf("y").forGetter(c -> c.y),
            Codec.DOUBLE.fieldOf("z").forGetter(c -> c.z),
            Codec.FLOAT.optionalFieldOf("yaw", 0.0f).forGetter(c -> c.yaw),
            Codec.BOOL.optionalFieldOf("pinned", false).forGetter(c -> c.pinned),
            Codec.LONG.optionalFieldOf("age", 0L).forGetter(c -> c.age),
            Codec.INT.optionalFieldOf("xp", 0).forGetter(c -> c.storedXp),
            ItemStack.OPTIONAL_CODEC.listOf().fieldOf("items").forGetter(Corpse::itemList)
    ).apply(instance, Corpse::new));

    private List<ItemStack> itemList() {
        List<ItemStack> list = new ArrayList<>(CONTAINER_SIZE);
        for (int i = 0; i < CONTAINER_SIZE; i++) {
            list.add(contents.getItem(i));
        }
        return list;
    }

    /** Snapshot copies of the contents, index-aligned, for the death-history record. */
    public List<ItemStack> itemSnapshot() {
        List<ItemStack> list = new ArrayList<>(CONTAINER_SIZE);
        for (int i = 0; i < CONTAINER_SIZE; i++) {
            list.add(contents.getItem(i).copy());
        }
        return list;
    }

    /** ≤16-char, unique-enough profile name for the fake body player (never shown to players). */
    public String fakeName() {
        String hex = Long.toHexString(fakeUuid.getLeastSignificantBits());
        return "F_" + hex.substring(0, Math.min(12, hex.length()));
    }

    public String displayOwner() {
        return ownerName == null || ownerName.isEmpty() ? "Someone" : ownerName;
    }

    public boolean isSkeleton() {
        long skeletonTicks = io.github.andrewwwwwwwwwwwwwww.ssc.CorpseConfig.get().skeletonTicks();
        return skeletonTicks > 0 && age >= skeletonTicks;
    }

    public boolean isLootEmpty() {
        for (int i = 0; i < CONTAINER_SIZE; i++) {
            if (!contents.getItem(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    public NonNullList<ItemStack> drainContents() {
        NonNullList<ItemStack> list = NonNullList.withSize(CONTAINER_SIZE, ItemStack.EMPTY);
        for (int i = 0; i < CONTAINER_SIZE; i++) {
            list.set(i, contents.getItem(i));
            contents.setItem(i, ItemStack.EMPTY);
        }
        return list;
    }
}
