package spectrum255.rogueengine.instance;

import java.util.Objects;
import java.util.UUID;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import spectrum255.rogueengine.RogueEngineFabric;

public record BoxInstance(
		UUID id,
		String name,
		ResourceKey<Level> dimensionKey,
		BoxState state,
		long stateVersion) {
	private static final int MAX_NAME_LENGTH = 32;

	public BoxInstance {
		Objects.requireNonNull(id, "id");
		if (name == null || name.isBlank() || name.codePointCount(0, name.length()) > MAX_NAME_LENGTH) {
			throw new IllegalArgumentException("Box name must contain 1 to 32 characters");
		}
		if (name.chars().anyMatch(Character::isISOControl)) {
			throw new IllegalArgumentException("Box name cannot contain control characters");
		}
		Objects.requireNonNull(dimensionKey, "dimensionKey");
		if (!RogueEngineFabric.MOD_ID.equals(dimensionKey.identifier().getNamespace())) {
			throw new IllegalArgumentException("Box dimension key must use the engine namespace");
		}
		Objects.requireNonNull(state, "state");
		if (stateVersion < 1) {
			throw new IllegalArgumentException("stateVersion must be positive");
		}
	}

	public static BoxInstance create(UUID id, String name) {
		return new BoxInstance(id, name, dimensionKey(id), BoxState.CREATING, 1);
	}

	public BoxInstance withState(BoxState target, long targetVersion) {
		if (targetVersion <= stateVersion) {
			throw new IllegalArgumentException("targetVersion must be greater than the current stateVersion");
		}
		if (!state.canTransitionTo(target)) {
			throw new IllegalArgumentException("Illegal box state transition: " + state + " -> " + target);
		}
		return new BoxInstance(id, name, dimensionKey, target, targetVersion);
	}

	public static ResourceKey<Level> dimensionKey(UUID id) {
		Objects.requireNonNull(id, "id");
		String path = "instance/" + id.toString().replace("-", "").toLowerCase(java.util.Locale.ROOT);
		return ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath(RogueEngineFabric.MOD_ID, path));
	}
}
