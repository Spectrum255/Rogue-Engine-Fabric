package spectrum255.rogueengine.instance;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class BoxStateMachineTest {
	@Test
	void legalLifecycleTransitionsAreAccepted() {
		assertTrue(BoxState.CREATING.canTransitionTo(BoxState.ACTIVE));
		assertTrue(BoxState.ACTIVE.canTransitionTo(BoxState.ARCHIVING));
		assertTrue(BoxState.ARCHIVING.canTransitionTo(BoxState.ARCHIVED));
		assertTrue(BoxState.ARCHIVED.canTransitionTo(BoxState.RESTORING));
		assertTrue(BoxState.RESTORING.canTransitionTo(BoxState.ACTIVE));
		assertTrue(BoxState.ACTIVE.canTransitionTo(BoxState.RESETTING));
		assertTrue(BoxState.RESETTING.canTransitionTo(BoxState.ACTIVE));
		assertTrue(BoxState.RESETTING.canTransitionTo(BoxState.RECOVERY_REQUIRED));
		assertTrue(BoxState.ARCHIVED.canTransitionTo(BoxState.DESTROY_PENDING));
		assertTrue(BoxState.DESTROY_PENDING.canTransitionTo(BoxState.DESTROYED));
	}

	@Test
	void resettingCannotArchiveOrDestroyDirectly() {
		assertFalse(BoxState.RESETTING.canTransitionTo(BoxState.ARCHIVING));
		assertFalse(BoxState.RESETTING.canTransitionTo(BoxState.DESTROY_PENDING));
	}

	@Test
	void archivedCannotBecomeActiveWithoutRestoring() {
		assertFalse(BoxState.ARCHIVED.canTransitionTo(BoxState.ACTIVE));
	}

	@Test
	void activeCannotBeDestroyedDirectly() {
		assertFalse(BoxState.ACTIVE.canTransitionTo(BoxState.DESTROY_PENDING));
	}

	@Test
	void destroyedStateIsTerminal() {
		for (BoxState target : BoxState.values()) {
			assertFalse(BoxState.DESTROYED.canTransitionTo(target));
		}
	}

	@Test
	void instanceRejectsIllegalTransition() {
		BoxInstance instance = BoxInstance.create(UUID.randomUUID(), "Test Box");
		assertThrows(IllegalArgumentException.class, () -> instance.withState(BoxState.ARCHIVED, 2));
	}

	@Test
	void instanceRejectsBlankNames() {
		assertThrows(IllegalArgumentException.class, () -> BoxInstance.create(UUID.randomUUID(), "   "));
	}
}
