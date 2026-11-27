package spectrum255.rogueengine.dimension;

import spectrum255.rogueengine.profile.BoxProfile;

public final class SpawnPreparationPolicy {
	private SpawnPreparationPolicy() {
	}

	public static boolean shouldSearch(BoxProfile.Spawn spawn) {
		return spawn instanceof BoxProfile.SearchSpawn;
	}
}
