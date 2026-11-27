package spectrum255.rogueengine.map;

import java.util.List;
import java.util.Objects;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

public final class SnapshotTransform {
	private SnapshotTransform() {
	}

	public static BlockPos transformPos(BlockPos local, Rotation rotation, Mirror mirror, BlockPos anchor) {
		return StructureTemplate.transform(local, mirror, rotation, BlockPos.ZERO).offset(anchor);
	}

	public static BoundingBox bounds(MapSnapshot snapshot, Rotation rotation, Mirror mirror, BlockPos anchor) {
		Objects.requireNonNull(snapshot, "snapshot");
		BoundingBox local = snapshot.localBounds();
		BoundingBox result = null;
		for (BlockPos corner : List.of(new BlockPos(local.minX(), local.minY(), local.minZ()),
				new BlockPos(local.maxX(), local.maxY(), local.maxZ()),
				new BlockPos(local.minX(), local.maxY(), local.minZ()),
				new BlockPos(local.maxX(), local.minY(), local.maxZ()),
				new BlockPos(local.minX(), local.minY(), local.maxZ()),
				new BlockPos(local.maxX(), local.maxY(), local.minZ()),
				new BlockPos(local.minX(), local.maxY(), local.maxZ()),
				new BlockPos(local.maxX(), local.minY(), local.minZ()))) {
			BlockPos world = transformPos(corner, rotation, mirror, anchor);
			BoundingBox point = new BoundingBox(world);
			result = result == null ? point : result.encapsulate(point);
		}
		return result;
	}

	public static BlockState transformState(BlockState state, Rotation rotation, Mirror mirror) {
		if (!state.hasProperty(DirectionalBlock.FACING)) {
			return state;
		}
		Direction direction = mirror.mirror(state.getValue(DirectionalBlock.FACING));
		return state.setValue(DirectionalBlock.FACING, rotation.rotate(direction));
	}
}
