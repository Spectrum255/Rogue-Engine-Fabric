package spectrum255.rogueengine.profile;

import java.util.Objects;
import java.util.Optional;

import com.google.gson.JsonElement;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;

import spectrum255.rogueengine.worldgen.BoundedNoiseGenerator;
import spectrum255.rogueengine.worldgen.CentralIslandGenerator;
import spectrum255.rogueengine.worldgen.FullChaosGenerator;
import spectrum255.rogueengine.worldgen.NoiseChaosGenerator;
import spectrum255.rogueengine.worldgen.PresetChaosGenerator;
import spectrum255.rogueengine.worldgen.WorldGenParameterResolver;
import spectrum255.rogueengine.worldgen.WorldGenParameters;

public record ResolvedBoxProfile(
		BoxProfile profile,
		Holder<DimensionType> dimensionType,
		ChunkGenerator generator,
		BoxProfile materializedProfile,
		Optional<WorldGenParameters> worldGenParameters) {
	public ResolvedBoxProfile {
		Objects.requireNonNull(profile, "profile");
		Objects.requireNonNull(dimensionType, "dimensionType");
		Objects.requireNonNull(generator, "generator");
		Objects.requireNonNull(materializedProfile, "materializedProfile");
		Objects.requireNonNull(worldGenParameters, "worldGenParameters");
	}

	public LevelStem levelStem() {
		return new LevelStem(dimensionType, generator);
	}

	public static ResolvedBoxProfile resolve(BoxProfile profile, HolderLookup.Provider registries,
			HolderLookup.Provider reloadableRegistries, long seed) {
		profile.validate().getOrThrow();
		Holder<DimensionType> dimensionType = registries.lookupOrThrow(Registries.DIMENSION_TYPE)
				.getOrThrow(ResourceKey.create(Registries.DIMENSION_TYPE, profile.dimensionType()));
		if (profile.generator() instanceof BoxProfile.StemGenerator stem) {
			LevelStem levelStem = findStem(registries, stem.id());
			ensureDimensionType(levelStem, profile.dimensionType());
			return plain(profile, dimensionType, levelStem.generator());
		}
		if (profile.generator() instanceof BoxProfile.InlineGenerator inline) {
			return plain(profile, dimensionType, inline.value());
		}
		BoxProfile.TemplateGenerator templateGenerator = (BoxProfile.TemplateGenerator) profile.generator();
		WorldGenTemplate template = WorldGenTemplateCatalog.from(reloadableRegistries).get(templateGenerator.id())
				.orElseThrow(() -> new IllegalArgumentException("Unknown worldgen template: " + templateGenerator.id()));
		LevelStem baseStem = findStem(registries, template.baseStem());
		ensureDimensionType(baseStem, profile.dimensionType());
		if (template.generatorKind().equals("map_pool")) {
			return materializedBase(profile, dimensionType, template, baseStem.generator());
		}
		return materializeTemplate(profile, dimensionType, template, baseStem, seed);
	}

	public static ResolvedBoxProfile materializeTemplate(BoxProfile profile, Holder<DimensionType> dimensionType,
			WorldGenTemplate template, LevelStem baseStem, long seed) {
		profile.validate().getOrThrow();
		if (!(profile.generator() instanceof BoxProfile.TemplateGenerator templateGenerator)) {
			throw new IllegalArgumentException("Profile does not reference a worldgen template");
		}
		template.validate().getOrThrow();
		ensureDimensionType(baseStem, profile.dimensionType());
		if (!(baseStem.generator() instanceof NoiseBasedChunkGenerator noiseGenerator)) {
			throw new IllegalArgumentException("Worldgen templates require a NoiseBasedChunkGenerator base stem");
		}
		JsonElement templateJson = WorldGenTemplateCodec.CODEC.encodeStart(JsonOps.INSTANCE, template).result().orElseThrow();
		String templateHash = ProfileSnapshot.sha256(templateJson.toString());
		WorldGenParameters parameters = WorldGenParameterResolver.resolve(template, templateHash, templateGenerator, seed);
		ChunkGenerator generated = switch (template.generatorKind()) {
			case "noise_chaos" -> NoiseChaosGenerator.create(
					noiseGenerator.getBiomeSource(), noiseGenerator.generatorSettings().value(), parameters.noise());
			case "full_chaos" -> FullChaosGenerator.create(
					noiseGenerator.getBiomeSource(), noiseGenerator.generatorSettings().value(), parameters.noise());
			case "preset_chaos" -> PresetChaosGenerator.create(
					noiseGenerator.getBiomeSource(), noiseGenerator.generatorSettings().value(), parameters.noise());
			case "central_island" -> CentralIslandGenerator.create(
					noiseGenerator, template.terrainBoundary(), template.island());
			default -> throw new IllegalArgumentException("Unsupported worldgen template: " + template.generatorKind());
		};
		ChunkGenerator generator = applyTerrainBoundary(profile, template, generated);
		BoxProfile materialized = new BoxProfile(
				2,
				profile.dimensionType(),
				new BoxProfile.InlineGenerator("inline", generator),
				profile.seed(),
				profile.spawn(),
				profile.border(),
				profile.inventory());
		return new ResolvedBoxProfile(profile, dimensionType, generator, materialized, Optional.of(parameters));
	}

	private static ResolvedBoxProfile plain(BoxProfile profile, Holder<DimensionType> dimensionType, ChunkGenerator generator) {
		return new ResolvedBoxProfile(profile, dimensionType, generator, profile, Optional.empty());
	}

	private static ResolvedBoxProfile materializedBase(BoxProfile profile, Holder<DimensionType> dimensionType,
			WorldGenTemplate template, ChunkGenerator generator) {
		ChunkGenerator bounded = applyTerrainBoundary(profile, template, generator);
		BoxProfile materialized = new BoxProfile(
				profile.schemaVersion(),
				profile.dimensionType(),
				new BoxProfile.InlineGenerator("inline", bounded),
				profile.seed(),
				profile.spawn(),
				profile.border(),
				profile.inventory());
		return new ResolvedBoxProfile(profile, dimensionType, bounded, materialized, Optional.empty());
	}

	private static ChunkGenerator applyTerrainBoundary(BoxProfile profile, WorldGenTemplate template,
			ChunkGenerator generator) {
		TerrainBoundary boundary = template.terrainBoundary();
		if (boundary == null) {
			return generator;
		}
		if (!(generator instanceof NoiseBasedChunkGenerator noiseGenerator)
				&& !(template.generatorKind().equals("central_island") && generator instanceof CentralIslandGenerator)) {
			throw new IllegalArgumentException("Terrain boundaries require a NoiseBasedChunkGenerator");
		}
		DataResult<TerrainBoundary> validation = boundary.validateWithin(profile.border());
		if (validation.result().isEmpty()) {
			throw new IllegalArgumentException(validation.error().orElseThrow().message());
		}
		if (profile.spawn() instanceof BoxProfile.FixedSpawn fixed
				&& !boundary.contains(fixed.x(), fixed.z())) {
			throw new IllegalArgumentException("Fixed spawn must be inside the template terrain boundary");
		}
		if (generator instanceof CentralIslandGenerator) {
			return generator;
		}
		return BoundedNoiseGenerator.create((NoiseBasedChunkGenerator) generator, boundary, boundary.outsideMode());
	}

	private static LevelStem findStem(HolderLookup.Provider registries, Identifier id) {
		return registries.lookupOrThrow(Registries.LEVEL_STEM).listElements()
				.filter(holder -> holder.key().identifier().equals(id))
				.findFirst()
				.map(holder -> holder.value())
				.orElseThrow(() -> new IllegalArgumentException("Unknown LevelStem: " + id));
	}

	private static void ensureDimensionType(LevelStem stem, Identifier expected) {
		if (stem.type().unwrapKey().isPresent()
				&& !stem.type().unwrapKey().orElseThrow().identifier().equals(expected)) {
			throw new IllegalArgumentException("LevelStem dimension type does not match profile: " + expected);
		}
	}
}
