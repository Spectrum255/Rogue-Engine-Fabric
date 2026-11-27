package spectrum255.rogueengine.dimension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import spectrum255.rogueengine.profile.EngineSettings;
import spectrum255.rogueengine.runtime.PerformanceBudget;
import spectrum255.rogueengine.runtime.PerformanceMetrics;

public final class ChunkPreparationService {
	private final Supplier<EngineSettings> settings;
	private final PerformanceMetrics metrics;
	private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();
	private final Map<TicketKey, Integer> ticketReferences = new ConcurrentHashMap<>();

	public ChunkPreparationService(Supplier<EngineSettings> settings) {
		this(settings, new PerformanceMetrics());
	}

	public ChunkPreparationService(Supplier<EngineSettings> settings, PerformanceMetrics metrics) {
		this.settings = settings;
		this.metrics = metrics;
		ServerTickEvents.END_SERVER_TICK.register(server -> tick());
	}

	public void prepare(ServerPlayer player, ServerLevel level, BlockPos target, int radius, Consumer<BlockPos> completion) {
		prepare(player, level, target, radius, true, completion);
	}

	public void prepare(ServerPlayer player, ServerLevel level, BlockPos target, int radius, boolean searchSafeSpawn,
			Consumer<BlockPos> completion) {
		if (pending.containsKey(player.getUUID())) {
			throw new IllegalStateException("A teleport is already being prepared for " + player.getUUID());
		}
		ChunkPos center = new ChunkPos(target.getX() >> 4, target.getZ() >> 4);
		EngineSettings current = settings.get();
		Pending task = new Pending(player, level, target, radius, completion,
				current.preloadChunksPerTick(), current.preloadTimeoutSeconds(), current.preloadTickBudgetMs());
		task.searching = searchSafeSpawn;
		task.positions = searchSafeSpawn ? List.of(center) : preloadPositions(center, radius);
		acquireTicket(level, center, Math.max(1, radius));
		pending.put(player.getUUID(), task);
	}

	public boolean hasPending(ServerLevel level) {
		return pending.values().stream().anyMatch(task -> task.level == level);
	}

	private void tick() {
		for (Pending task : List.copyOf(pending.values())) {
			try {
				if (task.player.isRemoved() || task.player.level().getServer() == null
						|| task.level.getServer() == null || task.level.getServer().getLevel(task.level.dimension()) != task.level) {
					throw new IllegalStateException("Target player or dimension is no longer available");
				}
				if (System.nanoTime() - task.startedAt > task.timeoutSeconds * 1_000_000_000L) {
					throw new IllegalStateException("Spawn chunk preparation timed out");
				}
				int started = 0;
				PerformanceBudget tickBudget = new PerformanceBudget(task.tickBudgetMs, System::nanoTime);
				while (started < task.chunksPerTick && task.index < task.positions.size() && tickBudget.allowsMore()) {
					if (task.currentFuture == null) {
						ChunkPos chunk = task.positions.get(task.index);
						task.currentFuture = task.level.getChunkSource().getChunkFuture(chunk.x(), chunk.z(), ChunkStatus.FULL, false);
						started++;
					}
					if (!task.currentFuture.isDone()) {
						break;
					}
					ChunkResult<ChunkAccess> result = task.currentFuture.join();
					if (!result.isSuccess()) {
						throw new IllegalStateException("Chunk loading failed: " + result.getError());
					}
					task.currentFuture = null;
					if (task.searching) {
						task.target = findSafeSpawn(task.level, task.positions.get(task.index));
						task.searching = false;
						task.positions = preloadPositions(new ChunkPos(task.target.getX() >> 4, task.target.getZ() >> 4), task.radius);
						task.index = 0;
					} else {
						task.index++;
					}
				}
				if (!task.searching && task.index >= task.positions.size()) {
					pending.remove(task.player.getUUID());
					releaseTicket(task);
					metrics.recordPreparation(System.nanoTime() - task.startedAt, true);
					task.completion.accept(task.target);
				}
			} catch (Exception exception) {
				pending.remove(task.player.getUUID());
				releaseTicket(task);
				metrics.recordPreparation(System.nanoTime() - task.startedAt, false);
				task.player.sendSystemMessage(Component.literal("Teleport preparation failed: " + exception.getMessage()));
			}
		}
	}

	private List<ChunkPos> preloadPositions(ChunkPos center, int radius) {
		List<ChunkPos> positions = new ArrayList<>();
		for (int x = -radius; x <= radius; x++) {
			for (int z = -radius; z <= radius; z++) {
				positions.add(new ChunkPos(center.x() + x, center.z() + z));
			}
		}
		return List.copyOf(positions);
	}

	private BlockPos findSafeSpawn(ServerLevel level, ChunkPos chunk) {
		int minY = level.getMinY();
		int maxY = level.getMaxY() + minY;
		int startY = Math.max(minY + 1, Math.min(maxY - 3, level.getChunkSource().getGenerator().getSpawnHeight(level)));
		for (int y = startY; y < maxY - 1; y++) {
			BlockPos pos = new BlockPos(chunk.getMiddleBlockX(), y, chunk.getMiddleBlockZ());
			if (isSafe(level, pos)) {
				return pos;
			}
		}
		for (int y = minY + 1; y < startY; y++) {
			BlockPos pos = new BlockPos(chunk.getMiddleBlockX(), y, chunk.getMiddleBlockZ());
			if (isSafe(level, pos)) {
				return pos;
			}
		}
		throw new IllegalStateException("No safe spawn found in chunk " + chunk);
	}

	private boolean isSafe(ServerLevel level, BlockPos pos) {
		BlockState ground = level.getBlockState(pos.below());
		BlockState feet = level.getBlockState(pos);
		BlockState head = level.getBlockState(pos.above());
		return !ground.getCollisionShape(level, pos.below()).isEmpty()
				&& feet.getCollisionShape(level, pos).isEmpty()
				&& head.getCollisionShape(level, pos.above()).isEmpty()
				&& !feet.is(Blocks.WATER)
				&& !feet.is(Blocks.LAVA);
	}

	private void acquireTicket(ServerLevel level, ChunkPos center, int radius) {
		TicketKey key = new TicketKey(level.dimension(), center, radius);
		if (ticketReferences.merge(key, 1, Integer::sum) == 1) {
			level.getChunkSource().addTicketWithRadius(TicketType.PLAYER_LOADING, center, radius);
		}
	}

	private void releaseTicket(Pending task) {
		int radius = Math.max(1, task.radius);
		TicketKey key = new TicketKey(task.level.dimension(), task.center, radius);
		Integer remaining = ticketReferences.computeIfPresent(key, (ignored, count) -> count <= 1 ? null : count - 1);
		if (remaining == null) {
			task.level.getChunkSource().removeTicketWithRadius(TicketType.PLAYER_LOADING, task.center, radius);
		}
	}

	private record TicketKey(ResourceKey<Level> dimension, ChunkPos center, int radius) {
	}

	private static final class Pending {
		private final ServerPlayer player;
		private final ServerLevel level;
		private final ChunkPos center;
		private final int radius;
		private final Consumer<BlockPos> completion;
		private final int chunksPerTick;
		private final int timeoutSeconds;
		private final int tickBudgetMs;
		private final long startedAt = System.nanoTime();
		private BlockPos target;
		private List<ChunkPos> positions;
		private CompletableFuture<ChunkResult<ChunkAccess>> currentFuture;
		private int index;
		private boolean searching;

		private Pending(ServerPlayer player, ServerLevel level, BlockPos target, int radius, Consumer<BlockPos> completion,
				int chunksPerTick, int timeoutSeconds, int tickBudgetMs) {
			this.player = player;
			this.level = level;
			this.center = new ChunkPos(target.getX() >> 4, target.getZ() >> 4);
			this.target = target;
			this.radius = radius;
			this.completion = completion;
			this.chunksPerTick = chunksPerTick;
			this.timeoutSeconds = timeoutSeconds;
			this.tickBudgetMs = tickBudgetMs;
		}
	}
}
