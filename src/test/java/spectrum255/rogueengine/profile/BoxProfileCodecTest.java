package spectrum255.rogueengine.profile;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

class BoxProfileCodecTest {
	@BeforeAll
	static void bootstrapMinecraft() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}
	@Test
	void parsesStemRandomSearchProfile() {
		DataResult<BoxProfile> result = parse("""
				{
				  "schema_version": 1,
				  "dimension_type": "minecraft:overworld",
				  "generator": {"kind": "stem", "id": "minecraft:overworld"},
				  "seed": {"mode": "random"},
				  "spawn": {"mode": "search", "radius": 32},
				  "border": {"center_x": 0.0, "center_z": 0.0, "initial_size": 512.0, "warning_blocks": 16.0}
				}
				""");

		assertTrue(result.result().isPresent());
		BoxProfile profile = result.result().orElseThrow();
		assertInstanceOf(BoxProfile.StemGenerator.class, profile.generator());
		assertTrue(profile.inventory().bringOnEnter());
		assertTrue(profile.inventory().takeOnExit());
	}

	@Test
	void parsesExplicitInventoryPolicy() {
		DataResult<BoxProfile> result = parse("""
				{
				  "schema_version": 1,
				  "dimension_type": "minecraft:overworld",
				  "generator": {"kind": "stem", "id": "minecraft:overworld"},
				  "seed": {"mode": "random"},
				  "spawn": {"mode": "search", "radius": 8},
				  "border": {"center_x": 0.0, "center_z": 0.0, "initial_size": 256.0, "warning_blocks": 16.0},
				  "inventory": {"bring_on_enter": false, "take_on_exit": false}
				}
				""");

		assertTrue(result.result().isPresent());
		assertFalse(result.result().orElseThrow().inventory().bringOnEnter());
		assertFalse(result.result().orElseThrow().inventory().takeOnExit());
	}

	@Test
	void rejectsNonBooleanInventoryPolicy() {
		assertFalse(parse("""
				{
				  "schema_version": 1,
				  "dimension_type": "minecraft:overworld",
				  "generator": {"kind": "stem", "id": "minecraft:overworld"},
				  "seed": {"mode": "random"},
				  "spawn": {"mode": "search", "radius": 8},
				  "border": {"center_x": 0.0, "center_z": 0.0, "initial_size": 256.0, "warning_blocks": 16.0},
				  "inventory": {"bring_on_enter": "no", "take_on_exit": true}
				}
				""").result().isPresent());
	}

	@Test
	void rejectsUnknownSchema() {
		assertFalse(parse("""
				{
				  "schema_version": 3,
				  "dimension_type": "minecraft:overworld",
				  "generator": {"kind": "stem", "id": "minecraft:overworld"},
				  "seed": {"mode": "random"},
				  "spawn": {"mode": "search", "radius": 32},
				  "border": {"center_x": 0.0, "center_z": 0.0, "initial_size": 512.0, "warning_blocks": 16.0}
				}
				""").result().isPresent());
	}

	@Test
	void fixedSeedRequiresValue() {
		assertFalse(parse("""
				{
				  "schema_version": 1,
				  "dimension_type": "minecraft:overworld",
				  "generator": {"kind": "stem", "id": "minecraft:overworld"},
				  "seed": {"mode": "fixed"},
				  "spawn": {"mode": "search", "radius": 32},
				  "border": {"center_x": 0.0, "center_z": 0.0, "initial_size": 512.0, "warning_blocks": 16.0}
				}
				""").result().isPresent());
	}

	@Test
	void randomSeedRejectsValue() {
		assertFalse(parse("""
				{
				  "schema_version": 1,
				  "dimension_type": "minecraft:overworld",
				  "generator": {"kind": "stem", "id": "minecraft:overworld"},
				  "seed": {"mode": "random", "value": 1},
				  "spawn": {"mode": "search", "radius": 32},
				  "border": {"center_x": 0.0, "center_z": 0.0, "initial_size": 512.0, "warning_blocks": 16.0}
				}
				""").result().isPresent());
	}

	@Test
	void fixedSpawnMustBeInsideBorder() {
		assertFalse(parse("""
				{
				  "schema_version": 1,
				  "dimension_type": "minecraft:overworld",
				  "generator": {"kind": "stem", "id": "minecraft:overworld"},
				  "seed": {"mode": "random"},
				  "spawn": {"mode": "fixed", "x": 300.0, "y": 64.0, "z": 0.0, "yaw": 0.0},
				  "border": {"center_x": 0.0, "center_z": 0.0, "initial_size": 512.0, "warning_blocks": 16.0}
				}
				""").result().isPresent());
	}

	private DataResult<BoxProfile> parse(String json) {
		JsonElement element = JsonParser.parseString(json);
		return BoxProfileCodec.CODEC.parse(JsonOps.INSTANCE, element);
	}
}
