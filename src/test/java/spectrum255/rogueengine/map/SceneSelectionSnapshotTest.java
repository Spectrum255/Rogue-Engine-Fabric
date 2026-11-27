package spectrum255.rogueengine.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonElement;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

class SceneSelectionSnapshotTest {
	@Test
	void snapshotRoundTripsThroughCodec() {
		SceneSelectionSnapshot snapshot = snapshot("scenes/" + UUID.randomUUID() + "/source.nbt", "a".repeat(64));
		JsonElement encoded = SceneSelectionSnapshotCodec.CODEC.encodeStart(JsonOps.INSTANCE, snapshot).getOrThrow();

		assertEquals(snapshot, SceneSelectionSnapshotCodec.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow());
	}

	@Test
	void capturesSelectionAndCachedScene() {
		UUID instanceId = UUID.randomUUID();
		MapSelection selection = new MapSelection(Identifier.withDefaultNamespace("pool"), "b".repeat(64),
				Identifier.withDefaultNamespace("scene"), 3, 42L, 1L);
		MapScene scene = new MapScene(1, Identifier.withDefaultNamespace("structure"),
				new MapScene.SceneBounds(BlockPos.ZERO, new BlockPos(15, 79, 15)), BlockPos.ZERO, BlockPos.ZERO,
				net.minecraft.world.level.block.Rotation.NONE, net.minecraft.world.level.block.Mirror.NONE, List.of(),
				new MapScene.RefreshPolicy(List.of(MapScene.RefreshTrigger.MANUAL), MapScene.PlayerResetPolicy.NONE));
		SceneCacheService.CachedScene cached = new SceneCacheService.CachedScene(
				"scenes/" + instanceId + "/source.nbt", "a".repeat(64));

		SceneSelectionSnapshot snapshot = SceneSelectionSnapshot.capture(instanceId, selection, scene, cached);

		assertEquals(selection.sceneId(), snapshot.sceneId());
		assertEquals(selection.seed(), snapshot.drawSeed());
		assertEquals(cached.hash(), snapshot.sceneHash());
		assertEquals(scene.spawn(), snapshot.spawn());
	assertEquals(scene.bounds(), snapshot.bounds());
	assertEquals(scene.processorIds(), snapshot.processorIds());
	}

	@Test
	void fullResetCapturesPlayerStateDirectory() {
		UUID instanceId = UUID.randomUUID();
		MapScene scene = new MapScene(1, Identifier.withDefaultNamespace("structure"),
				new MapScene.SceneBounds(BlockPos.ZERO, BlockPos.ZERO), BlockPos.ZERO, BlockPos.ZERO,
				net.minecraft.world.level.block.Rotation.NONE, net.minecraft.world.level.block.Mirror.NONE, List.of(),
				new MapScene.RefreshPolicy(List.of(MapScene.RefreshTrigger.MANUAL), MapScene.PlayerResetPolicy.FULL));
		SceneSelectionSnapshot snapshot = SceneSelectionSnapshot.capture(instanceId,
				new MapSelection(Identifier.withDefaultNamespace("pool"), "a".repeat(64),
						Identifier.withDefaultNamespace("scene"), 1, 42L, 0L),
				scene, new SceneCacheService.CachedScene("scenes/" + instanceId + "/source.nbt", "b".repeat(64)));

		assertEquals(Optional.of("scenes/" + instanceId + "/players"), snapshot.playerStatePath());
	}

	@Test
	void snapshotSourceIsPersistedInSelection() {
		UUID instanceId = UUID.randomUUID();
		Identifier sourceId = Identifier.parse("example:room");
		MapScene scene = new MapScene(2, new MapScene.Source(MapScene.SourceKind.SNAPSHOT, sourceId),
				new MapScene.SceneBounds(BlockPos.ZERO, BlockPos.ZERO), BlockPos.ZERO, BlockPos.ZERO,
				net.minecraft.world.level.block.Rotation.NONE, net.minecraft.world.level.block.Mirror.NONE, List.of(),
				new MapScene.RefreshPolicy(List.of(MapScene.RefreshTrigger.MANUAL), MapScene.PlayerResetPolicy.NONE));
		SceneSelectionSnapshot snapshot = SceneSelectionSnapshot.capture(instanceId,
				new MapSelection(Identifier.withDefaultNamespace("pool"), "a".repeat(64),
						Identifier.withDefaultNamespace("scene"), 1, 42L, 0L),
				scene, new SceneCacheService.CachedScene("scenes/" + instanceId + "/source.nbt", "b".repeat(64)));

		assertEquals(MapScene.SourceKind.SNAPSHOT, snapshot.sourceKind());
		assertEquals(sourceId, snapshot.sourceId());
		JsonElement encoded = SceneSelectionSnapshotCodec.CODEC.encodeStart(JsonOps.INSTANCE, snapshot).getOrThrow();
		assertEquals(snapshot, SceneSelectionSnapshotCodec.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow());
	}

	@Test
	void resetCountCanBeIncrementedWithoutChangingSelection() {
		SceneSelectionSnapshot snapshot = snapshot("scenes/" + UUID.randomUUID() + "/source.nbt", "a".repeat(64));

		SceneSelectionSnapshot updated = snapshot.withResetCount(3L);

		assertEquals(3L, updated.resetCount());
		assertEquals(snapshot.sceneId(), updated.sceneId());
		assertEquals(snapshot.drawSeed(), updated.drawSeed());
	}

	@Test
	void rejectsCachePathTraversal() {
		UUID instanceId = UUID.randomUUID();
		assertFalse(SceneSelectionSnapshotCodec.CODEC.parse(JsonOps.INSTANCE, json(instanceId,
				"scenes/" + instanceId + "/../source.nbt", "a".repeat(64))).result().isPresent());
	}

	@Test
	void rejectsCacheOutsideInstanceDirectory() {
		UUID instanceId = UUID.randomUUID();
		assertFalse(SceneSelectionSnapshotCodec.CODEC.parse(JsonOps.INSTANCE, json(instanceId,
				"scenes/" + UUID.randomUUID() + "/source.nbt", "a".repeat(64))).result().isPresent());
	}

	@Test
	void rejectsBlankHash() {
		UUID instanceId = UUID.randomUUID();
		assertFalse(SceneSelectionSnapshotCodec.CODEC.parse(JsonOps.INSTANCE, json(instanceId,
				"scenes/" + instanceId + "/source.nbt", "")).result().isPresent());
	}

	private SceneSelectionSnapshot snapshot(String cachePath, String sceneHash) {
		UUID instanceId = UUID.randomUUID();
		String expectedPath = "scenes/" + instanceId + "/source.nbt";
		return new SceneSelectionSnapshot(
				instanceId,
				Identifier.withDefaultNamespace("pool"),
				Identifier.withDefaultNamespace("scene"),
				new SceneSelectionSnapshot.Hashes("b".repeat(64), sceneHash),
				42L,
				0L,
				BlockPos.ZERO,
				BlockPos.ZERO,
				net.minecraft.world.level.block.Rotation.NONE,
				net.minecraft.world.level.block.Mirror.NONE,
				new MapScene.SceneBounds(BlockPos.ZERO, new BlockPos(15, 79, 15)),
				List.of(),
				expectedPath,
				new MapScene.RefreshPolicy(List.of(MapScene.RefreshTrigger.MANUAL), MapScene.PlayerResetPolicy.NONE),
				0L,
				Optional.empty());
	}

	private JsonElement json(UUID instanceId, String cachePath, String sceneHash) {
		return com.google.gson.JsonParser.parseString("""
				{
				  "instance_id": "%s",
				  "pool_id": "minecraft:pool",
				  "scene_id": "minecraft:scene",
				  "hashes": {"pool_hash": "%s", "scene_hash": "%s"},
				  "draw_seed": 42,
				  "draw_cycle": 0,
				  "anchor": [0, 0, 0],
				  "spawn": [0, 0, 0],
				  "rotation": "none",
				  "mirror": "none",
				  "bounds": {"min": [0, 0, 0], "max": [15, 79, 15]},
				  "processors": [],
				  "cache_path": "%s",
				  "refresh": {"triggers": ["manual"], "player_reset": "none"},
				  "reset_count": 0
				}
				""".formatted(instanceId, "b".repeat(64), sceneHash, cachePath));
	}
}
