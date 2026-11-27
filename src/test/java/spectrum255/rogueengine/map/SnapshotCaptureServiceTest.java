package spectrum255.rogueengine.map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

class SnapshotCaptureServiceTest {
	@Test
	void rejectsOversizedRegion() {
		assertThrows(IllegalArgumentException.class, () -> SnapshotCaptureService.validateRegion(
				new BlockPos(0, 0, 0), new BlockPos(512, 0, 0)));
	}

	@Test
	void acceptsClosedRegion() {
		assertDoesNotThrow(() -> SnapshotCaptureService.validateRegion(
				new BlockPos(0, 0, 0), new BlockPos(4, 1, 4)));
	}

	@Test
	void convertsAbsoluteBlockPositionsToSnapshotLocalCoordinates() {
		BoundingBox region = new BoundingBox(0, 128, 0, 4, 128, 4);
		assertEquals(new BlockPos(2, 0, 3), SnapshotCaptureService.localizeBlock(new BlockPos(2, 128, 3), region));
	}
}
