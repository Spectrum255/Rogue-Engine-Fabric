package spectrum255.rogueengine.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ProfileSnapshotTest {
	@Test
	void sha256UsesLowercaseHex() {
		assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
				ProfileSnapshot.sha256("abc"));
	}
}
