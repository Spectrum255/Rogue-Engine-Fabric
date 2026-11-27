package spectrum255.rogueengine.instance;

import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;

import net.minecraft.server.level.ServerPlayer;

import spectrum255.rogueengine.RogueEngineFabric;
import spectrum255.rogueengine.mixin.MinecraftServerAccessor;
import spectrum255.rogueengine.persistence.DirectoryHash;
import spectrum255.rogueengine.persistence.InstanceJournal;
import spectrum255.rogueengine.persistence.QuarantineService;
import spectrum255.rogueengine.runtime.ServerRuntime;

public final class BoxDestroyService {
	private final ServerRuntime runtime;

	public BoxDestroyService(ServerRuntime runtime) {
		this.runtime = runtime;
	}

	public PrepareResult prepare(ServerPlayer owner, UUID id) {
		if (runtime.isReloading() || runtime.isStopping()) {
			return new Rejected("The engine is reloading or stopping");
		}
		BoxInstance instance = runtime.boxes().get(id).orElse(null);
		if (instance == null || instance.state() != BoxState.ARCHIVED) {
			return new Rejected("Only archived boxes can be destroyed: " + id);
		}
		try {
			Path source = dimensionPath(instance);
			String hash = DirectoryHash.of(source);
			String token = runtime.confirmations().prepare(owner, id, instance.stateVersion(), hash);
			return new Prepared(token, hash, source);
		} catch (Exception exception) {
			return new Rejected("Could not prepare destroy: " + exception.getMessage());
		}
	}

	public LifecycleResult confirm(ServerPlayer owner, UUID id, String token) {
		if (runtime.isReloading() || runtime.isStopping()) {
			return new Rejected("The engine is reloading or stopping");
		}
		BoxInstance instance = runtime.boxes().get(id).orElse(null);
		if (instance == null || instance.state() != BoxState.ARCHIVED) {
			return new Rejected("Only archived boxes can be destroyed: " + id);
		}
		try {
			Path source = dimensionPath(instance);
			String hash = DirectoryHash.of(source);
			if (!runtime.confirmations().confirm(owner, id, instance.stateVersion(), hash, token)) {
				return new Rejected("Destroy confirmation is invalid, expired, or already used");
			}
			UUID operationId = UUID.randomUUID();
			transition(instance, BoxState.DESTROY_PENDING, operationId, "COMPLETE_DESTROY");
			BoxInstance pending = runtime.boxes().get(id).orElseThrow();
			runtime.journal().appendIntent(new InstanceJournal.WalRecord(operationId, id, BoxState.DESTROY_PENDING,
					BoxState.DESTROYED, pending.stateVersion(), pending.stateVersion() + 1, "COMPLETE_DESTROY",
					System.currentTimeMillis()));
			runtime.quarantine().quarantine(operationId, source, id, hash);
			runtime.quarantine().quarantineInventories(operationId, id, runtime.inventories().storeRoot());
			runtime.quarantine().quarantineScenes(operationId, id, runtime.sceneCache().root().resolve("scenes"));
			BoxInstance destroyed = pending.withState(BoxState.DESTROYED, pending.stateVersion() + 1);
			runtime.journal().commit(destroyed);
			var transitioned = runtime.boxes().transition(id, BoxState.DESTROYED, pending.stateVersion());
			if (transitioned instanceof BoxManager.Rejected rejected) {
				throw new IllegalStateException(rejected.reason());
			}
			runtime.metrics().recordDestroy();
			return new Destroyed();
		} catch (Exception exception) {
			if (runtime.boxes().get(id).map(BoxInstance::state).orElse(null) == BoxState.DESTROY_PENDING) {
				return new Rejected("Destroy is pending recovery: " + exception.getMessage());
			}
			markRecoveryRequired(id, exception.getMessage());
			return new Rejected("Destroy failed: " + exception.getMessage());
		}
}

	public void cancel(ServerPlayer owner, UUID id) {
		runtime.confirmations().cancel(owner, id);
	}

	private Path dimensionPath(BoxInstance instance) throws IOException {
		Path path = ((MinecraftServerAccessor) runtime.server()).getStorageSource().getDimensionPath(instance.dimensionKey());
		var worldRoot = ((MinecraftServerAccessor) runtime.server()).getStorageSource()
				.getLevelPath(net.minecraft.world.level.storage.LevelResource.ROOT);
		spectrum255.rogueengine.persistence.AtomicFiles.verifyManagedPath(worldRoot, path);
		return path;
	}

	private void transition(BoxInstance current, BoxState target, UUID operationId, String action) throws IOException {
		long nextVersion = current.stateVersion() + 1;
		runtime.journal().appendIntent(new InstanceJournal.WalRecord(operationId, current.id(), current.state(), target,
				current.stateVersion(), nextVersion, action, System.currentTimeMillis()));
		runtime.journal().commit(current.withState(target, nextVersion));
		var result = runtime.boxes().transition(current.id(), target, current.stateVersion());
		if (result instanceof BoxManager.Rejected rejected) {
			throw new IllegalStateException(rejected.reason());
		}
	}

	private void markRecoveryRequired(UUID id, String reason) {
		try {
			BoxInstance current = runtime.boxes().get(id).orElseThrow();
			BoxInstance recovery = current.withState(BoxState.RECOVERY_REQUIRED, current.stateVersion() + 1);
			runtime.journal().commit(recovery);
			runtime.boxes().transition(id, BoxState.RECOVERY_REQUIRED, current.stateVersion());
		} catch (Exception exception) {
			RogueEngineFabric.LOGGER.error("Failed to mark destroyed box {} for recovery: {}", id, reason, exception);
		}
	}

	public sealed interface PrepareResult permits Prepared, Rejected {
	}

	public record Prepared(String token, String contentHash, Path source) implements PrepareResult {
	}

	public record Rejected(String reason) implements PrepareResult, LifecycleResult {
	}

	public sealed interface LifecycleResult permits Destroyed, Rejected {
	}

	public record Destroyed() implements LifecycleResult {
	}
}
