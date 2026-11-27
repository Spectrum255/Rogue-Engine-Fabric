package spectrum255.rogueengine.map;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

public final class ScenePlacementService {
	private final Queue<Task<?>> tasks = new ArrayDeque<>();

	public ScenePlacementService() {
		this(true);
	}

	ScenePlacementService(boolean registerTick) {
		if (registerTick) {
			ServerTickEvents.END_SERVER_TICK.register(server -> tick());
		}
	}

	public <T> CompletableFuture<T> submit(Supplier<T> action, MapPool.PlacementBudget budget) {
		Objects.requireNonNull(action, "action");
		Objects.requireNonNull(budget, "budget");
		if (!budget.valid()) {
			throw new IllegalArgumentException("Scene placement budget is invalid");
		}
		CompletableFuture<T> future = new CompletableFuture<>();
		tasks.add(new Task<>(action, budget, future));
		return future;
	}

	public CompletableFuture<StructureScenePlacer.PlacementResult> place(ServerLevel level, Path source,
			MapScene scene, BlockPos origin, MapPool.PlacementBudget budget) {
		Objects.requireNonNull(level, "level");
		return submit(() -> {
			if (!level.getServer().isSameThread()) {
				throw new IllegalStateException("Scene placement must run on the server thread");
			}
			try {
				return StructureScenePlacer.place(level, source, scene, origin);
			} catch (IOException exception) {
				throw new UncheckedIOException(exception);
			}
		}, budget);
	}

	void tickForTest() {
		tick();
	}

	private void tick() {
		long startedAt = System.nanoTime();
		int processed = 0;
		while (!tasks.isEmpty() && processed < tasks.peek().budget().maxChunksPerTick()) {
			long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000L;
			if (elapsedMillis >= tasks.peek().budget().maxTickMs()) {
				break;
			}
			Task<?> task = tasks.remove();
			processed++;
			complete(task);
		}
	}

	private <T> void complete(Task<T> task) {
		try {
			task.future().complete(task.action().get());
		} catch (Throwable throwable) {
			task.future().completeExceptionally(throwable);
		}
	}

	private record Task<T>(Supplier<T> action, MapPool.PlacementBudget budget, CompletableFuture<T> future) {
	}
}
