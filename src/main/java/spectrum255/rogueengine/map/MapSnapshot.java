package spectrum255.rogueengine.map;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

public record MapSnapshot(int schemaVersion, Vec3i size, List<BlockState> palette,
		List<SnapshotBlock> blocks, List<CompoundTag> blockEntities, List<CompoundTag> entities) {
	public MapSnapshot {
		Objects.requireNonNull(size, "size");
		Objects.requireNonNull(palette, "palette");
		Objects.requireNonNull(blocks, "blocks");
		Objects.requireNonNull(blockEntities, "blockEntities");
		Objects.requireNonNull(entities, "entities");
		if (schemaVersion != 1 || size.getX() < 1 || size.getY() < 1 || size.getZ() < 1
				|| size.getX() > 512 || size.getY() > 512 || size.getZ() > 512
				|| (long) size.getX() * size.getY() * size.getZ() > 1_000_000L
				|| palette.isEmpty() || palette.size() > 4096 || blocks.size() > 1_000_000
				|| blockEntities.size() > 100_000 || entities.size() > 100_000) {
			throw new IllegalArgumentException("Invalid map snapshot dimensions or counts");
		}
		palette = List.copyOf(palette);
		blocks = List.copyOf(blocks);
		blockEntities = blockEntities.stream().map(CompoundTag::copy).toList();
		entities = entities.stream().map(CompoundTag::copy).toList();
		Set<BlockPos> seen = new HashSet<>();
		BoundingBox bounds = new BoundingBox(0, 0, 0, size.getX() - 1, size.getY() - 1, size.getZ() - 1);
		for (SnapshotBlock block : blocks) {
			if (!seen.add(block.pos()) || block.paletteIndex() < 0 || block.paletteIndex() >= palette.size()
					|| !bounds.isInside(block.pos())) {
				throw new IllegalArgumentException("Invalid map snapshot block");
			}
		}
	}

	public BoundingBox localBounds() {
		return new BoundingBox(0, 0, 0, size.getX() - 1, size.getY() - 1, size.getZ() - 1);
	}

	public record SnapshotBlock(BlockPos pos, int paletteIndex) {
		public SnapshotBlock {
			Objects.requireNonNull(pos, "pos");
		}
	}
}
