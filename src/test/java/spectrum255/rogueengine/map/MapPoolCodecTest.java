package spectrum255.rogueengine.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;

class MapPoolCodecTest {
	@Test
	void parsesWeightedPool() {
		MapPool pool = parse("""
				{
				  "schema_version": 1,
				  "entries": [
				    {"scene": "rogueengine:room_a", "weight": 3},
				    {"scene": "rogueengine:room_b", "weight": 1}
				  ],
				  "default_range": {"scenes": [], "tags": []},
				  "repeat_policy": "cycle_no_repeat",
				  "refresh": {"triggers": ["manual", "on_empty"], "player_reset": "none"},
				  "budget": {
				    "max_chunks_per_tick": 4,
				    "max_tick_ms": 5,
				    "max_entities": 128,
				    "max_block_entities": 256
				  }
				}
				""").result().orElseThrow();

		assertEquals(2, pool.entries().size());
		assertEquals(3, pool.entries().getFirst().weight());
		assertEquals(MapPool.RepeatPolicy.CYCLE_NO_REPEAT, pool.repeatPolicy());
	}

	@Test
	void rejectsEmptyEntries() {
		assertFalse(parse("""
				{
				  "schema_version": 1,
				  "entries": [],
				  "default_range": {"scenes": [], "tags": []},
				  "repeat_policy": "allow",
				  "refresh": {"triggers": ["manual"], "player_reset": "none"},
				  "budget": {"max_chunks_per_tick": 4, "max_tick_ms": 5, "max_entities": 128, "max_block_entities": 256}
				}
				""").result().isPresent());
	}

	@Test
	void rejectsNegativeWeight() {
		assertFalse(parse("""
				{
				  "schema_version": 1,
				  "entries": [{"scene": "rogueengine:room_a", "weight": -1}],
				  "default_range": {"scenes": [], "tags": []},
				  "repeat_policy": "allow",
				  "refresh": {"triggers": ["manual"], "player_reset": "none"},
				  "budget": {"max_chunks_per_tick": 4, "max_tick_ms": 5, "max_entities": 128, "max_block_entities": 256}
				}
				""").result().isPresent());
	}

	@Test
	void rejectsDuplicateScenes() {
		assertFalse(parse("""
				{
				  "schema_version": 1,
				  "entries": [
				    {"scene": "rogueengine:room_a", "weight": 1},
				    {"scene": "rogueengine:room_a", "weight": 2}
				  ],
				  "default_range": {"scenes": [], "tags": []},
				  "repeat_policy": "allow",
				  "refresh": {"triggers": ["manual"], "player_reset": "none"},
				  "budget": {"max_chunks_per_tick": 4, "max_tick_ms": 5, "max_entities": 128, "max_block_entities": 256}
				}
				""").result().isPresent());
	}

	private DataResult<MapPool> parse(String json) {
		JsonElement element = JsonParser.parseString(json);
		return MapPoolCodec.CODEC.parse(JsonOps.INSTANCE, element);
	}
}
