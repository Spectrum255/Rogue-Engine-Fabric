package spectrum255.rogueengine.inventory;

public final class InventoryRecoveryPolicy {
	private InventoryRecoveryPolicy() {
	}

	public static Action recover(PlayerInventoryRecord.Phase phase, boolean insideBox) {
		return switch (phase) {
			case ENTERING -> insideBox ? Action.APPLY_INSIDE : Action.RESTORE_OUTSIDE;
			case EXITING -> insideBox ? Action.APPLY_INSIDE : Action.FINISH_IDLE;
			case INSIDE, IDLE -> Action.NONE;
		};
	}

	public enum Action {
		NONE,
		RESTORE_OUTSIDE,
		APPLY_INSIDE,
		FINISH_IDLE
	}
}
