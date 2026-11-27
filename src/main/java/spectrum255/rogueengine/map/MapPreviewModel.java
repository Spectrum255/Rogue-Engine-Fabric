package spectrum255.rogueengine.map;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.material.MapColor;

public record MapPreviewModel(int width, int depth, int[] heights, int[] colors) {
	public MapPreviewModel {
		if (width < 1 || width > 512 || depth < 1 || depth > 512
				|| heights.length != width * depth || colors.length != width * depth) {
			throw new IllegalArgumentException("Invalid map preview dimensions");
		}
		heights = heights.clone();
		colors = colors.clone();
	}

	public int heightAt(int x, int z) {
		return heights[z * width + x];
	}

	public int colorAt(int x, int z) {
		return colors[z * width + x];
	}

	public static MapPreviewModel fromSnapshot(MapSnapshot snapshot, Rotation rotation, Mirror mirror) {
		Objects.requireNonNull(snapshot, "snapshot");
		List<PreviewBlock> blocks = snapshot.blocks().stream()
				.map(block -> new PreviewBlock(block.pos(), snapshot.palette().get(block.paletteIndex())))
				.toList();
		return fromBlocks(snapshot.size(), blocks, rotation, mirror);
	}

	public static MapPreviewModel fromBlocks(Vec3i size, List<PreviewBlock> blocks,
			Rotation rotation, Mirror mirror) {
		Objects.requireNonNull(size, "size");
		Objects.requireNonNull(blocks, "blocks");
		Objects.requireNonNull(rotation, "rotation");
		Objects.requireNonNull(mirror, "mirror");
		BoundingBox bounds = transformedBounds(size, rotation, mirror);
		int width = bounds.maxX() - bounds.minX() + 1;
		int depth = bounds.maxZ() - bounds.minZ() + 1;
		if (width > 512 || depth > 512) {
			throw new IllegalArgumentException("Map preview exceeds 512 blocks per side");
		}
		int[] heights = new int[width * depth];
		int[] colors = new int[width * depth];
		Arrays.fill(heights, Integer.MIN_VALUE);
		for (PreviewBlock block : blocks) {
			if (block.state().isAir()) {
				continue;
			}
			BlockPos transformed = SnapshotTransform.transformPos(block.pos(), rotation, mirror, BlockPos.ZERO);
			int x = transformed.getX() - bounds.minX();
			int z = transformed.getZ() - bounds.minZ();
			if (x < 0 || x >= width || z < 0 || z >= depth) {
				throw new IllegalArgumentException("Preview block is outside transformed bounds");
			}
			int index = z * width + x;
			if (transformed.getY() > heights[index]) {
				heights[index] = transformed.getY();
				colors[index] = block.state().getMapColor(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)
						.calculateARGBColor(MapColor.Brightness.HIGH);
			}
		}
		return new MapPreviewModel(width, depth, heights, colors);
	}

	private static BoundingBox transformedBounds(Vec3i size, Rotation rotation, Mirror mirror) {
		BoundingBox local = new BoundingBox(0, 0, 0, size.getX() - 1, size.getY() - 1, size.getZ() - 1);
		BoundingBox result = null;
		for (BlockPos corner : List.of(new BlockPos(local.minX(), local.minY(), local.minZ()),
				new BlockPos(local.maxX(), local.maxY(), local.maxZ()),
				new BlockPos(local.minX(), local.maxY(), local.minZ()),
				new BlockPos(local.maxX(), local.minY(), local.maxZ()),
				new BlockPos(local.minX(), local.minY(), local.maxZ()),
				new BlockPos(local.maxX(), local.maxY(), local.minZ()),
				new BlockPos(local.minX(), local.maxY(), local.maxZ()),
				new BlockPos(local.maxX(), local.minY(), local.minZ()))) {
			BlockPos transformed = SnapshotTransform.transformPos(corner, rotation, mirror, BlockPos.ZERO);
			BoundingBox point = new BoundingBox(transformed);
			result = result == null ? point : result.encapsulate(point);
		}
		return result;
	}

	public record PreviewBlock(BlockPos pos, BlockState state) {
		public PreviewBlock {
			Objects.requireNonNull(pos, "pos");
			Objects.requireNonNull(state, "state");
		}
	}
}
