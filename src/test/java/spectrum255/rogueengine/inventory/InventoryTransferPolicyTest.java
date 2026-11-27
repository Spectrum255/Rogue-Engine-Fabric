package spectrum255.rogueengine.inventory;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import spectrum255.rogueengine.profile.BoxProfile;

class InventoryTransferPolicyTest {
	@Test
	void enterWithoutItemsSavesOutsideAndAppliesInside() {
		var plan = InventoryTransferPolicy.enter(new BoxProfile.InventoryPolicy(false, true), true);
		assertTrue(plan.saveOutside());
		assertTrue(plan.applyInside());
		assertFalse(plan.mergeInside());
	}

	@Test
	void enterWithItemsKeepsCurrentInventoryWhenNoStoredBoxExists() {
		var plan = InventoryTransferPolicy.enter(new BoxProfile.InventoryPolicy(true, true), false);
		assertFalse(plan.saveOutside());
		assertFalse(plan.applyInside());
		assertFalse(plan.mergeInside());
	}

	@Test
	void enterWithItemsMergesStoredBoxInventory() {
		var plan = InventoryTransferPolicy.enter(new BoxProfile.InventoryPolicy(true, false), true);
		assertFalse(plan.saveOutside());
		assertFalse(plan.applyInside());
		assertTrue(plan.mergeInside());
	}

	@Test
	void leaveWithoutItemsStoresInsideAndRestoresOutside() {
		var plan = InventoryTransferPolicy.leave(new BoxProfile.InventoryPolicy(false, false), true);
		assertTrue(plan.saveInside());
		assertTrue(plan.restoreOutside());
		assertFalse(plan.mergeInside());
	}

	@Test
	void leaveWithoutItemsClearsPlayerWhenNoOutsideSnapshotExists() {
		var plan = InventoryTransferPolicy.leave(new BoxProfile.InventoryPolicy(true, false), false);
		assertTrue(plan.saveInside());
		assertFalse(plan.restoreOutside());
		assertTrue(plan.clearIfNoOutside());
	}

	@Test
	void leaveWithItemsRestoresOutsideAndMergesBoxInventory() {
		var plan = InventoryTransferPolicy.leave(new BoxProfile.InventoryPolicy(false, true), true);
		assertFalse(plan.saveInside());
		assertTrue(plan.restoreOutside());
		assertTrue(plan.mergeInside());
	}

	@Test
	void leaveWithItemsKeepsCurrentInventoryWhenNoOutsideSnapshotExists() {
		var plan = InventoryTransferPolicy.leave(new BoxProfile.InventoryPolicy(true, true), false);
		assertFalse(plan.saveInside());
		assertFalse(plan.restoreOutside());
		assertFalse(plan.mergeInside());
	}
}
