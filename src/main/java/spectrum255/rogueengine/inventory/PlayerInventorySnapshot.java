package spectrum255.rogueengine.inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.ItemStackWithSlot;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;

public final class PlayerInventorySnapshot {
	private final CompoundTag data;

	public PlayerInventorySnapshot(CompoundTag data) {
		this.data = Objects.requireNonNull(data, "data").copy();
		validate(this.data);
	}

	public static PlayerInventorySnapshot empty() {
		CompoundTag data = new CompoundTag();
		data.put("items", new ListTag());
		data.putInt("selected", 0);
		data.put("equipment", new CompoundTag());
		data.put("enderChest", new ListTag());
		return new PlayerInventorySnapshot(data);
	}

	public static PlayerInventorySnapshot capture(ServerPlayer player) {
		TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, player.registryAccess());
		player.getInventory().save(output.list("items", ItemStackWithSlot.CODEC));
		output.putInt("selected", player.getInventory().getSelectedSlot());
		EntityEquipment equipment = new EntityEquipment();
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			equipment.set(slot, player.getItemBySlot(slot).copy());
		}
		output.store("equipment", EntityEquipment.CODEC, equipment);
		player.getEnderChestInventory().storeAsItemList(output.list("enderChest", ItemStack.CODEC));
		return new PlayerInventorySnapshot(output.buildResult());
	}

	public void apply(ServerPlayer player) {
		ValueInput input = TagValueInput.create(ProblemReporter.DISCARDING, player.registryAccess(), data.copy());
		player.getInventory().load(input.listOrEmpty("items", ItemStackWithSlot.CODEC));
		player.getInventory().setSelectedSlot(Math.clamp(input.getIntOr("selected", 0), 0,
				player.getInventory().getSelectionSize() - 1));
		EntityEquipment equipment = input.read("equipment", EntityEquipment.CODEC).orElseGet(EntityEquipment::new);
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			player.setItemSlot(slot, equipment.get(slot).copy());
		}
		player.getEnderChestInventory().clearContent();
		player.getEnderChestInventory().fromItemList(input.listOrEmpty("enderChest", ItemStack.CODEC));
		player.inventoryMenu.broadcastChanges();
		player.containerMenu.broadcastChanges();
	}

	public void mergeInto(ServerPlayer player, ServerLevel destination, BlockPos position) {
		ValueInput input = TagValueInput.create(ProblemReporter.DISCARDING, player.registryAccess(), data.copy());
		List<ItemStack> inventoryItems = new ArrayList<>();
		input.listOrEmpty("items", ItemStackWithSlot.CODEC).forEach(slot -> inventoryItems.add(slot.stack().copy()));
		EntityEquipment equipment = input.read("equipment", EntityEquipment.CODEC).orElseGet(EntityEquipment::new);
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			if (!equipment.get(slot).isEmpty()) {
				inventoryItems.add(equipment.get(slot).copy());
			}
		}
		List<ItemStack> enderItems = new ArrayList<>();
		input.listOrEmpty("enderChest", ItemStack.CODEC).forEach(stack -> enderItems.add(stack.copy()));
		List<ItemStack> overflow = new ArrayList<>(InventoryMerger.merge(player.getInventory(), inventoryItems, Inventory.INVENTORY_SIZE));
		overflow.addAll(InventoryMerger.merge(player.getEnderChestInventory(), enderItems));
		dropOverflow(destination, position, overflow);
		player.inventoryMenu.broadcastChanges();
		player.containerMenu.broadcastChanges();
	}

	private static void dropOverflow(ServerLevel destination, BlockPos position, List<ItemStack> overflow) {
		for (ItemStack stack : overflow) {
			destination.addFreshEntity(new ItemEntity(destination, position.getX() + 0.5, position.getY() + 0.1,
					position.getZ() + 0.5, stack));
		}
	}

	public CompoundTag data() {
		return data.copy();
	}

	private static void validate(CompoundTag data) {
		if (!(data.get("items") instanceof ListTag) || !(data.get("equipment") instanceof CompoundTag)
				|| !(data.get("enderChest") instanceof ListTag) || !data.contains("selected")) {
			throw new IllegalArgumentException("Invalid player inventory snapshot");
		}
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof PlayerInventorySnapshot snapshot && data.equals(snapshot.data);
	}

	@Override
	public int hashCode() {
		return data.hashCode();
	}
}
