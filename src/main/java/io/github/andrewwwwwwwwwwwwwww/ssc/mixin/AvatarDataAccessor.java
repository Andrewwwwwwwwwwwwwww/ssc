package io.github.andrewwwwwwwwwwwwwww.ssc.mixin;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Exposes the skin-layer customisation byte so fake corpse players render all skin layers. */
@Mixin(Avatar.class)
public interface AvatarDataAccessor {
    @Accessor("DATA_PLAYER_MODE_CUSTOMISATION")
    static EntityDataAccessor<Byte> ssc$skinCustomisation() {
        throw new AssertionError();
    }
}
