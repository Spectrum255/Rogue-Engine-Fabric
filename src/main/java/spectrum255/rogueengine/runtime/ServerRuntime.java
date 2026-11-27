package spectrum255.rogueengine.runtime;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;

import spectrum255.rogueengine.command.DestroyConfirmationService;
import spectrum255.rogueengine.dimension.ChunkPreparationService;
import spectrum255.rogueengine.dimension.DimensionService;
import spectrum255.rogueengine.instance.BoxCreationService;
import spectrum255.rogueengine.instance.BoxManager;
import spectrum255.rogueengine.inventory.PlayerInventoryService;
import spectrum255.rogueengine.map.MapSnapshotCatalog;
import spectrum255.rogueengine.map.PlayerSceneStateService;
import spectrum255.rogueengine.map.PlayerSceneStateStore;
import spectrum255.rogueengine.map.SceneCacheService;
import spectrum255.rogueengine.map.ScenePlacementService;
import spectrum255.rogueengine.map.SnapshotPlacementService;
import spectrum255.rogueengine.inventory.PlayerInventoryStore;
import spectrum255.rogueengine.persistence.InstanceJournal;
import spectrum255.rogueengine.persistence.QuarantineService;
import spectrum255.rogueengine.profile.EngineSettings;

public final class ServerRuntime {
	private final MinecraftServer server;
	private final BoxManager boxes;
	private final InstanceJournal journal;
	private final DimensionService dimensions;
	private final ChunkPreparationService preparation;
	private final BoxCreationService creation;
	private final Supplier<EngineSettings> settings;
	private final DestroyConfirmationService confirmations;
	private final QuarantineService quarantine;
	private final PlayerInventoryService inventories;
	private final SceneCacheService sceneCache;
	private final ScenePlacementService scenePlacement;
	private final PlayerSceneStateService playerStates;
	private final MapSnapshotCatalog snapshots;
	private final SnapshotPlacementService snapshotPlacement;
	private final MutationGate mutationGate = new MutationGate();
	private final PerformanceMetrics metrics;
	private final AtomicBoolean reloading = new AtomicBoolean();
	private final AtomicBoolean stopping = new AtomicBoolean();

	public ServerRuntime(MinecraftServer server, BoxManager boxes, InstanceJournal journal, DimensionService dimensions,
			ChunkPreparationService preparation, BoxCreationService creation, Supplier<EngineSettings> settings,
			DestroyConfirmationService confirmations, QuarantineService quarantine, PerformanceMetrics metrics,
			PlayerInventoryStore inventoryStore) {
		this.server = server;
		this.boxes = boxes;
		this.journal = journal;
		this.dimensions = dimensions;
		this.preparation = preparation;
		this.creation = creation;
		this.settings = settings;
		this.confirmations = confirmations;
		this.quarantine = quarantine;
		this.metrics = metrics;
		this.inventories = new PlayerInventoryService(this, inventoryStore);
		this.sceneCache = new SceneCacheService(inventoryStore.root().getParent());
		this.scenePlacement = new ScenePlacementService();
		this.playerStates = new PlayerSceneStateService(
				new PlayerSceneStateStore(this.sceneCache.root(), server.registryAccess()));
		Path worldRoot = this.sceneCache.root().getParent().getParent();
		this.snapshots = new MapSnapshotCatalog(worldRoot, server.registryAccess(), id -> {
			Identifier resource = Identifier.fromNamespaceAndPath(id.getNamespace(),
					"rogueengine/map_snapshot/" + id.getPath() + ".nbt");
			try (InputStream input = server.getResourceManager().open(resource)) {
				return Optional.of(input.readAllBytes());
			} catch (IOException exception) {
				return Optional.empty();
			}
		});
		this.snapshotPlacement = new SnapshotPlacementService();
	}

	public DestroyConfirmationService confirmations() {
		return confirmations;
	}

	public QuarantineService quarantine() {
		return quarantine;
	}

	public PlayerInventoryService inventories() {
		return inventories;
	}

	public SceneCacheService sceneCache() {
		return sceneCache;
	}

	public ScenePlacementService scenePlacement() {
		return scenePlacement;
	}

	public PlayerSceneStateService playerStates() {
		return playerStates;
	}

	public MapSnapshotCatalog snapshots() {
		return snapshots;
	}

	public SnapshotPlacementService snapshotPlacement() {
		return snapshotPlacement;
	}

	public EngineSettings settings() {
		return settings.get();
	}

	public MinecraftServer server() {
		return server;
	}

	public BoxManager boxes() {
		return boxes;
	}

	public InstanceJournal journal() {
		return journal;
	}

	public DimensionService dimensions() {
		return dimensions;
	}

	public ChunkPreparationService preparation() {
		return preparation;
	}

	public BoxCreationService creation() {
		return creation;
	}

	public <T> T mutate(Supplier<T> action, Supplier<T> rejected) {
		if (isReloading() || isStopping() || !mutationGate.tryBegin()) {
			return rejected.get();
		}
		try {
			return action.get();
		} finally {
			mutationGate.end();
		}
	}

	public PerformanceMetrics metrics() {
		return metrics;
	}

	public boolean isReloading() {
		return reloading.get();
	}

	public boolean isStopping() {
		return stopping.get();
	}

	public void beginReload() {
		reloading.set(true);
	}

	public void endReload() {
		reloading.set(false);
	}

	public void beginStop() {
		stopping.set(true);
	}
}
