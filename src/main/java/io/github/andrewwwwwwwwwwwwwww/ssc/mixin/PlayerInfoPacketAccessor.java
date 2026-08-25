package io.github.andrewwwwwwwwwwwwwww.ssc.mixin;

import java.util.List;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Lets the fake-player layer build a player-info packet for a profile that has
 * no {@code ServerPlayer} behind it — the public constructors only accept real
 * players, so the packet is created empty and its entry list swapped in here.
 */
@Mixin(ClientboundPlayerInfoUpdatePacket.class)
public interface PlayerInfoPacketAccessor {
    @Mutable
    @Accessor("entries")
    void ssc$setEntries(List<ClientboundPlayerInfoUpdatePacket.Entry> entries);
}
