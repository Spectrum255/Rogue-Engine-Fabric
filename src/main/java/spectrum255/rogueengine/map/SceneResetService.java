package spectrum255.rogueengine.map;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import spectrum255.rogueengine.RogueEngineFabric;
import spectrum255.rogueengine.instance.BoxDefinition;
import spectrum255.rogueengine.instance.BoxInstance;
import spectrum255.rogueengine.instance.BoxManager;
import spectrum255.rogueengine.instance.BoxState;
import spectrum255.rogueengine.persistence.AtomicFiles;
import spectrum255.rogueengine.persistence.InstanceJournal;
import spectrum255.rogueengine.runtime.ServerRuntime;

public final class SceneResetService {
	private static final int MAX_CLEAR_VOLUME = 1_000_000;
	private static final MapPool.PlacementBudget RESET_BUDGET = new MapPool.PlacementBudget(4, 5, 128, 256);

	private final ServerRuntime runtime;

	public SceneResetService(ServerRuntime runtime) {
		this.runtime = runtime;
	}

	public ResetResult reset(UUID id, MapScene.RefreshTrigger trigger) {
		return request(id, trigger);
	}

	public ResetResult request(UUID id, MapScene.RefreshTrigger trigger) {
		if (runtime.isReloading() || runtime.isStopping()) {
			return new Rejected("The engine is reloading or stopping");
		}
		BoxInstance instance = runtime.boxes().get(id).orElse(null);
		if (instance == null || instance.state() != BoxState.ACTIVE) {
			return new Rejected("Box is not active: " + id);
		}
		BoxDefinition definition = runtime.journal().definition(id).orElse(null);
		if (definition == null || definition.sceneSnapshot() == null) {
			return new Rejected("Box is not a map scene: " + id);
		}
		ServerLevel level = runtime.dimensions().get(instance.dimensionKey());
		if (level == null) {
			return new Rejected("Box dimension is not loaded: " + id);
		}
		Decision decision = decide(definition.sceneSnapshot().refresh(), trigger, !level.players().isEmpty());
		if (decision == Decision.REJECT) {
			return new Rejected("Reset trigger is not allowed or players are still inside");
		}
		if (decision == Decision.PENDING) {
			try {
				writePending(definition.sceneSnapshot(), trigger);
				return new Pending(id);
			} catch (IOException exception) {
				return new Rejected("Could not record pending reset: " + exception.getMessage());
			}
		}
		return beginReset(instance, level, definition);
	}

	public void onPlayerLeave(ServerPlayer player) {
		for (BoxInstance instance : runtime.boxes().all()) {
			if (!instance.dimensionKey().equals(player.level().dimension()) || instance.state() != BoxState.ACTIVE) {
				continue;
			}
			ServerLevel level = runtime.dimensions().get(instance.dimensionKey());
			if (level == null || !level.players().isEmpty()) {
				continue;
			}
			BoxDefinition definition = runtime.journal().definition(instance.id()).orElse(null);
			if (definition == null || definition.sceneSnapshot() == null) {
				continue;
			}
			boolean pending = hasPending(definition.sceneSnapshot());
			if (pending || definition.sceneSnapshot().refresh().triggers().contains(MapScene.RefreshTrigger.ON_EMPTY)) {
				beginReset(instance, level, definition);
			}
		}
	}

	public void onDeath(ServerPlayer player) {
		instanceFor(player.level().dimension()).ifPresent(instance -> request(instance.id(), MapScene.RefreshTrigger.ON_DEATH));
	}

	public void completed(UUID id) {
		request(id, MapScene.RefreshTrigger.ON_COMPLETED);
	}

	public void recover(UUID id) {
		BoxInstance instance = runtime.boxes().get(id).orElse(null);
		if (instance == null || instance.state() != BoxState.RESETTING) {
			return;
		}
		BoxDefinition definition = runtime.journal().definition(id).orElse(null);
		ServerLevel level = runtime.dimensions().get(instance.dimensionKey());
		if (definition != null && level != null) {
			beginReset(instance, level, definition);
		}
	}

	public static Decision decide(MapScene.RefreshPolicy policy, MapScene.RefreshTrigger trigger, boolean playersOnline) {
		Objects.requireNonNull(policy, "policy");
		Objects.requireNonNull(trigger, "trigger");
		if (!policy.triggers().contains(trigger)) {
			return Decision.REJECT;
		}
		if (!playersOnline) {
			return Decision.RESET;
		}
		return trigger == MapScene.RefreshTrigger.ON_DEATH || trigger == MapScene.RefreshTrigger.ON_COMPLETED
				? Decision.PENDING : Decision.REJECT;
	}

	private ResetResult beginReset(BoxInstance instance, ServerLevel level, BoxDefinition definition) {
		SceneSelectionSnapshot snapshot = definition.sceneSnapshot();
		if (snapshot == null) {
			return new Rejected("Box has no scene snapshot: " + instance.id());
		}
		long volume = (long) (snapshot.bounds().max().getX() - snapshot.bounds().min().getX() + 1)
				* (snapshot.bounds().max().getY() - snapshot.bounds().min().getY() + 1)
				* (snapshot.bounds().max().getZ() - snapshot.bounds().min().getZ() + 1);
		if (volume > MAX_CLEAR_VOLUME) {
			return new Rejected("Scene reset area is too large: " + volume);
		}
		BoxInstance resetting;
		try {
			if (instance.state() == BoxState.ACTIVE) {
				long nextVersion = instance.stateVersion() + 1;
				runtime.journal().appendIntent(new InstanceJournal.WalRecord(UUID.randomUUID(), instance.id(),
						BoxState.ACTIVE, BoxState.RESETTING, instance.stateVersion(), nextVersion,
						"COMPLETE_RESET", System.currentTimeMillis()));
				runtime.journal().commit(instance.withState(BoxState.RESETTING, nextVersion));
				var transitioned = runtime.boxes().transition(instance.id(), BoxState.RESETTING, instance.stateVersion());
				if (transitioned instanceof BoxManager.Rejected rejected) {
					throw new IllegalStateException(rejected.reason());
				}
				resetting = runtime.boxes().get(instance.id()).orElseThrow();
			} else {
				resetting = instance;
			}
			clearPending(snapshot);
			clearScene(level, snapshot);
		} catch (Exception exception) {
			markRecoveryRequired(instance, exception.getMessage());
			return new Rejected("Reset failed: " + exception.getMessage());
		}

		MapScene.Source source = new MapScene.Source(snapshot.sourceKind(), snapshot.sourceId());
		MapScene scene = new MapScene(snapshot.sourceKind() == MapScene.SourceKind.SNAPSHOT ? 2 : 1, source,
				snapshot.bounds(), snapshot.anchor(), snapshot.spawn(), snapshot.rotation(), snapshot.mirror(),
				snapshot.processorIds(), snapshot.refresh());
		CompletableFuture<StructureScenePlacer.PlacementResult> future;
		try {
			Path sourcePath = runtime.sceneCache().resolve(snapshot.cachePath());
			future = snapshot.sourceKind() == MapScene.SourceKind.SNAPSHOT
					? runtime.snapshotPlacement().place(level, sourcePath, snapshot.sceneHash(), scene,
							snapshot.anchor(), RESET_BUDGET)
					: runtime.scenePlacement().place(level, sourcePath, scene, snapshot.anchor(), RESET_BUDGET);
		} catch (IOException exception) {
			markRecoveryRequired(resetting, exception.getMessage());
			return new Rejected("Reset failed: " + exception.getMessage());
		}
		future.whenComplete((result, error) -> runtime.server().execute(() -> {
			if (error != null) {
				markRecoveryRequired(resetting, error.getMessage());
				return;
			}
			try {
				if (!isSafeSpawn(level, result.spawn())) {
					throw new IllegalStateException("Reset scene spawn is not safe");
				}
				SceneSelectionSnapshot updatedSnapshot = snapshot.withResetCount(snapshot.resetCount() + 1);
				BoxDefinition updatedDefinition = new BoxDefinition(definition.instanceId(), definition.profileId(),
						definition.profileJson(), definition.profileHash(), definition.dependencyHash(),
						definition.levelStemJson(), definition.seed(), updatedSnapshot);
				runtime.journal().updateDefinition(updatedDefinition);
				long nextVersion = resetting.stateVersion() + 1;
				runtime.journal().commit(resetting.withState(BoxState.ACTIVE, nextVersion));
				var transitioned = runtime.boxes().transition(instance.id(), BoxState.ACTIVE, resetting.stateVersion());
				if (transitioned instanceof BoxManager.Rejected rejected) {
					throw new IllegalStateException(rejected.reason());
				}
			} catch (Exception exception) {
				markRecoveryRequired(resetting, exception.getMessage());
			}
		}));
		return new Started(instance.id());
	}

	private void clearScene(ServerLevel level, SceneSelectionSnapshot snapshot) {
		BoundingBox box = new BoundingBox(
				snapshot.bounds().min().getX(), snapshot.bounds().min().getY(), snapshot.bounds().min().getZ(),
				snapshot.bounds().max().getX(), snapshot.bounds().max().getY(), snapshot.bounds().max().getZ());
		for (var chunkPos : box.intersectingChunks().toList()) {
			ChunkAccess chunk = level.getChunk(chunkPos.x(), chunkPos.z(), ChunkStatus.FULL, true);
			for (int y = box.minY(); y <= box.maxY(); y++) {
				for (int x = box.minX(); x <= box.maxX(); x++) {
					for (int z = box.minZ(); z <= box.maxZ(); z++) {
						chunk.setBlockState(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
					}
				}
			}
		}
		List<Entity> entities = new ArrayList<>();
		level.getEntities(EntityTypeTest.forClass(Entity.class), entity -> box.isInside(entity.blockPosition()), entities);
		entities.forEach(Entity::discard);
	}

	private boolean isSafeSpawn(ServerLevel level, BlockPos pos) {
		var ground = level.getBlockState(pos.below()).getCollisionShape(level, pos.below());
		var feet = level.getBlockState(pos).getCollisionShape(level, pos);
		var head = level.getBlockState(pos.above()).getCollisionShape(level, pos.above());
		return !ground.isEmpty() && feet.isEmpty() && head.isEmpty()
				&& !level.getBlockState(pos).is(Blocks.WATER) && !level.getBlockState(pos).is(Blocks.LAVA);
	}

	private java.util.Optional<BoxInstance> instanceFor(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension) {
		return runtime.boxes().all().stream().filter(instance -> instance.dimensionKey().equals(dimension)).findFirst();
	}

	private Path pendingPath(SceneSelectionSnapshot snapshot) throws IOException {
		return runtime.sceneCache().resolve(snapshot.cachePath()).resolveSibling("pending-reset.txt");
	}

	private boolean hasPending(SceneSelectionSnapshot snapshot) {
		try {
			return Files.exists(pendingPath(snapshot));
		} catch (IOException exception) {
			throw new UncheckedIOException(exception);
		}
	}

	private void writePending(SceneSelectionSnapshot snapshot, MapScene.RefreshTrigger trigger) throws IOException {
		AtomicFiles.writeAtomic(pendingPath(snapshot), trigger.getSerializedName().getBytes(StandardCharsets.UTF_8));
	}

	private void clearPending(SceneSelectionSnapshot snapshot) throws IOException {
		Files.deleteIfExists(pendingPath(snapshot));
	}

	private void markRecoveryRequired(BoxInstance instance, String reason) {
		try {
			BoxInstance current = runtime.boxes().get(instance.id()).orElseThrow();
			BoxInstance recovery = current.withState(BoxState.RECOVERY_REQUIRED, current.stateVersion() + 1);
			runtime.journal().commit(recovery);
			var transitioned = runtime.boxes().transition(instance.id(), BoxState.RECOVERY_REQUIRED, current.stateVersion());
			if (transitioned instanceof BoxManager.Rejected rejected) {
				throw new IllegalStateException(rejected.reason());
			}
		} catch (Exception exception) {
			RogueEngineFabric.LOGGER.error("Failed to mark scene reset for recovery: {}", instance.id(), exception);
		}
	}

	public enum Decision {
		RESET,
		PENDING,
		REJECT
	}

	public sealed interface ResetResult permits Started, Pending, Rejected {
	}

	public record Started(UUID id) implements ResetResult {
	}

	public record Pending(UUID id) implements ResetResult {
	}

	public record Rejected(String reason) implements ResetResult {
	}
}
