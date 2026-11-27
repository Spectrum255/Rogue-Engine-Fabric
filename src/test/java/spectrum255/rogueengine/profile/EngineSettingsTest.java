package spectrum255.rogueengine.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;

class EngineSettingsTest {
	@Test
	void defaultsAreSafe() {
		EngineSettings settings = EngineSettings.DEFAULT;

		assertEquals(8, settings.maxActiveInstances());
		assertEquals(16, settings.maxPendingOperations());
		assertEquals(30, settings.destroyConfirmationTtlSeconds());
	}

	@Test
	void validJsonParses() {
		DataResult<EngineSettings> result = EngineSettings.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("""
				{
				  "schema_version": 1,
				  "max_active_instances": 4,
				  "max_pending_operations": 8,
				  "preload_radius": 2,
				  "preload_chunks_per_tick": 2,
				  "preload_tick_budget_ms": 5,
				  "preload_timeout_seconds": 60,
				  "archive_min_tick_budget_ms": 20,
				  "destroy_confirmation_ttl_seconds": 30,
				  "quarantine_retention_days": 7,
				  "fallback_dimension": "minecraft:overworld"
				}
				"""));

		assertTrue(result.result().isPresent());
		assertEquals(4, result.result().orElseThrow().maxActiveInstances());
	}

	@Test
	void invalidBudgetFails() {
		DataResult<EngineSettings> result = EngineSettings.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("""
				{
				  "schema_version": 1,
				  "max_active_instances": 0,
				  "max_pending_operations": 8,
				  "preload_radius": 2,
				  "preload_chunks_per_tick": 2,
				  "preload_tick_budget_ms": 5,
				  "preload_timeout_seconds": 60,
				  "archive_min_tick_budget_ms": 20,
				  "destroy_confirmation_ttl_seconds": 30,
				  "quarantine_retention_days": 7,
				  "fallback_dimension": "minecraft:overworld"
				}
				"""));

		assertFalse(result.result().isPresent());
	}
}
