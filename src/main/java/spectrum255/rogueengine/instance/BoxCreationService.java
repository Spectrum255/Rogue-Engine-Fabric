package spectrum255.rogueengine.instance;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import com.mojang.serialization.JsonOps;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;

import spectrum255.rogueengine.RogueEngineFabric;
import spectrum255.rogueengine.dimension.DimensionService;
import spectrum255.rogueengine.lifecycle.ServerLifecycleHooks;
import spectrum255.rogueengine.map.MapPool;
import spectrum255.rogueengine.map.MapPoolCatalog;
import spectrum255.rogueengine.map.MapPoolCodec;
import spectrum255.rogueengine.map.MapPoolDrawService;
import spectrum255.rogueengine.map.MapPoolDrawState;
import spectrum255.rogueengine.map.MapScene;
import spectrum255.rogueengine.map.MapSceneCatalog;
import spectrum255.rogueengine.map.SceneCacheService;
import spectrum255.rogueengine.map.SceneSelectionSnapshot;
import spectrum255.rogueengine.map.SelectionRequest;
import spectrum255.rogueengine.map.StructureScenePlacer;
import spectrum255.rogueengine.mixin.MinecraftServerAccessor;
import spectrum255.rogueengine.persistence.InstanceJournal;
import spectrum255.rogueengine.profile.BoxProfile;
import spectrum255.rogueengine.profile.EngineSettings;
import spectrum255.rogueengine.profile.ProfileCatalog;
import spectrum255.rogueengine.profile.ProfileSnapshot;
import spectrum255.rogueengine.profile.ResolvedBoxProfile;
import spectrum255.rogueengine.profile.WorldGenTemplate;
import spectrum255.rogueengine.profile.WorldGenTemplateCatalog;
import spectrum255.rogueengine.profile.WorldGenTemplateCodec;
import spectrum255.rogueengine.runtime.PerformanceMetrics;
import spectrum255.rogueengine.runtime.ServerRuntime;

public final class BoxCreationService {
	private final MinecraftServer server;
	private final BoxManager boxes;
	private final InstanceJournal journal;
	private final DimensionService dimensions;
	private final Supplier<EngineSettings> settings;

	public BoxCreationService(MinecraftServer server, BoxManager boxes, InstanceJournal journal,
			DimensionService dimensions, Supplier<EngineSettings> settings) {
		this.server = server;
		this.boxes = boxes;
		this.journal = journal;
		this.dimensions = dimensions;
		this.settings = settings;
	}

	public CreateResult create(Identifier profileId, String name) {
		return create(profileId, name, SelectionRequest.all());
	}

	public CreateResult create(Identifier profileId, String name, SelectionRequest selectionRequest) {
		if (!server.isSameThread()) {
			return new Rejected("Box creation must run on the server thread");
		}
		long active = server.levelKeys().stream()
				.filter(key -> RogueEngineFabric.MOD_ID.equals(key.identifier().getNamespace()))
				.filter(key -> key.identifier().getPath().startsWith("instance/"))
				.count();
		if (active >= settings.get().maxActiveInstances()) {
			return new Rejected("Active box limit reached: " + settings.get().maxActiveInstances());
		}
		BoxProfileHolder profileHolder = ProfileCatalog.from(server.reloadableRegistries().lookup()).get(profileId)
				.map(BoxProfileHolder::new)
				.orElse(null);
		if (profileHolder == null) {
			return new Rejected("Unknown box profile: " + profileId);
		}
		UUID id = UUID.randomUUID();
		BoxInstance instance = BoxInstance.create(id, name);
		BoxManager.CreateResult created = boxes.create(id, name);
		if (created instanceof BoxManager.Rejected rejected) {
			return new Rejected(rejected.reason());
		}
		BoxDefinition definition;
		MapPreparation mapPreparation = null;
		try {
			long seed = SeedFactory.choose(profileHolder.profile.seed());
			ResolvedBoxProfile resolved = ResolvedBoxProfile.resolve(profileHolder.profile, server.registryAccess(),
					server.reloadableRegistries().lookup(), seed);
			WorldGenTemplate mapTemplate = mapTemplate(profileHolder.profile);
			if (mapTemplate != null) {
				mapPreparation = prepareMap(id, seed, mapTemplate, selectionRequest);
				definition = BoxDefinition.capture(id, profileId, resolved, seed, server.registryAccess(),
						mapPreparation.snapshot());
			} else {
				definition = BoxDefinition.capture(id, profileId, resolved, seed, server.registryAccess());
			}
			journal.begin(instance, definition);
			journal.appendIntent(new InstanceJournal.WalRecord(UUID.randomUUID(), id, BoxState.CREATING, BoxState.ACTIVE,
					1, 2, "COMPLETE_CREATE", System.currentTimeMillis()));
		} catch (Exception exception) {
			markRecoveryRequired(instance, exception.getMessage());
			return new Rejected("Box preparation failed: " + exception.getMessage());
		}

		ServerLevel level = null;
		try {
			level = dimensions.create(definition);
			if (mapPreparation != null) {
				return startMapPlacement(instance, level, mapPreparation);
			}
			commitActive(instance);
			return new Created(id);
		} catch (Exception exception) {
			if (level != null) {
				try {
					dimensions.unload(level);
				} catch (IOException unloadException) {
					exception.addSuppressed(unloadException);
				}
			}
			markRecoveryRequired(instance, exception.getMessage());
			return new Rejected("Box creation failed: " + exception.getMessage());
		}
	}

	private WorldGenTemplate mapTemplate(BoxProfile profile) {
		if (!(profile.generator() instanceof BoxProfile.TemplateGenerator templateGenerator)) {
			return null;
		}
		WorldGenTemplate template = WorldGenTemplateCatalog.from(server.reloadableRegistries().lookup())
				.get(templateGenerator.id()).orElse(null);
		return template != null && template.generatorKind().equals("map_pool") ? template : null;
	}

	private MapPreparation prepareMap(UUID instanceId, long seed, WorldGenTemplate template,
			SelectionRequest request) throws IOException {
		MapPool pool = MapPoolCatalog.from(server.reloadableRegistries().lookup()).get(template.mapPoolId())
				.orElseThrow(() -> new IllegalArgumentException("Unknown map pool: " + template.mapPoolId()));
		String poolJson = MapPoolCodec.CODEC.encodeStart(JsonOps.INSTANCE, pool).result().orElseThrow().toString();
		String poolHash = ProfileSnapshot.sha256(poolJson);
		Path stateFile = mapPoolStateFile(template.mapPoolId());
		MapPoolDrawState persisted = MapPoolDrawService.loadState(stateFile, template.mapPoolId()).orElse(null);
		MapSceneCatalog scenes = MapSceneCatalog.from(server.reloadableRegistries().lookup());
		Map<Identifier, Set<Identifier>> tagScenes = new HashMap<>();
		Set<Identifier> tags = new HashSet<>(template.defaultSelection().tags());
		tags.addAll(request.tags());
		for (Identifier tag : tags) {
			tagScenes.put(tag, scenes.tagged(tag));
		}
		MapPoolDrawService.DrawResult draw = MapPoolDrawService.draw(pool, poolHash, seed, template.mapPoolId(),
				template.defaultSelection(), request, tagScenes, persisted);
		MapScene scene = scenes.get(draw.selection().sceneId())
				.orElseThrow(() -> new IllegalArgumentException("Unknown map scene: " + draw.selection().sceneId()));
		SceneCacheService.CachedScene cached;
		if (scene.source().kind() == MapScene.SourceKind.SNAPSHOT) {
			var loaded = runtime().snapshots().load(scene.snapshotId())
					.orElseThrow(() -> new IllegalArgumentException("Unknown map snapshot: " + scene.snapshotId()));
			cached = runtime().sceneCache().cache(instanceId, scene.snapshotId(), loaded.content());
		} else {
			var structure = server.getStructureTemplateManager().get(scene.structureId())
					.orElseThrow(() -> new IllegalArgumentException("Unknown map scene structure: " + scene.structureId()));
			ByteArrayOutputStream output = new ByteArrayOutputStream();
			NbtIo.writeCompressed(structure.save(new CompoundTag()), output);
			cached = runtime().sceneCache().cache(instanceId, scene.structureId(), output.toByteArray());
		}
		SceneSelectionSnapshot snapshot = SceneSelectionSnapshot.capture(instanceId, draw.selection(), scene, cached);
		return new MapPreparation(snapshot, scene, pool, draw.nextState(), stateFile);
	}

	private Path mapPoolStateFile(Identifier poolId) {
		Path worldRoot = ((MinecraftServerAccessor) server).getStorageSource().getLevelPath(LevelResource.ROOT);
		return worldRoot.resolve("data").resolve(RogueEngineFabric.MOD_ID).resolve("map-pools")
				.resolve(poolId.getNamespace()).resolve(poolId.getPath() + ".json");
	}

	private CreateResult startMapPlacement(BoxInstance instance, ServerLevel level, MapPreparation preparation)
			throws IOException {
		Path source = runtime().sceneCache().resolve(preparation.snapshot().cachePath());
		CompletableFuture<StructureScenePlacer.PlacementResult> future;
		if (preparation.scene().source().kind() == MapScene.SourceKind.SNAPSHOT) {
			future = runtime().snapshotPlacement().place(level, source, preparation.snapshot().sceneHash(),
					preparation.scene(), preparation.snapshot().anchor(), preparation.pool().budget());
		} else {
			future = runtime().scenePlacement().place(level, source, preparation.scene(),
					preparation.snapshot().anchor(), preparation.pool().budget());
		}
		future.whenComplete((result, error) -> server.execute(() -> {
			if (error != null) {
				RogueEngineFabric.LOGGER.error("Map scene placement failed for {}: {}", instance.id(), error.getMessage(), error);
				failMapCreation(instance, level, error.getMessage());
				return;
			}
			try {
				if (runtime().isStopping()) {
					throw new IllegalStateException("Server is stopping");
				}
				if (!isSafeSpawn(level, result.spawn())) {
					throw new IllegalStateException("Map scene spawn is not safe");
				}
				MapPoolDrawService.saveState(preparation.stateFile(), preparation.nextState());
				commitActive(instance);
			} catch (Exception exception) {
				RogueEngineFabric.LOGGER.error("Map scene finalization failed for {}: {}", instance.id(), exception.getMessage(), exception);
				failMapCreation(instance, level, exception.getMessage());
			}
		}));
		return new Pending(instance.id());
	}

	private void failMapCreation(BoxInstance instance, ServerLevel level, String reason) {
		try {
			dimensions.unload(level);
		} catch (IOException exception) {
			RogueEngineFabric.LOGGER.error("Failed to unload map box {}", instance.id(), exception);
		}
		markRecoveryRequired(instance, reason);
	}

	private void commitActive(BoxInstance instance) throws IOException {
		journal.commit(instance.withState(BoxState.ACTIVE, 2));
		BoxManager.TransitionResult transitioned = boxes.transition(instance.id(), BoxState.ACTIVE, 1);
		if (transitioned instanceof BoxManager.Rejected rejected) {
			throw new IllegalStateException(rejected.reason());
		}
		runtimeMetrics().recordCreate();
	}

	private boolean isSafeSpawn(ServerLevel level, BlockPos pos) {
		var ground = level.getBlockState(pos.below()).getCollisionShape(level, pos.below());
		var feet = level.getBlockState(pos).getCollisionShape(level, pos);
		var head = level.getBlockState(pos.above()).getCollisionShape(level, pos.above());
		return !ground.isEmpty() && feet.isEmpty() && head.isEmpty()
				&& !level.getBlockState(pos).is(Blocks.WATER) && !level.getBlockState(pos).is(Blocks.LAVA);
	}

	private PerformanceMetrics runtimeMetrics() {
		return runtime().metrics();
	}

	private ServerRuntime runtime() {
		return ServerLifecycleHooks.current(server);
	}

	private void markRecoveryRequired(BoxInstance instance, String reason) {
		try {
			BoxInstance recovery = instance.withState(BoxState.RECOVERY_REQUIRED, 2);
			if (journal.definition(instance.id()).isPresent()) {
				journal.commit(recovery);
			}
			boxes.transition(instance.id(), BoxState.RECOVERY_REQUIRED, 1);
		} catch (Exception exception) {
			RogueEngineFabric.LOGGER.error("Failed to mark box {} for recovery: {}", instance.id(), reason, exception);
		}
	}

	public sealed interface CreateResult permits Created, Pending, Rejected {
	}

	public record Created(UUID id) implements CreateResult {
	}

	public record Pending(UUID id) implements CreateResult {
	}

	public record Rejected(String reason) implements CreateResult {
	}

	private record MapPreparation(SceneSelectionSnapshot snapshot, MapScene scene, MapPool pool,
			MapPoolDrawState nextState, Path stateFile) {
	}

	private record BoxProfileHolder(BoxProfile profile) {
	}
}
