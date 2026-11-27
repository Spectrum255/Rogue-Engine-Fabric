package spectrum255.rogueengine.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.ItemStackWithSlot;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;

class PlayerInventorySnapshotTest {
	@BeforeAll
	static void bootstrapMinecraft() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		Bootstrap.validate();
		Items.STONE.builtInRegistryHolder().bindComponents(DataComponentMap.EMPTY);
		Items.IRON_SWORD.builtInRegistryHolder().bindComponents(DataComponentMap.EMPTY);
		Items.IRON_CHESTPLATE.builtInRegistryHolder().bindComponents(DataComponentMap.EMPTY);
		Items.SHIELD.builtInRegistryHolder().bindComponents(DataComponentMap.EMPTY);
		Items.DIAMOND.builtInRegistryHolder().bindComponents(DataComponentMap.EMPTY);
	}

	@Test
	void roundTripsInventoryEquipmentAndEnderChest() {
		TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, RegistryAccess.EMPTY);
		output.list("items", ItemStackWithSlot.CODEC).add(new ItemStackWithSlot(3, new ItemStack(Items.STONE, 7)));
		output.putInt("selected", 2);
		EntityEquipment equipment = new EntityEquipment();
		equipment.set(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		equipment.set(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
		equipment.set(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
		output.store("equipment", EntityEquipment.CODEC, equipment);
		output.list("enderChest", ItemStack.CODEC).add(new ItemStack(Items.DIAMOND, 4));

		PlayerInventorySnapshot snapshot = new PlayerInventorySnapshot(output.buildResult());
		CompoundTag restored = snapshot.data();
		ValueInput input = TagValueInput.create(ProblemReporter.DISCARDING, RegistryAccess.EMPTY, restored);

		assertEquals(2, input.getIntOr("selected", -1));
		var items = input.listOrEmpty("items", ItemStackWithSlot.CODEC).iterator();
		ItemStackWithSlot slot = items.next();
		assertEquals(3, slot.slot());
		assertEquals(7, slot.stack().getCount());
		assertEquals(Items.STONE, slot.stack().getItem());
		EntityEquipment restoredEquipment = input.read("equipment", EntityEquipment.CODEC).orElseThrow();
		assertEquals(Items.IRON_SWORD, restoredEquipment.get(EquipmentSlot.MAINHAND).getItem());
		assertEquals(Items.IRON_CHESTPLATE, restoredEquipment.get(EquipmentSlot.CHEST).getItem());
		assertEquals(Items.SHIELD, restoredEquipment.get(EquipmentSlot.OFFHAND).getItem());
		var enderChest = input.listOrEmpty("enderChest", ItemStack.CODEC).iterator();
		ItemStack enderStack = enderChest.next();
		assertEquals(4, enderStack.getCount());
		assertEquals(Items.DIAMOND, enderStack.getItem());
	}
}
