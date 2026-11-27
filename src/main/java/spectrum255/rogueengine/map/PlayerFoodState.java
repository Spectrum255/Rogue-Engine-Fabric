package spectrum255.rogueengine.map;

import net.minecraft.core.HolderLookup;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;

public record PlayerFoodState(int foodLevel, float saturation, float exhaustion, int tickTimer) {
	public PlayerFoodState {
		if (foodLevel < 0 || foodLevel > 20 || !Float.isFinite(saturation) || saturation < 0.0F
				|| !Float.isFinite(exhaustion) || exhaustion < 0.0F || tickTimer < 0) {
			throw new IllegalArgumentException("Invalid player food state");
		}
	}

	public static PlayerFoodState capture(FoodData food) {
		TagValueOutput output = TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);
		food.addAdditionalSaveData(output);
		var tag = output.buildResult();
		return new PlayerFoodState(tag.getIntOr("foodLevel", 0), tag.getFloatOr("foodSaturationLevel", 0.0F),
				tag.getFloatOr("foodExhaustionLevel", 0.0F), tag.getIntOr("foodTickTimer", 0));
	}

	public void apply(FoodData food, HolderLookup.Provider registries) {
		TagValueOutput output = TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);
		output.putInt("foodLevel", foodLevel);
		output.putInt("foodTickTimer", tickTimer);
		output.putFloat("foodSaturationLevel", saturation);
		output.putFloat("foodExhaustionLevel", exhaustion);
		food.readAdditionalSaveData(TagValueInput.create(ProblemReporter.DISCARDING, registries, output.buildResult()));
	}
}
