package spectrum255.rogueengine.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

class MapSceneCodecTest {
	@Test
	void parsesStructureScene() {
		MapScene scene = parse("""
				{
				  "schema_version": 1,
				  "structure_id": "rogue-engine-fabric:example_room",
				  "bounds": {"min": [0, 64, 0], "max": [15, 79, 15]},
				  "anchor": [0, 64, 0],
				  "spawn": [7, 65, 7],
				  "rotation": "clockwise_90",
				  "mirror": "left_right",
				  "processors": ["minecraft:empty"],
				  "refresh": {
				    "triggers": ["manual", "on_empty"],
				    "player_reset": "none"
				  }
				}
				""").result().orElseThrow();

		assertEquals("rogue-engine-fabric:example_room", scene.structureId().toString());
		assertEquals(new BlockPos(0, 64, 0), scene.bounds().min());
		assertEquals(new BlockPos(15, 79, 15), scene.bounds().max());
		assertEquals(Rotation.CLOCKWISE_90, scene.rotation());
		assertEquals(Mirror.LEFT_RIGHT, scene.mirror());
	}

	@Test
	void parsesSnapshotSourceV2() {
		MapScene scene = parse("""
				{
				  "schema_version": 2,
				  "source": {"kind": "snapshot", "id": "example:room"},
				  "bounds": {"min": [0, 64, 0], "max": [15, 79, 15]},
				  "anchor": [0, 64, 0],
				  "spawn": [7, 65, 7],
				  "rotation": "none",
				  "mirror": "none",
				  "refresh": {"triggers": ["manual"], "player_reset": "none"}
				}
				""").result().orElseThrow();

		assertEquals(MapScene.SourceKind.SNAPSHOT, scene.source().kind());
		assertEquals(Identifier.parse("example:room"), scene.source().id());
	}

	@Test
	void rejectsV2WithoutSource() {
		assertFalse(parse("""
				{
				  "schema_version": 2,
				  "bounds": {"min": [0, 64, 0], "max": [15, 79, 15]},
				  "anchor": [0, 64, 0],
				  "spawn": [7, 65, 7],
				  "rotation": "none",
				  "mirror": "none",
				  "refresh": {"triggers": ["manual"], "player_reset": "none"}
				}
				""").result().isPresent());
	}

	@Test
	void rejectsInvertedBounds() {
		assertFalse(parse("""
				{
				  "schema_version": 1,
				  "structure_id": "rogue-engine-fabric:example_room",
				  "bounds": {"min": [16, 64, 0], "max": [0, 79, 15]},
				  "anchor": [0, 64, 0],
				  "spawn": [7, 65, 7],
				  "rotation": "none",
				  "mirror": "none",
				  "processors": [],
				  "refresh": {"triggers": ["manual"], "player_reset": "none"}
				}
				""").result().isPresent());
	}

	@Test
	void rejectsUnknownRotation() {
		assertFalse(parse("""
				{
				  "schema_version": 1,
				  "structure_id": "rogue-engine-fabric:example_room",
				  "bounds": {"min": [0, 64, 0], "max": [15, 79, 15]},
				  "anchor": [0, 64, 0],
				  "spawn": [7, 65, 7],
				  "rotation": "sideways",
				  "mirror": "none",
				  "processors": [],
				  "refresh": {"triggers": ["manual"], "player_reset": "none"}
				}
				""").result().isPresent());
	}

	private DataResult<MapScene> parse(String json) {
		JsonElement element = JsonParser.parseString(json);
		return MapSceneCodec.CODEC.parse(JsonOps.INSTANCE, element);
	}
}
