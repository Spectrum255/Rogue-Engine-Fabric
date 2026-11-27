package spectrum255.rogueengine.lifecycle;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

import spectrum255.rogueengine.RogueEngineFabric;
import spectrum255.rogueengine.command.DestroyConfirmationService;
import spectrum255.rogueengine.dimension.ChunkPreparationService;
import spectrum255.rogueengine.dimension.DimensionSeeds;
import spectrum255.rogueengine.dimension.DimensionService;
import spectrum255.rogueengine.instance.BoxCreationService;
import spectrum255.rogueengine.instance.BoxInstance;
import spectrum255.rogueengine.instance.BoxManager;
import spectrum255.rogueengine.instance.BoxState;
import spectrum255.rogueengine.inventory.PlayerInventoryStore;
import spectrum255.rogueengine.map.SceneResetService;
import spectrum255.rogueengine.mixin.MinecraftServerAccessor;
import spectrum255.rogueengine.persistence.InstanceJournal;
import spectrum255.rogueengine.persistence.PlayerReferenceScanner;
import spectrum255.rogueengine.persistence.QuarantineService;
import spectrum255.rogueengine.profile.DataPackReloadListener;
import spectrum255.rogueengine.profile.EngineSettings;
import spectrum255.rogueengine.runtime.PerformanceMetrics;
import spectrum255.rogueengine.runtime.ServerRuntime;

public final class ServerLifecycleHooks {
	private static final Map<MinecraftServer, ServerRuntime> RUNTIMES = new ConcurrentHashMap<>();
	private static DataPackReloadListener settingsListener;

	private ServerLifecycleHooks() {
	}

	public static void register(DataPackReloadListener listener) {
		settingsListener = listener;
		ServerPlayerEvents.JOIN.register(ServerLifecycleHooks::onJoin);
		ServerPlayerEvents.LEAVE.register(player -> new SceneResetService(current(player.level().getServer())).onPlayerLeave(player));
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) ->
				current(newPlayer.level().getServer()).inventories().afterRespawn(newPlayer, alive));
		ServerLifecycleEvents.SERVER_STARTING.register(ServerLifecycleHooks::starting);
		ServerLifecycleEvents.SERVER_STOPPING.register(ServerLifecycleHooks::stopping);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			DimensionSeeds.clear();
			RUNTIMES.remove(server);
		});
		ServerLifecycleEvents.START_DATA_PACK_RELOAD.register((server, manager) -> {
			ServerRuntime runtime = RUNTIMES.get(server);
			if (runtime != null) {
				runtime.beginReload();
			}
		});
		ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, manager, success) -> {
			ServerRuntime runtime = RUNTIMES.get(server);
			if (runtime != null) {
				runtime.endReload();
			}
		});
	}

	public static ServerRuntime current(MinecraftServer server) {
		ServerRuntime runtime = RUNTIMES.get(server);
		if (runtime == null) {
			throw new IllegalStateException("Rogue Engine runtime is not initialized");
		}
		return runtime;
	}

	private static void onJoin(ServerPlayer player) {
		ServerRuntime runtime = current(player.level().getServer());
		runtime.inventories().recoverOnJoin(player);
		try {
			var definition = runtime.inventories().definitionFor(player.level().dimension());
			runtime.playerStates().recoverOnJoin(
					new spectrum255.rogueengine.map.VanillaPlayerSceneStateAccess(player),
					definition == null ? null : definition.sceneSnapshot());
		} catch (Exception exception) {
			RogueEngineFabric.LOGGER.error("Could not restore player scene state for {}", player.getUUID(), exception);
			player.connection.disconnect(net.minecraft.network.chat.Component.literal("Could not restore box player state"));
		}
	}

	public static void restoreDimensions(MinecraftServer server) {
		ServerRuntime runtime = current(server);
		for (BoxInstance instance : runtime.boxes().all()) {
			if (instance.state() == BoxState.ARCHIVING) {
				var definition = runtime.journal().definition(instance.id())
						.orElseThrow(() -> new IllegalStateException("Archiving box has no definition: " + instance.id()));
				var storage = ((MinecraftServerAccessor) server).getStorageSource();
				try {
					if (PlayerReferenceScanner.isReferenced(storage.getLevelPath(LevelResource.PLAYER_DATA_DIR), instance.dimensionKey())) {
						throw new IllegalStateException("Offline player still references archiving box " + instance.id());
					}
				} catch (IOException exception) {
					throw new IllegalStateException("Could not verify offline players for " + instance.id(), exception);
				}
				ServerLevel level = runtime.dimensions().load(definition);
				try {
					runtime.dimensions().unload(level);
				} catch (IOException exception) {
					throw new IllegalStateException("Failed to finish interrupted archive for " + instance.id(), exception);
				}
				var updated = instance.withState(BoxState.ARCHIVED, instance.stateVersion() + 1);
				try {
					runtime.journal().commit(updated);
				} catch (IOException exception) {
					throw new IllegalStateException("Failed to commit archived box " + instance.id(), exception);
				}
				var result = runtime.boxes().transition(instance.id(), BoxState.ARCHIVED, instance.stateVersion());
				if (result instanceof BoxManager.Rejected rejected) {
					throw new IllegalStateException(rejected.reason());
				}
				continue;
			}
			if (instance.state() == BoxState.ACTIVE || instance.state() == BoxState.RESTORING
					|| instance.state() == BoxState.RESETTING) {
				var definition = runtime.journal().definition(instance.id())
						.orElseThrow(() -> new IllegalStateException("Active box has no definition: " + instance.id()));
				runtime.dimensions().load(definition);
				if (instance.state() == BoxState.RESTORING) {
					var updated = instance.withState(BoxState.ACTIVE, instance.stateVersion() + 1);
					try {
						runtime.journal().commit(updated);
					} catch (IOException exception) {
						throw new IllegalStateException("Failed to commit restored box " + instance.id(), exception);
					}
					var result = runtime.boxes().transition(instance.id(), BoxState.ACTIVE, instance.stateVersion());
					if (result instanceof BoxManager.Rejected rejected) {
						throw new IllegalStateException(rejected.reason());
					}
				}
			}
		}
		recoverInterruptedOperations(runtime);
	}

	public static void recoverNow(MinecraftServer server) {
		recoverInterruptedOperations(current(server));
	}

	private static void recoverInterruptedOperations(ServerRuntime runtime) {
		try {
			for (var operation : runtime.journal().load().pendingOperations()) {
				BoxInstance current = runtime.boxes().get(operation.instanceId()).orElse(null);
				if (current == null) {
					continue;
				}
				if (current.state() == BoxState.RESETTING || operation.recoveryAction().equals("COMPLETE_RESET")) {
					new SceneResetService(runtime).recover(current.id());
					continue;
				}
				if (operation.recoveryAction().equals("COMPLETE_DESTROY")) {
					var storage = ((MinecraftServerAccessor) runtime.server()).getStorageSource();
					Path source = storage.getDimensionPath(current.dimensionKey());
					runtime.quarantine().recover(operation.operationId(), operation.instanceId(), source);
					runtime.quarantine().quarantineInventories(operation.operationId(), operation.instanceId(),
							runtime.inventories().storeRoot());
					runtime.quarantine().quarantineScenes(operation.operationId(), operation.instanceId(),
							runtime.sceneCache().root().resolve("scenes"));
					BoxInstance destroyed = current.withState(BoxState.DESTROYED, current.stateVersion() + 1);
					runtime.journal().commit(destroyed);
					runtime.boxes().transition(current.id(), BoxState.DESTROYED, current.stateVersion());
				} else if (current.state() == BoxState.CREATING) {
					BoxInstance recovery = current.withState(BoxState.RECOVERY_REQUIRED, current.stateVersion() + 1);
					runtime.journal().commit(recovery);
					runtime.boxes().transition(current.id(), BoxState.RECOVERY_REQUIRED, current.stateVersion());
				}
			}
			runtime.quarantine().purgeExpired();
		} catch (Exception exception) {
			throw new IllegalStateException("Failed to recover interrupted Rogue Engine operations", exception);
		}
	}

	private static void starting(MinecraftServer server) {
		try {
			var storage = ((MinecraftServerAccessor) server).getStorageSource();
			InstanceJournal journal = new InstanceJournal(storage.getLevelPath(LevelResource.ROOT)
					.resolve("data").resolve(RogueEngineFabric.MOD_ID));
			BoxManager boxes = new BoxManager(journal.load().instances().values());
			DimensionService dimensions = new DimensionService(server);
			PerformanceMetrics metrics = new PerformanceMetrics();
			ChunkPreparationService preparation = new ChunkPreparationService(settingsListener::current, metrics);
			BoxCreationService creation = new BoxCreationService(server, boxes, journal, dimensions, settingsListener::current);
			EngineSettings settings = settingsListener.current();
			DestroyConfirmationService confirmations = new DestroyConfirmationService(System::currentTimeMillis,
					settings.destroyConfirmationTtlSeconds() * 1000L);
			Path worldRoot = storage.getLevelPath(LevelResource.ROOT);
			QuarantineService quarantine = new QuarantineService(worldRoot,
					settings.quarantineRetentionDays(), System::currentTimeMillis);
			PlayerInventoryStore inventoryStore = new PlayerInventoryStore(worldRoot.resolve("data")
					.resolve(RogueEngineFabric.MOD_ID).resolve("inventories"));
			RUNTIMES.put(server, new ServerRuntime(server, boxes, journal, dimensions, preparation, creation,
					settingsListener::current, confirmations, quarantine, metrics, inventoryStore));
		} catch (IOException exception) {
			throw new IllegalStateException("Failed to initialize Rogue Engine journal", exception);
		}
	}

	private static void stopping(MinecraftServer server) {
		ServerRuntime runtime = RUNTIMES.get(server);
		if (runtime != null) {
			runtime.beginStop();
		}
	}
}
