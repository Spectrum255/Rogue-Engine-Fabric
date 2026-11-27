package spectrum255.rogueengine.worldgen;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;

import com.mojang.serialization.DataResult;

import spectrum255.rogueengine.profile.BoxProfile;
import spectrum255.rogueengine.profile.WorldGenTemplate;
import spectrum255.rogueengine.profile.WorldGenTemplate.NoiseParameters;
import spectrum255.rogueengine.profile.WorldGenTemplate.NoiseRanges;
import spectrum255.rogueengine.profile.WorldGenTemplate.Preset;

public final class WorldGenParameterResolver {
	private static final long MIX = 0x9E3779B97F4A7C15L;

	private WorldGenParameterResolver() {
	}

	public static WorldGenParameters resolve(WorldGenTemplate template, String templateHash,
			BoxProfile.TemplateGenerator generator, long seed) {
		Objects.requireNonNull(template, "template");
		Objects.requireNonNull(generator, "generator");
		if (templateHash == null || templateHash.isBlank()) {
			throw new IllegalArgumentException("templateHash is required");
		}
		requireValid(template.validate());
		RandomizationMode mode = RandomizationMode.from(template.generatorKind());
		long mixedSeed = seed ^ (templateHash.hashCode() * MIX) ^ (generator.id().hashCode() * MIX);
		Random random = new Random(mixedSeed);
		NoiseParameters selected = switch (mode) {
			case NOISE_CHAOS, FULL_CHAOS -> randomize(template.ranges(), random);
			case PRESET_CHAOS -> selectPreset(template, generator.preset(), random);
			case CENTRAL_ISLAND -> {
				if (!generator.overrides().isEmpty()) {
					throw new IllegalArgumentException("central_island does not support noise parameter overrides");
				}
				yield new NoiseParameters(0.0, 1.0, 1.0, 1.0, 1.0, 0.0, 0.0, 0);
			}
		};
		NoiseParameters resolved = applyOverrides(selected, generator.overrides());
		requireValid(template.ranges().validate(resolved));
		return new WorldGenParameters(generator.id(), template.generatorKind(), templateHash, resolved,
				mode == RandomizationMode.PRESET_CHAOS ? findPresetName(template, selected, generator.preset()) : Optional.empty());
	}

	private static <T> T requireValid(DataResult<T> result) {
		if (result.result().isEmpty()) {
			throw new IllegalArgumentException(result.error().orElseThrow().message());
		}
		return result.result().orElseThrow();
	}

	private static NoiseParameters randomize(NoiseRanges ranges, Random random) {
		return new NoiseParameters(
				randomDouble(ranges.densityOffset(), random),
				randomDouble(ranges.densityScale(), random),
				randomDouble(ranges.ridgeScale(), random),
				randomDouble(ranges.continentScale(), random),
				randomDouble(ranges.erosionScale(), random),
				randomDouble(ranges.temperatureShift(), random),
				randomDouble(ranges.vegetationShift(), random),
				randomInt(ranges.seaLevelOffset(), random));
	}

	private static double randomDouble(WorldGenTemplate.DoubleRange range, Random random) {
		return range.min() == range.max() ? range.min() : range.min() + random.nextDouble() * (range.max() - range.min());
	}

	private static int randomInt(WorldGenTemplate.IntRange range, Random random) {
		return range.min() == range.max() ? range.min() : random.nextInt(range.max() - range.min() + 1) + range.min();
	}

	private static NoiseParameters selectPreset(WorldGenTemplate template, Optional<String> requested, Random random) {
		if (requested.isPresent()) {
			return template.presets().stream()
					.filter(preset -> preset.name().equals(requested.get()))
					.findFirst()
					.orElseThrow(() -> new IllegalArgumentException("Unknown worldgen preset: " + requested.get()))
					.parameters();
		}
		return template.presets().get(random.nextInt(template.presets().size())).parameters();
	}

	private static Optional<String> findPresetName(WorldGenTemplate template, NoiseParameters selected,
			Optional<String> requested) {
		if (requested.isPresent()) {
			return requested;
		}
		for (Preset preset : template.presets()) {
			if (preset.parameters().equals(selected)) {
				return Optional.of(preset.name());
			}
		}
		return Optional.empty();
	}

	private static NoiseParameters applyOverrides(NoiseParameters base, Map<String, Double> overrides) {
		for (String key : overrides.keySet()) {
			if (!isKnownParameter(key)) {
				throw new IllegalArgumentException("Unknown worldgen parameter override: " + key);
			}
		}
		return new NoiseParameters(
				override(overrides, "density_offset", base.densityOffset()),
				override(overrides, "density_scale", base.densityScale()),
				override(overrides, "ridge_scale", base.ridgeScale()),
				override(overrides, "continent_scale", base.continentScale()),
				override(overrides, "erosion_scale", base.erosionScale()),
				override(overrides, "temperature_shift", base.temperatureShift()),
				override(overrides, "vegetation_shift", base.vegetationShift()),
				seaLevelOverride(overrides, base.seaLevelOffset()));
	}

	private static double override(Map<String, Double> overrides, String key, double base) {
		Double value = overrides.get(key);
		return value == null ? base : value;
	}

	private static int seaLevelOverride(Map<String, Double> overrides, int base) {
		Double value = overrides.get("sea_level_offset");
		if (value == null) {
			return base;
		}
		if (!Double.isFinite(value) || value != Math.rint(value) || value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
			throw new IllegalArgumentException("sea_level_offset override must be a finite integer");
		}
		return value.intValue();
	}

	private static boolean isKnownParameter(String key) {
		return switch (key) {
			case "density_offset", "density_scale", "ridge_scale", "continent_scale", "erosion_scale",
					"temperature_shift", "vegetation_shift", "sea_level_offset" -> true;
			default -> false;
		};
	}
}
