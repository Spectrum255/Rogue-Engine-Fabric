package spectrum255.rogueengine.dimension;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public final class PlayerRespawnPolicy {
	private PlayerRespawnPolicy() {
	}

	public static boolean shouldUseFallback(ResourceKey<Level> dimension) {
		return DimensionSeeds.seedFor(dimension).isPresent();
	}
}
