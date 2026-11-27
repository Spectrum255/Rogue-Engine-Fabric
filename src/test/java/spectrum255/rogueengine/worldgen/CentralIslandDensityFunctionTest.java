package spectrum255.rogueengine.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunctions;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;

import spectrum255.rogueengine.profile.CentralIslandSettings;
import spectrum255.rogueengine.profile.TerrainBoundary;

class CentralIslandDensityFunctionTest {
	@BeforeAll
	static void bootstrapMinecraft() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	void createsOneSolidCenterAndVoidOutside() {
		DensityFunction noise = DensityFunctions.zero();
		CentralIslandDensityFunction function = new CentralIslandDensityFunction(noise, boundary(), settings());
		DensitySampler sampler = function.compileSampler(null);

		assertTrue(sampler.sampleValue(SamplerContext.EMPTY_UNCACHED, 0, 128, 0) > 0.0F);
		assertTrue(sampler.sampleValue(SamplerContext.EMPTY_UNCACHED, 0, 129, 0) < 0.0F);
		assertTrue(sampler.sampleValue(SamplerContext.EMPTY_UNCACHED, 0, 96, 0) > 0.0F);
		assertEquals(-1.0F, sampler.sampleValue(SamplerContext.EMPTY_UNCACHED, 200, 128, 0));
	}

	@Test
	void roundTripsThroughOwnCodec() {
		CentralIslandDensityFunction function = new CentralIslandDensityFunction(
				DensityFunctions.zero(), boundary(), settings());
		JsonElement json = CentralIslandDensityFunction.CODEC.codec().encodeStart(JsonOps.INSTANCE, function).getOrThrow();
		assertEquals(function, CentralIslandDensityFunction.CODEC.codec().parse(JsonOps.INSTANCE, json).getOrThrow());
	}

	private TerrainBoundary boundary() {
		return new TerrainBoundary(0, 0, 16, 16,
				TerrainBoundary.Shape.CIRCLE, TerrainBoundary.OutsideMode.VOID);
	}

	private CentralIslandSettings settings() {
		return new CentralIslandSettings(CentralIslandSettings.SurfaceMode.VANILLA_SURFACE,
				128, 64, 1.5, 4.0, 0.35, Optional.empty());
	}
}
