package spectrum255.rogueengine.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class PlayerSceneStatePolicyTest {
	@Test
	void decidesCaptureKeepAndRestore() {
		assertEquals(PlayerSceneStatePolicy.Action.CAPTURE, PlayerSceneStatePolicy.decide(false, 0L, 0L));
		assertEquals(PlayerSceneStatePolicy.Action.KEEP, PlayerSceneStatePolicy.decide(true, 2L, 2L));
		assertEquals(PlayerSceneStatePolicy.Action.RESTORE, PlayerSceneStatePolicy.decide(true, 2L, 3L));
	}

	@Test
	void rejectsFutureWatermark() {
		assertThrows(IllegalArgumentException.class, () -> PlayerSceneStatePolicy.decide(true, 4L, 3L));
	}
}
