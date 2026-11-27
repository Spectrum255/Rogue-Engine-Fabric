package spectrum255.rogueengine.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.food.FoodData;

class PlayerFoodStateTest {
	@BeforeAll
	static void bootstrap() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	void capturesAndRestoresAllVanillaFoodFields() {
		FoodData food = new FoodData();
		food.setFoodLevel(17);
		food.setSaturation(6.5F);
		food.addExhaustion(3.25F);

		PlayerFoodState state = PlayerFoodState.capture(food);
		FoodData restored = new FoodData();
		restored.setFoodLevel(2);
		restored.setSaturation(0.0F);
		state.apply(restored, registries());

		assertEquals(state, PlayerFoodState.capture(restored));
	}

	@Test
	void rejectsInvalidFoodValues() {
		assertThrows(IllegalArgumentException.class, () -> new PlayerFoodState(21, 0.0F, 0.0F, 0));
	}

	private RegistryAccess registries() {
		return RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
	}
}
