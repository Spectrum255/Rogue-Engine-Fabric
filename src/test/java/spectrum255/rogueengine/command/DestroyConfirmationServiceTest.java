package spectrum255.rogueengine.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

class DestroyConfirmationServiceTest {
	@Test
	void tokenIsBoundToOwnerInstanceVersionAndHash() {
		AtomicLong now = new AtomicLong(1000);
		DestroyConfirmationService service = new DestroyConfirmationService(now::get, 100);
		Object owner = new Object();
		UUID id = UUID.randomUUID();
		String token = service.prepare(owner, id, 4, "hash");

		assertTrue(service.confirm(owner, id, 4, "hash", token));
		assertFalse(service.confirm(owner, id, 4, "hash", token));
	}

	@Test
	void wrongOwnerIsRejected() {
		DestroyConfirmationService service = new DestroyConfirmationService(() -> 1000, 100);
		UUID id = UUID.randomUUID();
		String token = service.prepare(new Object(), id, 4, "hash");

		assertFalse(service.confirm(new Object(), id, 4, "hash", token));
	}

	@Test
	void expiredTokenIsRejected() {
		AtomicLong now = new AtomicLong(1000);
		DestroyConfirmationService service = new DestroyConfirmationService(now::get, 100);
		UUID id = UUID.randomUUID();
		String token = service.prepare(new Object(), id, 4, "hash");
		now.set(1101);

		assertFalse(service.confirm(new Object(), id, 4, "hash", token));
	}

	@Test
	void cancelInvalidatesToken() {
		DestroyConfirmationService service = new DestroyConfirmationService(() -> 1000, 100);
		Object owner = new Object();
		UUID id = UUID.randomUUID();
		String token = service.prepare(owner, id, 4, "hash");
		service.cancel(owner, id);

		assertFalse(service.confirm(owner, id, 4, "hash", token));
		assertEquals(0, service.pendingCount());
	}
}
