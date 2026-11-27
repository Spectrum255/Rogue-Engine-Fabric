package spectrum255.rogueengine.map;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

class SnapshotTransformTest {
	@BeforeAll
	static void bootstrap() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	void rotatesBoundsAroundAnchor() {
		MapSnapshot snapshot = new MapSnapshot(1, new Vec3i(2, 1, 3),
				List.of(Blocks.STONE.defaultBlockState()), List.of(), List.of(), List.of());

		BoundingBox box = SnapshotTransform.bounds(snapshot, Rotation.CLOCKWISE_90, Mirror.NONE, new BlockPos(10, 64, 20));

		assertEquals(3, box.getXSpan());
		assertEquals(2, box.getZSpan());
		assertEquals(1, box.getYSpan());
		assertEquals(64, box.minY());
	}

	@Test
	void rotatesFacing() {
		BlockState state = Blocks.PISTON.defaultBlockState().setValue(DirectionalBlock.FACING, Direction.NORTH);

		BlockState transformed = SnapshotTransform.transformState(state, Rotation.CLOCKWISE_90, Mirror.NONE);

		assertEquals(Direction.EAST, transformed.getValue(DirectionalBlock.FACING));
	}
}
