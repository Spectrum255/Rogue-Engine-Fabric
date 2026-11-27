package spectrum255.rogueengine.instance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class BoxManagerRestoreTest {
	@Test
	void restoresPersistedSnapshots() {
		BoxInstance first = BoxInstance.create(UUID.randomUUID(), "First").withState(BoxState.ACTIVE, 2);
		BoxInstance second = BoxInstance.create(UUID.randomUUID(), "Second")
						.withState(BoxState.ACTIVE, 2)
						.withState(BoxState.ARCHIVING, 3)
						.withState(BoxState.ARCHIVED, 4);
		BoxManager manager = new BoxManager(List.of(first, second));

		assertEquals(first, manager.get(first.id()).orElseThrow());
		assertEquals(second, manager.get(second.id()).orElseThrow());
	}

	@Test
	void duplicateRestoreIsRejected() {
		BoxInstance instance = BoxInstance.create(UUID.randomUUID(), "Box");
		BoxManager manager = new BoxManager(List.of(instance));

		assertThrows(IllegalArgumentException.class, () -> manager.restore(instance));
	}
}
