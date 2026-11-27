package spectrum255.rogueengine.map;

import java.util.Objects;

import net.minecraft.resources.Identifier;

public record MapSelection(
		Identifier poolId,
		String poolHash,
		Identifier sceneId,
		int weight,
		long seed,
		long cycle) {
	public MapSelection {
		Objects.requireNonNull(poolId, "poolId");
		Objects.requireNonNull(poolHash, "poolHash");
		Objects.requireNonNull(sceneId, "sceneId");
	}
}
