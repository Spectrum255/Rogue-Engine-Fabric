package spectrum255.rogueengine.map;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.mojang.serialization.Codec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.Strategy;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

public final class SavedChunkDecoder {
	private static final Strategy<BlockState> BLOCK_STRATEGY = Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY);
	private static final Codec<PalettedContainer<BlockState>> BLOCK_STATE_CODEC = PalettedContainer.codecRW(
			BlockState.FULL_CODEC, BLOCK_STRATEGY, Blocks.AIR.defaultBlockState());

	private SavedChunkDecoder() {
	}

	public static Decoded decode(CompoundTag chunk, ChunkPos expectedPos, BoundingBox region,
			RegistryAccess registries, boolean includeBlockEntities) {
		Objects.requireNonNull(chunk, "chunk");
		Objects.requireNonNull(expectedPos, "expectedPos");
		Objects.requireNonNull(region, "region");
		Objects.requireNonNull(registries, "registries");
		if (chunk.getIntOr("xPos", Integer.MIN_VALUE) != expectedPos.x()
				|| chunk.getIntOr("zPos", Integer.MIN_VALUE) != expectedPos.z()) {
			throw new IllegalArgumentException("Saved chunk position mismatch");
		}
		ChunkStatus status = ChunkStatus.CODEC.parse(NbtOps.INSTANCE, chunk.get("Status"))
				.result().orElseThrow();
		if (status != ChunkStatus.FULL) {
			throw new IllegalArgumentException("Saved chunk is not full");
		}
		Map<BlockState, Integer> palette = new LinkedHashMap<>();
		List<MapSnapshot.SnapshotBlock> blocks = new ArrayList<>();
		for (Tag element : chunk.getListOrEmpty("sections")) {
			if (!(element instanceof CompoundTag section)) {
				continue;
			}
			int sectionY = section.getByteOr("Y", (byte) 0);
			CompoundTag stateTag = section.getCompoundOrEmpty("block_states");
			PalettedContainer<BlockState> states = BLOCK_STATE_CODEC
					.parse(NbtOps.INSTANCE, stateTag).result().orElseThrow();
			int worldSectionY = sectionY * 16;
			int minY = Math.max(region.minY(), worldSectionY);
			int maxY = Math.min(region.maxY(), worldSectionY + 15);
			int minX = Math.max(region.minX(), expectedPos.getMinBlockX());
			int maxX = Math.min(region.maxX(), expectedPos.getMaxBlockX());
			int minZ = Math.max(region.minZ(), expectedPos.getMinBlockZ());
			int maxZ = Math.min(region.maxZ(), expectedPos.getMaxBlockZ());
			for (int y = minY; y <= maxY; y++) {
				for (int x = minX; x <= maxX; x++) {
					for (int z = minZ; z <= maxZ; z++) {
						BlockState state = states.get(x - expectedPos.getMinBlockX(),
								y - worldSectionY, z - expectedPos.getMinBlockZ());
						if (state.isAir()) {
							continue;
						}
						int index = palette.computeIfAbsent(state, ignored -> palette.size());
						blocks.add(new MapSnapshot.SnapshotBlock(new BlockPos(x - region.minX(),
								y - region.minY(), z - region.minZ()), index));
					}
				}
			}
		}
		List<CompoundTag> blockEntities = new ArrayList<>();
		if (includeBlockEntities) {
			for (Tag element : chunk.getListOrEmpty("block_entities")) {
				if (!(element instanceof CompoundTag source)) {
					continue;
				}
				BlockPos pos = BlockEntity.getPosFromTag(expectedPos, source);
				if (!region.isInside(pos)) {
					continue;
				}
				CompoundTag tag = source.copy();
				tag.putInt("x", pos.getX() - region.minX());
				tag.putInt("y", pos.getY() - region.minY());
				tag.putInt("z", pos.getZ() - region.minZ());
				blockEntities.add(tag);
			}
		}
		return new Decoded(blocks, blockEntities, palette);
	}

	public record Decoded(List<MapSnapshot.SnapshotBlock> blocks, List<CompoundTag> blockEntities,
			Map<BlockState, Integer> palette) {
	}
}
