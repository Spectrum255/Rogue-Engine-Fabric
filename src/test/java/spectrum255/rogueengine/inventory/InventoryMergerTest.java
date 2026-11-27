package spectrum255.rogueengine.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

class InventoryMergerTest {
	@BeforeAll
	static void bootstrapMinecraft() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		Bootstrap.validate();
		DataComponentMap components = DataComponentMap.builder()
				.set(DataComponents.MAX_STACK_SIZE, 64).build();
		Items.STONE.builtInRegistryHolder().bindComponents(components);
		Items.DIAMOND.builtInRegistryHolder().bindComponents(components);
	}

	@Test
	void mergesIntoMatchingAndEmptySlots() {
		SimpleContainer destination = new SimpleContainer(4);
		destination.setItem(0, new ItemStack(Items.STONE, 10));

		List<ItemStack> overflow = InventoryMerger.merge(destination,
				List.of(new ItemStack(Items.STONE, 5), new ItemStack(Items.DIAMOND, 3)));

		assertTrue(overflow.isEmpty());
		assertEquals(15, destination.getItem(0).getCount());
		assertEquals(3, destination.getItem(1).getCount());
	}

	@Test
	void respectsMainInventorySlotLimit() {
		SimpleContainer destination = new SimpleContainer(4);

		List<ItemStack> overflow = InventoryMerger.merge(destination,
				List.of(new ItemStack(Items.STONE, 64), new ItemStack(Items.DIAMOND, 64), new ItemStack(Items.STONE, 64)), 2);

		assertEquals(1, overflow.size());
		assertEquals(64, overflow.getFirst().getCount());
		assertTrue(destination.getItem(2).isEmpty());
		assertTrue(destination.getItem(3).isEmpty());
	}

	@Test
	void returnsUnplaceableItemsWithoutDropping() {
		SimpleContainer destination = new SimpleContainer(1);

		List<ItemStack> overflow = InventoryMerger.merge(destination, List.of(new ItemStack(Items.STONE, 70)));

		assertEquals(1, overflow.size());
		assertEquals(64, destination.getItem(0).getCount());
		assertEquals(6, overflow.getFirst().getCount());
	}
}
