package spectrum255.rogueengine.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import org.junit.jupiter.api.Test;

class SnapshotPlacementServiceTest {
	@Test
	void budgetedQueueCompletesAndPropagatesFailure() {
		SnapshotPlacementService service = new SnapshotPlacementService(false);
		CompletableFuture<String> ok = service.submit(() -> "ok", budget(1));
		service.tickForTest();
		assertEquals("ok", ok.join());

		CompletableFuture<String> failed = service.submit(() -> {
			throw new IllegalStateException("boom");
		}, budget(1));
		service.tickForTest();
		CompletionException error = assertThrows(CompletionException.class, failed::join);
		assertInstanceOf(IllegalStateException.class, error.getCause());
	}

	private MapPool.PlacementBudget budget(int chunks) {
		return new MapPool.PlacementBudget(chunks, 5, 128, 256);
	}
}
