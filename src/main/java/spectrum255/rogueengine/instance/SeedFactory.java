package spectrum255.rogueengine.instance;

import java.security.SecureRandom;

import spectrum255.rogueengine.profile.BoxProfile;

public final class SeedFactory {
	private static final SecureRandom RANDOM = new SecureRandom();

	private SeedFactory() {
	}

	public static long choose(BoxProfile.Seed seed) {
		if (seed.mode().equals("fixed")) {
			return seed.value().orElseThrow(() -> new IllegalArgumentException("Fixed seed requires a value"));
		}
		if (!seed.mode().equals("random")) {
			throw new IllegalArgumentException("Unknown seed mode: " + seed.mode());
		}
		return RANDOM.nextLong();
	}
}
