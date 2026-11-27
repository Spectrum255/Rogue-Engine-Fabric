package spectrum255.rogueengine.map;

import java.util.List;
import java.util.Objects;

import net.minecraft.resources.Identifier;

public record MapPoolDrawState(
		Identifier poolId,
		String poolHash,
		List<Identifier> remaining,
		long cycle) {
	public MapPoolDrawState {
		Objects.requireNonNull(poolId, "poolId");
		Objects.requireNonNull(poolHash, "poolHash");
		remaining = List.copyOf(remaining);
	}
}
