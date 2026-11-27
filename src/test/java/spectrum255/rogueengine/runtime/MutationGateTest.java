package spectrum255.rogueengine.runtime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MutationGateTest {
	@Test
	void onlyOneMutationRunsAtATime() {
		MutationGate gate = new MutationGate();

		assertTrue(gate.tryBegin());
		assertFalse(gate.tryBegin());
		gate.end();
		assertTrue(gate.tryBegin());
	}
}
