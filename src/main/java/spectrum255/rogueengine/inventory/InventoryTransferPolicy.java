package spectrum255.rogueengine.inventory;

import spectrum255.rogueengine.profile.BoxProfile;

public final class InventoryTransferPolicy {
	private InventoryTransferPolicy() {
	}

	public static Plan enter(BoxProfile.InventoryPolicy policy, boolean hasStoredRecord) {
		if (!policy.bringOnEnter()) {
			return new Plan(true, false, true, false, false, false);
		}
		return new Plan(false, false, false, hasStoredRecord, false, false);
	}

	public static Plan leave(BoxProfile.InventoryPolicy policy, boolean hasOutsideSnapshot) {
		if (policy.takeOnExit()) {
			return hasOutsideSnapshot
					? new Plan(false, true, false, true, false, false)
					: new Plan(false, false, false, false, false, false);
		}
		return new Plan(false, hasOutsideSnapshot, false, false, true, !hasOutsideSnapshot);
	}

	public record Plan(
			boolean saveOutside,
			boolean restoreOutside,
			boolean applyInside,
			boolean mergeInside,
			boolean saveInside,
			boolean clearIfNoOutside) {
	}
}
