package spectrum255.rogueengine.dimension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.Level;

import spectrum255.rogueengine.instance.BoxInstance;

class PlayerRespawnPolicyTest {
	@BeforeAll
	static void bootstrapMinecraft() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@AfterEach
	void clearSeeds() {
		DimensionSeeds.clear();
	}

	@Test
	void boxDeathWithoutPersonalRespawnUsesFallback() {
		var box = BoxInstance.dimensionKey(UUID.randomUUID());
		DimensionSeeds.register(box, 1L);
		assertTrue(PlayerRespawnPolicy.shouldUseFallback(box));
	}

	@Test
	void boxDeathAlwaysUsesFallback() {
		var box = BoxInstance.dimensionKey(UUID.randomUUID());
		DimensionSeeds.register(box, 1L);
		assertTrue(PlayerRespawnPolicy.shouldUseFallback(box));
	}

	@Test
	void overworldDeathKeepsVanillaRespawn() {
		assertFalse(PlayerRespawnPolicy.shouldUseFallback(Level.OVERWORLD));
	}
}
