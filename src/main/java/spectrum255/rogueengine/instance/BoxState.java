package spectrum255.rogueengine.instance;

public enum BoxState {
	CREATING,
	ACTIVE,
	RESETTING,
	ARCHIVING,
	ARCHIVED,
	RESTORING,
	DESTROY_PENDING,
	DESTROYED,
	RECOVERY_REQUIRED;

	public boolean canTransitionTo(BoxState target) {
		if (target == null || target == this) {
			return false;
		}
		return switch (this) {
			case CREATING -> target == ACTIVE || target == RECOVERY_REQUIRED;
			case ACTIVE -> target == ARCHIVING || target == RESETTING || target == RECOVERY_REQUIRED;
			case RESETTING -> target == ACTIVE || target == RECOVERY_REQUIRED;
			case ARCHIVING -> target == ARCHIVED || target == RECOVERY_REQUIRED;
			case ARCHIVED -> target == RESTORING || target == DESTROY_PENDING || target == RECOVERY_REQUIRED;
			case RESTORING -> target == ACTIVE || target == RECOVERY_REQUIRED;
			case DESTROY_PENDING -> target == DESTROYED || target == RECOVERY_REQUIRED;
			case DESTROYED, RECOVERY_REQUIRED -> false;
		};
	}
}
