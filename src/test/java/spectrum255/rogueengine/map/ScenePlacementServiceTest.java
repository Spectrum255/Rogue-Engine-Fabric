package spectrum255.rogueengine.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class ScenePlacementServiceTest {
	@Test
	void processesTasksAcrossTicksWithinBudget() {
		ScenePlacementService service = new ScenePlacementService(false);
		AtomicInteger counter = new AtomicInteger();
		CompletableFuture<Integer> first = service.submit(counter::incrementAndGet, budget(1));
		CompletableFuture<Integer> second = service.submit(counter::incrementAndGet, budget(1));

		service.tickForTest();
		assertTrue(first.isDone());
		assertFalse(second.isDone());
		assertEquals(1, counter.get());

		service.tickForTest();
		assertTrue(second.isDone());
		assertEquals(2, counter.get());
	}

	@Test
	void taskFailureCompletesExceptionally() {
		ScenePlacementService service = new ScenePlacementService(false);
		CompletableFuture<Integer> future = service.submit(() -> {
			throw new IllegalStateException("failed");
		}, budget(1));

		service.tickForTest();

		assertTrue(future.isCompletedExceptionally());
		CompletionException exception = assertThrows(CompletionException.class, future::join);
		assertEquals(IllegalStateException.class, exception.getCause().getClass());
	}

	private MapPool.PlacementBudget budget(int tasksPerTick) {
		return new MapPool.PlacementBudget(tasksPerTick, 5, 128, 256);
	}
}
