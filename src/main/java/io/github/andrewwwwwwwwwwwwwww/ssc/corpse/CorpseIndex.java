package io.github.andrewwwwwwwwwwwwwww.ssc.corpse;

import com.mojang.serialization.Codec;
import io.github.andrewwwwwwwwwwwwwww.ssc.ServerSidedCorpse;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Every live corpse on the server, keyed by its anchor entity's UUID and
 * persisted on the overworld's data storage so bodies survive restarts.
 */
public class CorpseIndex extends SavedData {

    public static final Codec<CorpseIndex> CODEC = Codec
            .unboundedMap(UUIDUtil.STRING_CODEC, Corpse.CODEC)
            .xmap(CorpseIndex::new, index -> index.byAnchor);

    public static final SavedDataType<CorpseIndex> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(ServerSidedCorpse.MOD_ID, "corpses"),
            CorpseIndex::new,
            CODEC,
            DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

    private final Map<UUID, Corpse> byAnchor;

    public CorpseIndex() {
        this.byAnchor = new HashMap<>();
    }

    private CorpseIndex(Map<UUID, Corpse> map) {
        this.byAnchor = new HashMap<>(map);
    }

    public static CorpseIndex get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public Corpse byAnchor(UUID anchorId) {
        return byAnchor.get(anchorId);
    }

    /** Resolve either of a corpse's two hitbox anchors to the corpse. */
    public Corpse byAnyAnchor(UUID anchorId) {
        Corpse primary = byAnchor.get(anchorId);
        if (primary != null) {
            return primary;
        }
        for (Corpse corpse : byAnchor.values()) {
            if (anchorId.equals(corpse.anchorId2)) {
                return corpse;
            }
        }
        return null;
    }

    public void put(Corpse corpse) {
        byAnchor.put(corpse.anchorId, corpse);
        setDirty();
    }

    public void remove(UUID anchorId) {
        if (byAnchor.remove(anchorId) != null) {
            setDirty();
        }
    }

    /** Stable snapshot for iteration (ticking may remove corpses mid-loop). */
    public List<Corpse> all() {
        return new ArrayList<>(byAnchor.values());
    }
}
