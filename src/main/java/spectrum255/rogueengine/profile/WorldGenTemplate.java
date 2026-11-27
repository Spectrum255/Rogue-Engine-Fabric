package spectrum255.rogueengine.profile;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.mojang.serialization.DataResult;

import net.minecraft.resources.Identifier;

import spectrum255.rogueengine.map.MapPool;

public record WorldGenTemplate(
		int schemaVersion,
		String generatorKind,
		Identifier baseStem,
		NoiseRanges ranges,
		List<Preset> presets,
		Identifier mapPoolId,
		MapPool.SelectionRange defaultSelection,
		TerrainBoundary terrainBoundary,
		CentralIslandSettings island) {
	private static final Set<String> GENERATOR_KINDS =
			Set.of("noise_chaos", "full_chaos", "preset_chaos", "central_island", "map_pool");

	public WorldGenTemplate {
		Objects.requireNonNull(baseStem, "baseStem");
		Objects.requireNonNull(ranges, "ranges");
		presets = List.copyOf(presets);
		Objects.requireNonNull(defaultSelection, "defaultSelection");
	}

	public WorldGenTemplate(int schemaVersion, String generatorKind, Identifier baseStem,
			NoiseRanges ranges, List<Preset> presets) {
		this(schemaVersion, generatorKind, baseStem, ranges, presets, null,
				new MapPool.SelectionRange(List.of(), List.of()), null, null);
	}

	public DataResult<WorldGenTemplate> validate() {
		if (schemaVersion != 1) {
			return DataResult.error(() -> "Unsupported worldgen template schema_version: " + schemaVersion);
		}
		if (!GENERATOR_KINDS.contains(generatorKind)) {
			return DataResult.error(() -> "Unknown worldgen template generator_kind: " + generatorKind);
		}
		if (generatorKind.equals("map_pool") != (mapPoolId != null)) {
			return DataResult.error(() -> "map_pool is required only for map_pool templates");
		}
		DataResult<NoiseRanges> rangeResult = ranges.validate();
		if (rangeResult.result().isEmpty()) {
			return rangeResult.map(ignored -> this);
		}
		if (generatorKind.equals("preset_chaos") && presets.isEmpty()) {
			return DataResult.error(() -> "preset_chaos requires at least one preset");
		}
		if (terrainBoundary != null) {
			DataResult<TerrainBoundary> boundaryResult = terrainBoundary.validate();
			if (boundaryResult.result().isEmpty()) {
				return boundaryResult.map(ignored -> this);
			}
		}
		boolean centralIsland = generatorKind.equals("central_island");
		if (centralIsland != (island != null)) {
			return DataResult.error(() -> "island is required only for central_island templates");
		}
		if (centralIsland) {
			if (terrainBoundary == null || terrainBoundary.outsideMode() != TerrainBoundary.OutsideMode.VOID) {
				return DataResult.error(() -> "central_island requires a void terrain boundary");
			}
			DataResult<CentralIslandSettings> islandResult = island.validate();
			if (islandResult.result().isEmpty()) {
				return islandResult.map(ignored -> this);
			}
		}
		Set<String> names = new java.util.HashSet<>();
		for (Preset preset : presets) {
			if (preset.name().isBlank() || !names.add(preset.name())) {
				return DataResult.error(() -> "Preset names must be non-blank and unique");
			}
			DataResult<NoiseParameters> presetResult = ranges.validate(preset.parameters());
			if (presetResult.result().isEmpty()) {
				return presetResult.map(ignored -> this);
			}
		}
		return DataResult.success(this);
	}

	public record DoubleRange(double min, double max) {
		public boolean valid() {
			return Double.isFinite(min) && Double.isFinite(max) && min <= max;
		}

		public boolean contains(double value) {
			return Double.isFinite(value) && value >= min && value <= max;
		}
	}

	public record IntRange(int min, int max) {
		public boolean valid() {
			return min <= max;
		}

		public boolean contains(int value) {
			return value >= min && value <= max;
		}
	}

	public static NoiseRanges neutralRanges() {
		return new NoiseRanges(
				new DoubleRange(0.0, 0.0),
				new DoubleRange(1.0, 1.0),
				new DoubleRange(1.0, 1.0),
				new DoubleRange(1.0, 1.0),
				new DoubleRange(1.0, 1.0),
				new DoubleRange(0.0, 0.0),
				new DoubleRange(0.0, 0.0),
				new IntRange(0, 0));
	}

	public record NoiseRanges(
			DoubleRange densityOffset,
			DoubleRange densityScale,
			DoubleRange ridgeScale,
			DoubleRange continentScale,
			DoubleRange erosionScale,
			DoubleRange temperatureShift,
			DoubleRange vegetationShift,
			IntRange seaLevelOffset) {
		public NoiseRanges {
			Objects.requireNonNull(densityOffset, "densityOffset");
			Objects.requireNonNull(densityScale, "densityScale");
			Objects.requireNonNull(ridgeScale, "ridgeScale");
			Objects.requireNonNull(continentScale, "continentScale");
			Objects.requireNonNull(erosionScale, "erosionScale");
			Objects.requireNonNull(temperatureShift, "temperatureShift");
			Objects.requireNonNull(vegetationShift, "vegetationShift");
			Objects.requireNonNull(seaLevelOffset, "seaLevelOffset");
		}

		public DataResult<NoiseRanges> validate() {
			if (!densityOffset.valid() || !densityScale.valid() || !ridgeScale.valid()
					|| !continentScale.valid() || !erosionScale.valid() || !temperatureShift.valid()
					|| !vegetationShift.valid() || !seaLevelOffset.valid()) {
				return DataResult.error(() -> "Ranges must be finite and min <= max");
			}
			if (densityScale.min() <= 0.0 || ridgeScale.min() <= 0.0
					|| continentScale.min() <= 0.0 || erosionScale.min() <= 0.0) {
				return DataResult.error(() -> "Density and climate scales must be positive");
			}
			if (densityScale.max() > 4.0 || ridgeScale.max() > 4.0
					|| continentScale.max() > 4.0 || erosionScale.max() > 4.0) {
				return DataResult.error(() -> "Density and climate scales must not exceed 4.0");
			}
			if (densityOffset.min() < -1.0 || densityOffset.max() > 1.0
					|| temperatureShift.min() < -1.0 || temperatureShift.max() > 1.0
					|| vegetationShift.min() < -1.0 || vegetationShift.max() > 1.0) {
				return DataResult.error(() -> "Density offsets and climate shifts must stay within [-1, 1]");
			}
			if (seaLevelOffset.min() < -64 || seaLevelOffset.max() > 64) {
				return DataResult.error(() -> "Sea level offset must stay within [-64, 64]");
			}
			return DataResult.success(this);
		}

		public DataResult<NoiseParameters> validate(NoiseParameters parameters) {
			if (!densityOffset.contains(parameters.densityOffset())
					|| !densityScale.contains(parameters.densityScale())
					|| !ridgeScale.contains(parameters.ridgeScale())
					|| !continentScale.contains(parameters.continentScale())
					|| !erosionScale.contains(parameters.erosionScale())
					|| !temperatureShift.contains(parameters.temperatureShift())
					|| !vegetationShift.contains(parameters.vegetationShift())
					|| !seaLevelOffset.contains(parameters.seaLevelOffset())) {
				return DataResult.error(() -> "Preset parameters are outside the template ranges");
			}
			return DataResult.success(parameters);
		}
	}

	public record NoiseParameters(
			double densityOffset,
			double densityScale,
			double ridgeScale,
			double continentScale,
			double erosionScale,
			double temperatureShift,
			double vegetationShift,
			int seaLevelOffset) {
	}

	public record Preset(String name, NoiseParameters parameters) {
		public Preset {
			Objects.requireNonNull(name, "name");
			Objects.requireNonNull(parameters, "parameters");
		}
	}
}
