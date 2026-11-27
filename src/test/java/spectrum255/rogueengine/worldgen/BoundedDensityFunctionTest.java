package spectrum255.rogueengine.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunctions;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;

import spectrum255.rogueengine.profile.TerrainBoundary;

class BoundedDensityFunctionTest {
	@BeforeAll
	static void bootstrapMinecraft() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	void masksOutsideBoundaryAndRoundTrips() {
		TerrainBoundary boundary = new TerrainBoundary(0, 0, 16, 16,
				TerrainBoundary.Shape.CIRCLE, TerrainBoundary.OutsideMode.VOID);
		BoundedDensityFunction function = new BoundedDensityFunction(DensityFunctions.constant(0.5F), boundary);
		DensitySampler sampler = function.compileSampler(null);

		assertEquals(0.5F, sampler.sampleValue(SamplerContext.EMPTY_UNCACHED, 0, 64, 0));
		assertEquals(0.5F, sampler.sampleValue(SamplerContext.EMPTY_UNCACHED, 16, 64, 0));
		assertEquals(-1.0F, sampler.sampleValue(SamplerContext.EMPTY_UNCACHED, 17, 64, 0));

		JsonElement json = BoundedDensityFunction.CODEC.codec().encodeStart(JsonOps.INSTANCE, function).getOrThrow();
		assertEquals(function, BoundedDensityFunction.CODEC.codec().parse(JsonOps.INSTANCE, json).getOrThrow());
	}
}
