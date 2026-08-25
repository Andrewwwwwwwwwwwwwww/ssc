package io.github.andrewwwwwwwwwwwwwww.ssc.menu;

import io.github.andrewwwwwwwwwwwwwww.ssc.corpse.Corpse;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The corpse's loot screen, shown to vanilla clients as a plain 6-row chest —
 * the menu type is vanilla {@code GENERIC_9x6}, so no client mod is needed;
 * only this server-side instance knows the slots map onto a corpse.
 *
 * <p>Layout mirrors the inventory it came from: row 1 is armor (head→feet) and
 * offhand, rows 2–4 the main inventory, row 5 the hotbar, and unused cells are
 * inert glass-pane fillers. Slots are take-only; nothing can be put in.
 */
public class CorpseChestMenu extends AbstractContainerMenu {
    private static final int CHEST_SLOTS = 54;

    /**
     * Chest cell → inventory index (−1 = filler). Row 1: armor 39..36
     * (head→feet, matching vanilla's top-to-bottom order), gap, offhand 40.
     * Rows 2–4: main inventory 9..35 (indices line up 1:1 with the grid).
     * Row 5: hotbar 0..8. Row 6: filler.
     */
    private static final int[] CELL_TO_INV = buildCellMap();

    private static int[] buildCellMap() {
        int[] map = new int[CHEST_SLOTS];
        java.util.Arrays.fill(map, -1);
        map[0] = 39; // helmet
        map[1] = 38; // chestplate
        map[2] = 37; // leggings
        map[3] = 36; // boots
        map[5] = 40; // offhand
        for (int i = 9; i <= 35; i++) {
            map[i] = i; // main inventory, same index
        }
        for (int c = 0; c < 9; c++) {
            map[36 + c] = c; // hotbar
        }
        return map;
    }

    /** Shared inert filler shown in unused cells. */
    private static final SimpleContainer FILLER = new SimpleContainer(1);

    static {
        ItemStack pane = new ItemStack(Items.STAINED_GLASS_PANE.gray());
        pane.set(DataComponents.CUSTOM_NAME, Component.literal(" "));
        FILLER.setItem(0, pane);
    }

    private final Corpse corpse;
    private final Interaction anchor;

    public CorpseChestMenu(int containerId, Inventory playerInventory, Corpse corpse, Interaction anchor) {
        super(MenuType.GENERIC_9x6, containerId);
        this.corpse = corpse;
        this.anchor = anchor;

        // Chest grid: same slot count and order a vanilla 9x6 client builds, so
        // indices stay in sync. Screen coordinates are client-side; zeros are fine.
        for (int cell = 0; cell < CHEST_SLOTS; cell++) {
            int inv = CELL_TO_INV[cell];
            if (inv < 0) {
                addSlot(new Slot(FILLER, 0, 0, 0) {
                    @Override
                    public boolean mayPickup(Player player) {
                        return false;
                    }

                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return false;
                    }
                });
            } else {
                addSlot(new Slot(corpse.contents, inv, 0, 0) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return false; // take-only: you can never put items INTO a corpse
                    }
                });
            }
        }

        // The viewing player's own inventory, in vanilla ChestMenu order.
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 0, 0));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 0, 0));
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        // Take-only: only corpse cells shift-move (to the player); shift-clicking
        // your own items does nothing rather than pushing them into the corpse.
        if (index >= CHEST_SLOTS) {
            return ItemStack.EMPTY;
        }
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem() || !slot.mayPickup(player)) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (!moveItemStackTo(stack, CHEST_SLOTS, this.slots.size(), true)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        // When the corpse empties, the tick loop discards the anchor, which
        // closes the screen for everyone through this same check.
        return anchor.isAlive() && anchor.distanceToSqr(player) < 64.0;
    }
}
