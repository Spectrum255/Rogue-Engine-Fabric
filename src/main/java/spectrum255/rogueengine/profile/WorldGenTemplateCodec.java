package spectrum255.rogueengine.profile;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.Identifier;

import spectrum255.rogueengine.map.MapPool;
import spectrum255.rogueengine.map.MapPoolCodec;

public final class WorldGenTemplateCodec {
	private WorldGenTemplateCodec() {
	}

	private static final Codec<WorldGenTemplate.DoubleRange> DOUBLE_RANGE_CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.DOUBLE.fieldOf("min").forGetter(WorldGenTemplate.DoubleRange::min),
			Codec.DOUBLE.fieldOf("max").forGetter(WorldGenTemplate.DoubleRange::max))
			.apply(instance, WorldGenTemplate.DoubleRange::new));

	private static final Codec<WorldGenTemplate.IntRange> INT_RANGE_CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.INT.fieldOf("min").forGetter(WorldGenTemplate.IntRange::min),
			Codec.INT.fieldOf("max").forGetter(WorldGenTemplate.IntRange::max))
			.apply(instance, WorldGenTemplate.IntRange::new));

	private static final Codec<WorldGenTemplate.NoiseRanges> NOISE_RANGES_CODEC = RecordCodecBuilder.create(instance -> instance.group(
			DOUBLE_RANGE_CODEC.fieldOf("density_offset").forGetter(WorldGenTemplate.NoiseRanges::densityOffset),
			DOUBLE_RANGE_CODEC.fieldOf("density_scale").forGetter(WorldGenTemplate.NoiseRanges::densityScale),
			DOUBLE_RANGE_CODEC.fieldOf("ridge_scale").forGetter(WorldGenTemplate.NoiseRanges::ridgeScale),
			DOUBLE_RANGE_CODEC.fieldOf("continent_scale").forGetter(WorldGenTemplate.NoiseRanges::continentScale),
			DOUBLE_RANGE_CODEC.fieldOf("erosion_scale").forGetter(WorldGenTemplate.NoiseRanges::erosionScale),
			DOUBLE_RANGE_CODEC.fieldOf("temperature_shift").forGetter(WorldGenTemplate.NoiseRanges::temperatureShift),
			DOUBLE_RANGE_CODEC.fieldOf("vegetation_shift").forGetter(WorldGenTemplate.NoiseRanges::vegetationShift),
			INT_RANGE_CODEC.fieldOf("sea_level_offset").forGetter(WorldGenTemplate.NoiseRanges::seaLevelOffset))
			.apply(instance, WorldGenTemplate.NoiseRanges::new));

	private static final Codec<WorldGenTemplate.NoiseParameters> NOISE_PARAMETERS_CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.DOUBLE.fieldOf("density_offset").forGetter(WorldGenTemplate.NoiseParameters::densityOffset),
			Codec.DOUBLE.fieldOf("density_scale").forGetter(WorldGenTemplate.NoiseParameters::densityScale),
			Codec.DOUBLE.fieldOf("ridge_scale").forGetter(WorldGenTemplate.NoiseParameters::ridgeScale),
			Codec.DOUBLE.fieldOf("continent_scale").forGetter(WorldGenTemplate.NoiseParameters::continentScale),
			Codec.DOUBLE.fieldOf("erosion_scale").forGetter(WorldGenTemplate.NoiseParameters::erosionScale),
			Codec.DOUBLE.fieldOf("temperature_shift").forGetter(WorldGenTemplate.NoiseParameters::temperatureShift),
			Codec.DOUBLE.fieldOf("vegetation_shift").forGetter(WorldGenTemplate.NoiseParameters::vegetationShift),
			Codec.INT.fieldOf("sea_level_offset").forGetter(WorldGenTemplate.NoiseParameters::seaLevelOffset))
			.apply(instance, WorldGenTemplate.NoiseParameters::new));

	private static final Codec<WorldGenTemplate.Preset> PRESET_CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.STRING.fieldOf("name").forGetter(WorldGenTemplate.Preset::name),
			NOISE_PARAMETERS_CODEC.fieldOf("parameters").forGetter(WorldGenTemplate.Preset::parameters))
			.apply(instance, WorldGenTemplate.Preset::new));

	public static final Codec<WorldGenTemplate> CODEC = RecordCodecBuilder.<WorldGenTemplate>create(instance -> instance.group(
			Codec.intRange(1, 1).fieldOf("schema_version").forGetter(WorldGenTemplate::schemaVersion),
			Codec.STRING.fieldOf("generator_kind").forGetter(WorldGenTemplate::generatorKind),
			Identifier.CODEC.fieldOf("base_stem").forGetter(WorldGenTemplate::baseStem),
			NOISE_RANGES_CODEC.optionalFieldOf("ranges", WorldGenTemplate.neutralRanges()).forGetter(WorldGenTemplate::ranges),
			PRESET_CODEC.listOf().optionalFieldOf("presets", java.util.List.of()).forGetter(WorldGenTemplate::presets),
			Identifier.CODEC.optionalFieldOf("map_pool")
								.forGetter(template -> java.util.Optional.ofNullable(template.mapPoolId())),
			MapPoolCodec.SELECTION_RANGE_CODEC.optionalFieldOf("default_range",
					new MapPool.SelectionRange(java.util.List.of(), java.util.List.of()))
					.forGetter(WorldGenTemplate::defaultSelection),
			TerrainBoundary.CODEC.optionalFieldOf("terrain_boundary")
					.forGetter(template -> java.util.Optional.ofNullable(template.terrainBoundary())),
			CentralIslandSettings.CODEC.optionalFieldOf("island")
					.forGetter(template -> java.util.Optional.ofNullable(template.island())))
			.apply(instance, (schema, kind, base, ranges, presets, mapPool, selection, boundary, island) ->
					new WorldGenTemplate(schema, kind, base, ranges, presets, mapPool.orElse(null), selection,
							boundary.orElse(null), island.orElse(null))))
			.validate(WorldGenTemplate::validate);
}
