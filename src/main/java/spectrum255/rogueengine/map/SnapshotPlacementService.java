package spectrum255.rogueengine.map;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

public final class SnapshotPlacementService {
	private static final int MAX_SOURCE_BYTES = 16 * 1024 * 1024;
	private static final int MAX_NBT_BYTES = 64 * 1024 * 1024;
	private static final int MAX_ENTITIES_PER_TICK = 256;
	private final Queue<Task<?>> tasks = new ArrayDeque<>();
	private final Queue<SnapshotJob> jobs = new ArrayDeque<>();

	public SnapshotPlacementService() {
		this(true);
	}

	SnapshotPlacementService(boolean registerTick) {
		if (registerTick) {
			ServerTickEvents.END_SERVER_TICK.register(server -> tick());
		}
	}

	public <T> CompletableFuture<T> submit(Supplier<T> action, MapPool.PlacementBudget budget) {
		Objects.requireNonNull(action, "action");
		Objects.requireNonNull(budget, "budget");
		if (!budget.valid()) {
			throw new IllegalArgumentException("Snapshot placement budget is invalid");
		}
		CompletableFuture<T> future = new CompletableFuture<>();
		tasks.add(new Task<>(action, budget, future));
		return future;
	}

	public CompletableFuture<StructureScenePlacer.PlacementResult> place(ServerLevel level, Path source,
			String expectedHash, MapScene scene, BlockPos origin, MapPool.PlacementBudget budget) throws IOException {
		Objects.requireNonNull(level, "level");
		Objects.requireNonNull(source, "source");
		if (!budget.valid()) {
			throw new IllegalArgumentException("Snapshot placement budget is invalid");
		}
		if (Files.size(source) <= 0 || Files.size(source) > MAX_SOURCE_BYTES) {
			throw new IOException("Snapshot source size is outside the allowed range");
		}
		byte[] bytes = Files.readAllBytes(source);
		if (!sha256(bytes).equals(expectedHash)) {
			throw new IOException("Snapshot source hash mismatch");
		}
		CompoundTag tag = NbtIo.readCompressed(new java.io.ByteArrayInputStream(bytes),
				NbtAccounter.create(MAX_NBT_BYTES));
		MapSnapshot snapshot = MapSnapshotCodec.read(tag, level.registryAccess());
		if (snapshot.blockEntities().size() > budget.maxBlockEntities()
				|| snapshot.entities().size() > budget.maxEntities()) {
			throw new IllegalArgumentException("Snapshot exceeds the placement entity budget");
		}
		SnapshotScenePlacer.Prepared prepared = SnapshotScenePlacer.prepare(snapshot, scene, origin);
		CompletableFuture<StructureScenePlacer.PlacementResult> future = new CompletableFuture<>();
		jobs.add(new SnapshotJob(level, prepared, scene.spawn(), budget, future,
				prepared.bounds().intersectingChunks().toList().iterator()));
		return future;
	}

	void tickForTest() {
		tick();
	}

	private void tick() {
		long startedAt = System.nanoTime();
		int processed = 0;
		while (!tasks.isEmpty() || !jobs.isEmpty()) {
			MapPool.PlacementBudget budget = !tasks.isEmpty()
					? tasks.peek().budget() : jobs.peek().budget();
			long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000L;
			if (elapsedMillis >= budget.maxTickMs()) {
				break;
			}
			if (!tasks.isEmpty()) {
				if (processed >= budget.maxChunksPerTick()) {
					break;
				}
				Task<?> task = tasks.remove();
				processed++;
				complete(task);
				continue;
			}
			SnapshotJob job = jobs.peek();
			try {
				if (job.chunks.hasNext()) {
					if (processed >= job.budget().maxChunksPerTick()) {
						break;
					}
					SnapshotScenePlacer.placeChunk(job.level, job.prepared, job.chunks.next());
					processed++;
					continue;
				}
				int entities = Math.min(MAX_ENTITIES_PER_TICK, job.prepared.entities().size() - job.entityIndex);
				for (int i = 0; i < entities; i++) {
					SnapshotScenePlacer.spawnEntity(job.level, job.prepared.entities().get(job.entityIndex++));
					if ((System.nanoTime() - startedAt) / 1_000_000L >= job.budget().maxTickMs()) {
						break;
					}
				}
				if (job.entityIndex >= job.prepared.entities().size()) {
					jobs.remove();
					job.future.complete(new StructureScenePlacer.PlacementResult(job.prepared.bounds(), job.spawn));
				}
			} catch (Throwable throwable) {
				jobs.remove();
				job.future.completeExceptionally(throwable);
			}
		}
	}

	private <T> void complete(Task<T> task) {
		try {
			task.future.complete(task.action().get());
		} catch (Throwable throwable) {
			task.future.completeExceptionally(throwable);
		}
	}

	private static String sha256(byte[] bytes) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is unavailable", exception);
		}
	}

	private record Task<T>(Supplier<T> action, MapPool.PlacementBudget budget, CompletableFuture<T> future) {
	}

	private static final class SnapshotJob {
		private final ServerLevel level;
		private final SnapshotScenePlacer.Prepared prepared;
		private final BlockPos spawn;
		private final MapPool.PlacementBudget budget;
		private final CompletableFuture<StructureScenePlacer.PlacementResult> future;
		private final Iterator<ChunkPos> chunks;
		private int entityIndex;

		private MapPool.PlacementBudget budget() {
			return budget;
		}

		private SnapshotJob(ServerLevel level, SnapshotScenePlacer.Prepared prepared, BlockPos spawn,
				MapPool.PlacementBudget budget, CompletableFuture<StructureScenePlacer.PlacementResult> future,
				Iterator<ChunkPos> chunks) {
			this.level = level;
			this.prepared = prepared;
			this.spawn = spawn;
			this.budget = budget;
			this.future = future;
			this.chunks = chunks;
		}
	}
}
