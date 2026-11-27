package spectrum255.rogueengine.inventory;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

public final class InventoryMerger {
	private InventoryMerger() {
	}

	public static List<ItemStack> merge(Container destination, List<ItemStack> source) {
		return merge(destination, source, destination.getContainerSize());
	}

	public static List<ItemStack> merge(Container destination, List<ItemStack> source, int slotLimit) {
		int limit = Math.clamp(slotLimit, 0, destination.getContainerSize());
		List<ItemStack> overflow = new ArrayList<>();
		for (ItemStack original : source) {
			if (original.isEmpty()) {
				continue;
			}
			int remaining = original.getCount();
			while (remaining > 0) {
				int transferred = 0;
				for (int slot = 0; slot < limit && remaining > 0; slot++) {
					ItemStack existing = destination.getItem(slot);
					if (existing.isEmpty() || !ItemStack.isSameItemSameComponents(existing, original)) {
						continue;
					}
					int amount = Math.min(existing.getMaxStackSize() - existing.getCount(), remaining);
					if (amount > 0) {
						existing.grow(amount);
						destination.setChanged();
						remaining -= amount;
						transferred += amount;
					}
				}
				if (remaining == 0) {
					break;
				}
				for (int slot = 0; slot < limit && remaining > 0; slot++) {
					if (!destination.getItem(slot).isEmpty()) {
						continue;
					}
					int amount = Math.min(original.getMaxStackSize(), remaining);
					destination.setItem(slot, original.copyWithCount(amount));
					remaining -= amount;
					transferred += amount;
				}
				if (transferred == 0) {
					break;
				}
			}
			if (remaining > 0) {
				overflow.add(original.copyWithCount(remaining));
			}
		}
		return List.copyOf(overflow);
	}
}
