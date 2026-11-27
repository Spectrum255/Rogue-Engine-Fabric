package spectrum255.rogueengine.dimension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import spectrum255.rogueengine.profile.BoxProfile;

class SpawnPreparationPolicyTest {
	@Test
	void fixedSpawnKeepsConfiguredPosition() {
		assertFalse(SpawnPreparationPolicy.shouldSearch(
				new BoxProfile.FixedSpawn("fixed", 12.5, 70.0, -8.5, 45.0F)));
	}

	@Test
	void searchSpawnLooksForSafePosition() {
		assertTrue(SpawnPreparationPolicy.shouldSearch(new BoxProfile.SearchSpawn("search", 8)));
	}
}
