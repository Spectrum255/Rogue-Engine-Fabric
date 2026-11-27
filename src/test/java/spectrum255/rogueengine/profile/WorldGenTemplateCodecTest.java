package spectrum255.rogueengine.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;

class WorldGenTemplateCodecTest {
	@BeforeAll
	static void bootstrapMinecraft() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	void parsesNoiseChaosTemplate() {
		WorldGenTemplate template = parse("""
				{
				  "schema_version": 1,
				  "generator_kind": "noise_chaos",
				  "base_stem": "minecraft:overworld",
				  "ranges": {
				    "density_offset": {"min": -0.2, "max": 0.2},
				    "density_scale": {"min": 0.8, "max": 1.2},
				    "ridge_scale": {"min": 0.8, "max": 1.2},
				    "continent_scale": {"min": 0.8, "max": 1.2},
				    "erosion_scale": {"min": 0.8, "max": 1.2},
				    "temperature_shift": {"min": -0.1, "max": 0.1},
				    "vegetation_shift": {"min": -0.1, "max": 0.1},
				    "sea_level_offset": {"min": -8, "max": 8}
				  },
				  "presets": []
				}
				""").getOrThrow();

		assertEquals("noise_chaos", template.generatorKind());
		assertEquals("minecraft:overworld", template.baseStem().toString());
		assertEquals(-0.2, template.ranges().densityOffset().min());
		assertEquals(0.2, template.ranges().densityOffset().max());
	}

	@Test
	void rejectsInvertedRange() {
		assertFalse(parse("""
				{
				  "schema_version": 1,
				  "generator_kind": "noise_chaos",
				  "base_stem": "minecraft:overworld",
				  "ranges": {
				    "density_offset": {"min": 1.0, "max": -1.0},
				    "density_scale": {"min": 0.8, "max": 1.2},
				    "ridge_scale": {"min": 0.8, "max": 1.2},
				    "continent_scale": {"min": 0.8, "max": 1.2},
				    "erosion_scale": {"min": 0.8, "max": 1.2},
				    "temperature_shift": {"min": -0.1, "max": 0.1},
				    "vegetation_shift": {"min": -0.1, "max": 0.1},
				    "sea_level_offset": {"min": -8, "max": 8}
				  },
				  "presets": []
				}
				""").result().isPresent());
	}

	@Test
	void presetChaosRequiresPresets() {
		assertFalse(parse("""
				{
				  "schema_version": 1,
				  "generator_kind": "preset_chaos",
				  "base_stem": "minecraft:overworld",
				  "ranges": {
				    "density_offset": {"min": -0.2, "max": 0.2},
				    "density_scale": {"min": 0.8, "max": 1.2},
				    "ridge_scale": {"min": 0.8, "max": 1.2},
				    "continent_scale": {"min": 0.8, "max": 1.2},
				    "erosion_scale": {"min": 0.8, "max": 1.2},
				    "temperature_shift": {"min": -0.1, "max": 0.1},
				    "vegetation_shift": {"min": -0.1, "max": 0.1},
				    "sea_level_offset": {"min": -8, "max": 8}
				  },
				  "presets": []
				}
				""").result().isPresent());
	}

	@Test
	void rejectsPresetOutsideRanges() {
		assertFalse(parse("""
				{
				  "schema_version": 1,
				  "generator_kind": "preset_chaos",
				  "base_stem": "minecraft:overworld",
				  "ranges": {
				    "density_offset": {"min": -0.2, "max": 0.2},
				    "density_scale": {"min": 0.8, "max": 1.2},
				    "ridge_scale": {"min": 0.8, "max": 1.2},
				    "continent_scale": {"min": 0.8, "max": 1.2},
				    "erosion_scale": {"min": 0.8, "max": 1.2},
				    "temperature_shift": {"min": -0.1, "max": 0.1},
				    "vegetation_shift": {"min": -0.1, "max": 0.1},
				    "sea_level_offset": {"min": -8, "max": 8}
				  },
				  "presets": [{
				    "name": "invalid",
				    "parameters": {
				      "density_offset": 5.0,
				      "density_scale": 1.0,
				      "ridge_scale": 1.0,
				      "continent_scale": 1.0,
				      "erosion_scale": 1.0,
				      "temperature_shift": 0.0,
				      "vegetation_shift": 0.0,
				      "sea_level_offset": 0
				    }
				  }]
				}
				""").result().isPresent());
	}

	@Test
	void parsesTerrainBoundary() {
		WorldGenTemplate template = parse("""
				{
				  "schema_version": 1,
				  "generator_kind": "noise_chaos",
				  "base_stem": "minecraft:overworld",
				  "terrain_boundary": {
				    "center_x": 0.0,
				    "center_z": 0.0,
				    "radius_x": 128.0,
				    "radius_z": 128.0,
				    "shape": "circle",
				    "outside_mode": "void"
				  }
				}
				""").getOrThrow();

		assertEquals(TerrainBoundary.Shape.CIRCLE, template.terrainBoundary().shape());
		assertEquals(TerrainBoundary.OutsideMode.VOID, template.terrainBoundary().outsideMode());
		assertEquals(128.0, template.terrainBoundary().radiusX());
	}

	@Test
	void rejectsMismatchedCircleBoundary() {
		assertFalse(parse("""
				{
				  "schema_version": 1,
				  "generator_kind": "noise_chaos",
				  "base_stem": "minecraft:overworld",
				  "terrain_boundary": {
				    "center_x": 0.0,
				    "center_z": 0.0,
				    "radius_x": 128.0,
				    "radius_z": 64.0,
				    "shape": "circle",
				    "outside_mode": "void"
				  }
				}
				""").result().isPresent());
	}

	@Test
	void parsesCentralIslandTemplate() {
		WorldGenTemplate template = parse("""
				{
				  "schema_version": 1,
				  "generator_kind": "central_island",
				  "base_stem": "minecraft:overworld",
				  "terrain_boundary": {
				    "center_x": 0.0,
				    "center_z": 0.0,
				    "radius_x": 128.0,
				    "radius_z": 128.0,
				    "shape": "circle",
				    "outside_mode": "void"
				  },
				  "island": {
				    "surface_mode": "custom_layers",
				    "center_y": 128,
				    "max_thickness": 64,
				    "bottom_taper": 1.5,
				    "surface_roughness": 4.0,
				    "edge_noise": 0.35,
				    "custom_layers": {
				      "top_block": {"id": "minecraft:grass_block"},
				      "soil_block": {"id": "minecraft:dirt"},
				      "rock_block": {"id": "minecraft:stone"},
				      "top_depth": 1,
				      "soil_depth": 3,
				      "ore_block": {"id": "minecraft:coal_ore"},
				      "ore_chance": 0.02,
				      "ore_min_depth": 4,
				      "ore_max_depth": 32
				    }
				  }
				}
				""").getOrThrow();

		assertEquals("central_island", template.generatorKind());
		assertEquals(129, template.island().spawnY());
		assertEquals(Blocks.GRASS_BLOCK.defaultBlockState(),
				template.island().customLayers().orElseThrow().topBlock());
	}

	@Test
	void centralIslandRequiresVoidBoundary() {
		assertFalse(parse("""
				{
				  "schema_version": 1,
				  "generator_kind": "central_island",
				  "base_stem": "minecraft:overworld",
				  "terrain_boundary": {
				    "center_x": 0.0,
				    "center_z": 0.0,
					    "radius_x": 128.0,
					    "radius_z": 128.0,
					    "shape": "circle",
					    "outside_mode": "bedrock_floor"
					  },
				  "island": {
				    "surface_mode": "vanilla_surface",
				    "center_y": 128,
				    "max_thickness": 64,
				    "bottom_taper": 1.5,
				    "surface_roughness": 4.0,
				    "edge_noise": 0.35
				  }
				}
				""").result().isPresent());
	}

	@Test
	void parsesMapPoolTemplate() {
		WorldGenTemplate template = parse("""
				{
				  "schema_version": 1,
				  "generator_kind": "map_pool",
				  "base_stem": "minecraft:overworld",
				  "map_pool": "rogue-engine-fabric:example_pool",
				  "default_range": {"scenes": ["rogue-engine-fabric:room_a"], "tags": []}
				}
				""").getOrThrow();

		assertEquals("map_pool", template.generatorKind());
		assertEquals("rogue-engine-fabric:example_pool", template.mapPoolId().toString());
		assertEquals(1, template.defaultSelection().sceneIds().size());
	}

	@Test
	void mapPoolTemplateRequiresPoolId() {
		assertFalse(parse("""
				{
				  "schema_version": 1,
				  "generator_kind": "map_pool",
				  "base_stem": "minecraft:overworld"
				}
				""").result().isPresent());
	}

	@Test
	void noiseTemplateRejectsMapPoolFields() {
		assertFalse(parse("""
				{
				  "schema_version": 1,
				  "generator_kind": "noise_chaos",
				  "base_stem": "minecraft:overworld",
				  "map_pool": "rogue-engine-fabric:example_pool",
				  "ranges": {
				    "density_offset": {"min": -0.2, "max": 0.2},
				    "density_scale": {"min": 0.8, "max": 1.2},
				    "ridge_scale": {"min": 0.8, "max": 1.2},
				    "continent_scale": {"min": 0.8, "max": 1.2},
				    "erosion_scale": {"min": 0.8, "max": 1.2},
				    "temperature_shift": {"min": -0.1, "max": 0.1},
				    "vegetation_shift": {"min": -0.1, "max": 0.1},
				    "sea_level_offset": {"min": -8, "max": 8}
				  },
				  "presets": []
				}
				""").result().isPresent());
	}

	@Test
	void rejectsExcessiveScale() {
		WorldGenTemplate.NoiseRanges ranges = new WorldGenTemplate.NoiseRanges(
				new WorldGenTemplate.DoubleRange(-0.2, 0.2),
				new WorldGenTemplate.DoubleRange(0.5, 5.0),
				new WorldGenTemplate.DoubleRange(0.8, 1.2),
				new WorldGenTemplate.DoubleRange(0.8, 1.2),
				new WorldGenTemplate.DoubleRange(0.8, 1.2),
				new WorldGenTemplate.DoubleRange(-0.1, 0.1),
				new WorldGenTemplate.DoubleRange(-0.1, 0.1),
				new WorldGenTemplate.IntRange(-8, 8));

		assertFalse(ranges.validate().result().isPresent());
	}

	@Test
	void rejectsUnknownSchema() {
		assertFalse(parse("""
				{
				  "schema_version": 2,
				  "generator_kind": "noise_chaos",
				  "base_stem": "minecraft:overworld",
				  "ranges": {
				    "density_offset": {"min": -0.2, "max": 0.2},
				    "density_scale": {"min": 0.8, "max": 1.2},
				    "ridge_scale": {"min": 0.8, "max": 1.2},
				    "continent_scale": {"min": 0.8, "max": 1.2},
				    "erosion_scale": {"min": 0.8, "max": 1.2},
				    "temperature_shift": {"min": -0.1, "max": 0.1},
				    "vegetation_shift": {"min": -0.1, "max": 0.1},
				    "sea_level_offset": {"min": -8, "max": 8}
				  },
				  "presets": []
				}
				""").result().isPresent());
	}

	private DataResult<WorldGenTemplate> parse(String json) {
		JsonElement element = JsonParser.parseString(json);
		return WorldGenTemplateCodec.CODEC.parse(JsonOps.INSTANCE, element);
	}
}
