package spectrum255.rogueengine.profile;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;

class CentralIslandSettingsTest {
	@BeforeAll
	static void bootstrapMinecraft() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	void validatesIslandShapeAndLayerDepths() {
		CentralIslandSettings.CustomLayers layers = layers();
		CentralIslandSettings settings = new CentralIslandSettings(
				CentralIslandSettings.SurfaceMode.CUSTOM_LAYERS, 128, 64, 1.5, 4.0, 0.35,
				Optional.of(layers));

		assertTrue(settings.validate().result().isPresent());
		assertFalse(new CentralIslandSettings(
				CentralIslandSettings.SurfaceMode.CUSTOM_LAYERS, 128, 64, 1.5, 4.0, 0.35,
				Optional.empty()).validate().result().isPresent());
		assertFalse(new CentralIslandSettings(
				CentralIslandSettings.SurfaceMode.VANILLA_SURFACE, 128, 64, 1.5, 4.0, 0.35,
				Optional.of(layers)).validate().result().isPresent());
	}

	@Test
	void rejectsInvalidRanges() {
		assertFalse(new CentralIslandSettings(
				CentralIslandSettings.SurfaceMode.VANILLA_SURFACE, 400, 64, 1.5, 4.0, 0.35,
				Optional.empty()).validate().result().isPresent());
		assertFalse(new CentralIslandSettings(
				CentralIslandSettings.SurfaceMode.VANILLA_SURFACE, 128, 8, 1.5, 4.0, 0.35,
				Optional.empty()).validate().result().isPresent());
		assertFalse(new CentralIslandSettings(
				CentralIslandSettings.SurfaceMode.VANILLA_SURFACE, 128, 64, 0.1, 4.0, 0.35,
				Optional.empty()).validate().result().isPresent());
	}

	private CentralIslandSettings.CustomLayers layers() {
		return new CentralIslandSettings.CustomLayers(
				Blocks.GRASS_BLOCK.defaultBlockState(), Blocks.DIRT.defaultBlockState(),
				Blocks.STONE.defaultBlockState(), 1, 3, Optional.of(Blocks.COAL_ORE.defaultBlockState()),
				0.02, 4, 32);
	}
}
