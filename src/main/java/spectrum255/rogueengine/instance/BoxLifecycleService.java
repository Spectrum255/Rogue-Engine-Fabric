package spectrum255.rogueengine.instance;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

import spectrum255.rogueengine.RogueEngineFabric;
import spectrum255.rogueengine.mixin.MinecraftServerAccessor;
import spectrum255.rogueengine.persistence.InstanceJournal;
import spectrum255.rogueengine.persistence.PlayerReferenceScanner;
import spectrum255.rogueengine.runtime.ServerRuntime;

public final class BoxLifecycleService {
	private final ServerRuntime runtime;

	public BoxLifecycleService(ServerRuntime runtime) {
		this.runtime = runtime;
	}

	public LifecycleResult archive(UUID id) {
		if (runtime.isReloading() || runtime.isStopping()) {
			return new Rejected("The engine is reloading or stopping");
		}
		BoxInstance instance = runtime.boxes().get(id).orElse(null);
		if (instance == null || instance.state() != BoxState.ACTIVE) {
			return new Rejected("Box is not active: " + id);
		}
		ServerLevel level = runtime.dimensions().get(instance.dimensionKey());
		if (level == null) {
			return new Rejected("Box dimension is not loaded: " + id);
		}
		if (!level.players().isEmpty()) {
			return new Rejected("Box still has online players");
		}
		if (runtime.preparation().hasPending(level)) {
			return new Rejected("A teleport is still being prepared");
		}
		try {
			runtime.server().getPlayerList().saveAll();
			var storage = ((MinecraftServerAccessor) runtime.server()).getStorageSource();
			Set<UUID> onlinePlayers = runtime.server().getPlayerList().getPlayers().stream()
					.map(ServerPlayer::getUUID).collect(Collectors.toSet());
			if (PlayerReferenceScanner.isReferenced(storage.getLevelPath(LevelResource.PLAYER_DATA_DIR),
					instance.dimensionKey(), onlinePlayers)) {
				return new Rejected("An offline player is still located in this box");
			}
		} catch (IOException exception) {
			return new Rejected("Could not verify offline player locations: " + exception.getMessage());
		}

		try {
			transitionJournalAndMemory(instance, BoxState.ARCHIVING, "COMPLETE_ARCHIVE");
			runtime.dimensions().unload(level);
			BoxInstance archiving = runtime.boxes().get(id).orElseThrow();
			transitionJournalAndMemory(archiving, BoxState.ARCHIVED, "COMPLETE_ARCHIVE");
			runtime.metrics().recordArchive();
			return new Success();
		} catch (Exception exception) {
			markRecoveryRequired(id, exception.getMessage());
			return new Rejected("Archive failed: " + exception.getMessage());
		}
	}

	public LifecycleResult restore(UUID id) {
		if (runtime.isReloading() || runtime.isStopping()) {
			return new Rejected("The engine is reloading or stopping");
		}
		BoxInstance instance = runtime.boxes().get(id).orElse(null);
		if (instance == null || instance.state() != BoxState.ARCHIVED) {
			return new Rejected("Box is not archived: " + id);
		}
		var definition = runtime.journal().definition(id).orElse(null);
		if (definition == null) {
			return new Rejected("Box definition is missing: " + id);
		}
		try {
			transitionJournalAndMemory(instance, BoxState.RESTORING, "COMPLETE_RESTORE");
			runtime.dimensions().load(definition);
			BoxInstance restoring = runtime.boxes().get(id).orElseThrow();
			transitionJournalAndMemory(restoring, BoxState.ACTIVE, "COMPLETE_RESTORE");
			runtime.metrics().recordRestore();
			return new Success();
		} catch (Exception exception) {
			markRecoveryRequired(id, exception.getMessage());
			return new Rejected("Restore failed: " + exception.getMessage());
		}
	}

	private void transitionJournalAndMemory(BoxInstance current, BoxState target, String action) throws IOException {
		long nextVersion = current.stateVersion() + 1;
		runtime.journal().appendIntent(new InstanceJournal.WalRecord(UUID.randomUUID(), current.id(), current.state(), target,
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
			var result = runtime.boxes().transition(id, BoxState.RECOVERY_REQUIRED, current.stateVersion());
			if (result instanceof BoxManager.Rejected rejected) {
				RogueEngineFabric.LOGGER.warn("Recovery state mismatch for {}: {}", id, rejected.reason());
			}
		} catch (Exception exception) {
			RogueEngineFabric.LOGGER.error("Failed to mark box {} for recovery: {}", id, reason, exception);
		}
	}

	public sealed interface LifecycleResult permits Success, Rejected {
	}

	public record Success() implements LifecycleResult {
	}

	public record Rejected(String reason) implements LifecycleResult {
	}
}
