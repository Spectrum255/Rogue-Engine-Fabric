package spectrum255.rogueengine.worldgen;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;

import spectrum255.rogueengine.RogueEngineFabric;

public final class WorldGenRegistry {
	private static boolean densityFunctionsRegistered;
	private static boolean generatorsRegistered;

	private WorldGenRegistry() {
	}

	public static synchronized void register() {
		if (!densityFunctionsRegistered) {
			Registry.register(BuiltInRegistries.DENSITY_FUNCTION_TYPE,
					RogueEngineFabric.id("bounded_density"), BoundedDensityFunction.CODEC);
			Registry.register(BuiltInRegistries.DENSITY_FUNCTION_TYPE,
					RogueEngineFabric.id("central_island_density"), CentralIslandDensityFunction.CODEC);
			densityFunctionsRegistered = true;
		}
		if (!generatorsRegistered) {
			registerGenerators();
			generatorsRegistered = true;
		}
	}

	private static void registerGenerators() {
		Registry.register(BuiltInRegistries.CHUNK_GENERATOR,
				RogueEngineFabric.id("bounded_noise"), BoundedNoiseGenerator.CODEC);
		Registry.register(BuiltInRegistries.CHUNK_GENERATOR,
				RogueEngineFabric.id("central_island"), CentralIslandGenerator.CODEC);
	}
}
