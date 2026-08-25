package io.github.andrewwwwwwwwwwwwwww.ssc.mixin;

import net.minecraft.world.entity.Interaction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Opens up {@link Interaction}'s private hitbox sizing for the corpse anchor. */
@Mixin(Interaction.class)
public interface InteractionInvoker {
    @Invoker("setWidth")
    void ssc$setWidth(float width);

    @Invoker("setHeight")
    void ssc$setHeight(float height);
}
