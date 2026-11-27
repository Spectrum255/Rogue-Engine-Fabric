package spectrum255.rogueengine.instance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import spectrum255.rogueengine.profile.BoxProfile;

class SeedFactoryTest {
	@Test
	void fixedSeedIsPreserved() {
		assertEquals(42L, SeedFactory.choose(new BoxProfile.Seed("fixed", Optional.of(42L))));
	}
}
