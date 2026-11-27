package spectrum255.rogueengine.inventory;

import java.util.Objects;
import java.util.UUID;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public record PlayerInventoryRecord(
		int schemaVersion,
		UUID playerId,
		UUID instanceId,
		Phase phase,
		boolean hasOutside,
		PlayerInventorySnapshot outside,
		PlayerInventorySnapshot inside,
		ResourceKey<Level> source,
		ResourceKey<Level> destination) {
	public PlayerInventoryRecord {
		if (schemaVersion != 1) {
			throw new IllegalArgumentException("Unsupported inventory record schema");
		}
		Objects.requireNonNull(playerId, "playerId");
		Objects.requireNonNull(instanceId, "instanceId");
		Objects.requireNonNull(phase, "phase");
		Objects.requireNonNull(outside, "outside");
		Objects.requireNonNull(inside, "inside");
	}

	public enum Phase {
		IDLE,
		ENTERING,
		INSIDE,
		EXITING
	}

	public static ResourceKey<Level> dimension(String value) {
		return ResourceKey.create(Registries.DIMENSION, net.minecraft.resources.Identifier.parse(value));
	}
}
