package spectrum255.rogueengine.dimension;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import spectrum255.rogueengine.RogueEngineFabric;

public final class DimensionSeeds {
	private static final Map<ResourceKey<Level>, Long> SEEDS = new ConcurrentHashMap<>();

	private DimensionSeeds() {
	}

	public static void register(ResourceKey<Level> key, long seed) {
		Identifier id = key.identifier();
		if (!RogueEngineFabric.MOD_ID.equals(id.getNamespace()) || !id.getPath().startsWith("instance/")) {
			throw new IllegalArgumentException("Only Rogue Engine dimensions can register instance seeds");
		}
		SEEDS.put(key, seed);
	}

	public static void unregister(ResourceKey<Level> key) {
		SEEDS.remove(key);
	}

	public static Optional<Long> seedFor(ResourceKey<Level> key) {
		return Optional.ofNullable(SEEDS.get(key));
	}

	public static void clear() {
		SEEDS.clear();
	}
}
