package spectrum255.rogueengine.map;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import net.minecraft.nbt.CompoundTag;

public record PlayerSceneState(UUID playerId, UUID instanceId, float health, PlayerFoodState food,
		int airSupply, int experienceLevel, float experienceProgress, int experienceTotal,
		List<CompoundTag> effects, long appliedResetCount) {
	private static final int MAX_EFFECTS = 100;

	public PlayerSceneState {
		Objects.requireNonNull(playerId, "playerId");
		Objects.requireNonNull(instanceId, "instanceId");
		Objects.requireNonNull(food, "food");
		Objects.requireNonNull(effects, "effects");
		if (!Float.isFinite(health) || health <= 0.0F || health > 1_000_000.0F || airSupply < 0
				|| experienceLevel < 0 || !Float.isFinite(experienceProgress)
				|| experienceProgress < 0.0F || experienceProgress > 1.0F || experienceTotal < 0
				|| appliedResetCount < 0L || effects.size() > MAX_EFFECTS) {
			throw new IllegalArgumentException("Invalid player scene state");
		}
		effects = effects.stream().map(CompoundTag::copy).toList();
	}

	public PlayerSceneState withAppliedResetCount(long count) {
		return new PlayerSceneState(playerId, instanceId, health, food, airSupply, experienceLevel,
				experienceProgress, experienceTotal, effects, count);
	}
}
