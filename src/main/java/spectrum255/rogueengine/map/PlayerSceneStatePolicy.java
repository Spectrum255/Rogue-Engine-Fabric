package spectrum255.rogueengine.map;

public final class PlayerSceneStatePolicy {
	private PlayerSceneStatePolicy() {
	}

	public static Action decide(boolean stateExists, long appliedResetCount, long currentResetCount) {
		if (!stateExists) {
			return Action.CAPTURE;
		}
		if (appliedResetCount > currentResetCount) {
			throw new IllegalArgumentException("Player state watermark is ahead of the box reset count");
		}
		return appliedResetCount < currentResetCount ? Action.RESTORE : Action.KEEP;
	}

	public enum Action {
		CAPTURE,
		KEEP,
		RESTORE
	}
}
