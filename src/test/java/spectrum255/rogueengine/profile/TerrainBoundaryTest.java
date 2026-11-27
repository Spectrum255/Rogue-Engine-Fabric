package spectrum255.rogueengine.profile;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import net.minecraft.world.level.ChunkPos;

class TerrainBoundaryTest {
	@Test
	void circleContainsPointsAndIntersectsChunks() {
		TerrainBoundary boundary = new TerrainBoundary(0, 0, 16, 16,
				TerrainBoundary.Shape.CIRCLE, TerrainBoundary.OutsideMode.VOID);

		assertTrue(boundary.contains(0, 0));
		assertTrue(boundary.contains(16, 0));
		assertFalse(boundary.contains(17, 0));
		assertTrue(boundary.intersects(new ChunkPos(1, 0)));
		assertFalse(boundary.intersects(new ChunkPos(2, 0)));
	}

	@Test
	void ellipseUsesIndependentRadii() {
		TerrainBoundary boundary = new TerrainBoundary(10, -4, 32, 16,
				TerrainBoundary.Shape.ELLIPSE, TerrainBoundary.OutsideMode.BEDROCK_FLOOR);

		assertTrue(boundary.contains(42, -4));
		assertTrue(boundary.contains(10, 12));
		assertFalse(boundary.contains(10, 13));
	}

	@Test
	void validatesShapeRadiiAndHardBorderContainment() {
		TerrainBoundary valid = new TerrainBoundary(0, 0, 128, 128,
				TerrainBoundary.Shape.CIRCLE, TerrainBoundary.OutsideMode.VOID);
		assertTrue(valid.validate().result().isPresent());
		assertTrue(valid.validateWithin(new BoxProfile.Border(0, 0, 256, 16)).result().isPresent());
		assertFalse(valid.validateWithin(new BoxProfile.Border(0, 0, 128, 16)).result().isPresent());

		TerrainBoundary mismatchedCircle = new TerrainBoundary(0, 0, 128, 64,
				TerrainBoundary.Shape.CIRCLE, TerrainBoundary.OutsideMode.VOID);
		assertFalse(mismatchedCircle.validate().result().isPresent());
	}
}
