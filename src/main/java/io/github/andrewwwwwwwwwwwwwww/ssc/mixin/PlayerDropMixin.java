package io.github.andrewwwwwwwwwwwwwww.ssc.mixin;

import io.github.andrewwwwwwwwwwwwwww.ssc.CorpseConfig;
import io.github.andrewwwwwwwwwwwwwww.ssc.corpse.Corpse;
import io.github.andrewwwwwwwwwwwwwww.ssc.corpse.CorpseManager;
import io.github.andrewwwwwwwwwwwwwww.ssc.corpse.CorpsePlacement;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.gamerules.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Intercepts the death-drop step: instead of scattering the player's items as
 * loose item entities, sweep them (and their experience) into a corpse at the
 * death location. Runs at HEAD of {@code Player.dropEquipment} and cancels the
 * vanilla scatter entirely.
 */
@Mixin(Player.class)
public abstract class PlayerDropMixin {

    @Shadow protected abstract void destroyVanishingCursedItems();

    @Inject(method = "dropEquipment", at = @At("HEAD"), cancellable = true)
    private void ssc$captureCorpse(ServerLevel level, CallbackInfo ci) {
        Player self = (Player) (Object) this;
        if (!CorpseConfig.get().enabled || self.isSpectator()) {
            return;
        }
        // keepInventory keeps everything already — nothing to bury.
        if (level.getGameRules().get(GameRules.KEEP_INVENTORY)) {
            return;
        }

        // Honour Curse of Vanishing exactly as vanilla would before we sweep.
        this.destroyVanishingCursedItems();

        Inventory inventory = self.getInventory();
        // Snapshot the whole inventory keeping each item at its own slot index,
        // so the corpse can later return items to their original slots.
        NonNullList<ItemStack> byIndex = NonNullList.withSize(Corpse.CONTAINER_SIZE, ItemStack.EMPTY);
        boolean anyItems = false;
        int size = Math.min(inventory.getContainerSize(), Corpse.CONTAINER_SIZE);
        for (int i = 0; i < size; i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty()) {
                byIndex.set(i, stack);
                anyItems = true;
            }
        }

        int experience = CorpseConfig.get().keepExperience ? self.totalExperience : 0;

        // Respect where players want corpses to spawn. At a disallowed spot we
        // bail and let everything drop normally instead of stranding items.
        CorpseConfig cfg = CorpseConfig.get();
        if (!cfg.spawnInLava && self.isInLava()) {
            return;
        }
        if (!cfg.spawnOverVoid && CorpsePlacement.isOverVoid(level, self.blockPosition(), cfg.voidScanDepth)) {
            return;
        }

        if (!anyItems && experience <= 0) {
            return; // truly nothing to bury — let vanilla proceed (it drops nothing)
        }

        if (!CorpseManager.createFromDeath(level, self, byIndex, experience)) {
            return; // could not place a corpse — vanilla drops rather than deleting items
        }

        inventory.clearContent();
        self.skipDropExperience(); // the corpse carries the XP; don't also drop orbs
        ci.cancel();
    }
}
