package io.github.andrewwwwwwwwwwwwwww.ssc.mixin;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Exposes {@link Entity}'s pose data key for building fake-entity metadata packets. */
@Mixin(Entity.class)
public interface EntityPoseAccessor {
    @Accessor("DATA_POSE")
    static EntityDataAccessor<Pose> ssc$dataPose() {
        throw new AssertionError();
    }
}
