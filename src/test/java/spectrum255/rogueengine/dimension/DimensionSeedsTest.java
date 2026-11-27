package spectrum255.rogueengine.dimension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.Level;

import spectrum255.rogueengine.instance.BoxInstance;

class DimensionSeedsTest {
	@BeforeAll
	static void bootstrapMinecraft() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	void onlyRegisteredDynamicDimensionsOverrideSeed() {
		ResourceKey<Level> key = BoxInstance.dimensionKey(UUID.randomUUID());
		DimensionSeeds.register(key, 123L);

		assertEquals(123L, DimensionSeeds.seedFor(key).orElseThrow());
		assertTrue(DimensionSeeds.seedFor(Level.OVERWORLD).isEmpty());

		DimensionSeeds.unregister(key);
		assertTrue(DimensionSeeds.seedFor(key).isEmpty());
	}

	@Test
	void rejectsForeignNamespace() {
		ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, Identifier.withDefaultNamespace("overworld"));
		try {
			DimensionSeeds.register(key, 1L);
			throw new AssertionError("Expected foreign dimension registration to fail");
		} catch (IllegalArgumentException expected) {
			assertEquals("Only Rogue Engine dimensions can register instance seeds", expected.getMessage());
		}
	}
}
