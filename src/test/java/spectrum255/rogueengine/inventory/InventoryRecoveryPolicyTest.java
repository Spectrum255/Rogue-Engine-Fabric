package spectrum255.rogueengine.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class InventoryRecoveryPolicyTest {
	@Test
	void interruptedEnterOutsideBoxRollsBack() {
		assertEquals(InventoryRecoveryPolicy.Action.RESTORE_OUTSIDE,
				InventoryRecoveryPolicy.recover(PlayerInventoryRecord.Phase.ENTERING, false));
	}

	@Test
	void interruptedEnterInsideBoxCompletes() {
		assertEquals(InventoryRecoveryPolicy.Action.APPLY_INSIDE,
				InventoryRecoveryPolicy.recover(PlayerInventoryRecord.Phase.ENTERING, true));
	}

	@Test
	void interruptedExitInsideBoxRollsBack() {
		assertEquals(InventoryRecoveryPolicy.Action.APPLY_INSIDE,
				InventoryRecoveryPolicy.recover(PlayerInventoryRecord.Phase.EXITING, true));
	}

	@Test
	void interruptedExitOutsideBoxCompletes() {
		assertEquals(InventoryRecoveryPolicy.Action.FINISH_IDLE,
				InventoryRecoveryPolicy.recover(PlayerInventoryRecord.Phase.EXITING, false));
	}

	@Test
	void stablePhasesNeedNoRecovery() {
		assertEquals(InventoryRecoveryPolicy.Action.NONE,
				InventoryRecoveryPolicy.recover(PlayerInventoryRecord.Phase.INSIDE, true));
		assertEquals(InventoryRecoveryPolicy.Action.NONE,
				InventoryRecoveryPolicy.recover(PlayerInventoryRecord.Phase.IDLE, false));
	}
}
