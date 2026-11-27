package spectrum255.rogueengine.instance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class BoxManagerTest {
	@Test
	void createStoresOneImmutableSnapshot() {
		BoxManager manager = new BoxManager();
		UUID id = UUID.randomUUID();

		BoxManager.CreateResult result = manager.create(id, "Example");

		assertInstanceOf(BoxManager.Created.class, result);
		assertEquals(1, manager.all().size());
		assertEquals("Example", manager.get(id).orElseThrow().name());
	}

	@Test
	void duplicateUuidIsRejected() {
		BoxManager manager = new BoxManager();
		UUID id = UUID.randomUUID();
		manager.create(id, "First");

		BoxManager.CreateResult result = manager.create(id, "Second");

		assertInstanceOf(BoxManager.Rejected.class, result);
		assertEquals("First", manager.get(id).orElseThrow().name());
	}

	@Test
	void transitionChecksExpectedVersion() {
		BoxManager manager = new BoxManager();
		UUID id = UUID.randomUUID();
		manager.create(id, "Example");

		BoxManager.TransitionResult result = manager.transition(id, BoxState.ACTIVE, 99);

		assertInstanceOf(BoxManager.Rejected.class, result);
		assertEquals(BoxState.CREATING, manager.get(id).orElseThrow().state());
	}

	@Test
	void legalTransitionReplacesSnapshot() {
		BoxManager manager = new BoxManager();
		UUID id = UUID.randomUUID();
		manager.create(id, "Example");

		BoxManager.TransitionResult result = manager.transition(id, BoxState.ACTIVE, 1);

		assertInstanceOf(BoxManager.Updated.class, result);
		BoxInstance updated = manager.get(id).orElseThrow();
		assertEquals(BoxState.ACTIVE, updated.state());
		assertEquals(2, updated.stateVersion());
	}

	@Test
	void unknownInstanceIsRejected() {
		BoxManager manager = new BoxManager();
		BoxManager.TransitionResult result = manager.transition(UUID.randomUUID(), BoxState.ACTIVE, 1);

		assertInstanceOf(BoxManager.Rejected.class, result);
		assertTrue(manager.all().isEmpty());
	}
}
