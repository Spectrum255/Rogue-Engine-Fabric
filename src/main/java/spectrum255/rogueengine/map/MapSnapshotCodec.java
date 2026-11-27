package spectrum255.rogueengine.map;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.level.block.state.BlockState;

public final class MapSnapshotCodec {
	private MapSnapshotCodec() {
	}

	public static CompoundTag write(MapSnapshot snapshot, HolderLookup.Provider registries) {
		CompoundTag root = new CompoundTag();
		root.putInt("schema_version", 1);
		root.put("size", intList(snapshot.size()));
		var ops = RegistryOps.create(NbtOps.INSTANCE, registries);
		ListTag palette = new ListTag();
		for (BlockState state : snapshot.palette()) {
			palette.add(BlockState.FULL_CODEC.encodeStart(ops, state).result().orElseThrow());
		}
		root.put("palette", palette);
		ListTag blocks = new ListTag();
		for (MapSnapshot.SnapshotBlock block : snapshot.blocks()) {
			CompoundTag entry = new CompoundTag();
			entry.put("pos", intList(new Vec3i(block.pos().getX(), block.pos().getY(), block.pos().getZ())));
			entry.putInt("state", block.paletteIndex());
			blocks.add(entry);
		}
		root.put("blocks", blocks);
		root.put("block_entities", compounds(snapshot.blockEntities()));
		root.put("entities", compounds(snapshot.entities()));
		return root;
	}

	public static MapSnapshot read(CompoundTag root, HolderLookup.Provider registries) {
		if (root.getIntOr("schema_version", 0) != 1) {
			throw new IllegalArgumentException("Unsupported map snapshot schema");
		}
		Vec3i size = vec3(root, "size");
		var ops = RegistryOps.create(NbtOps.INSTANCE, registries);
		List<BlockState> palette = new ArrayList<>();
		for (Tag element : root.getListOrEmpty("palette")) {
			var parsed = BlockState.FULL_CODEC.parse(ops, element);
			palette.add(parsed.result().orElseThrow(() -> new IllegalArgumentException(
					"Invalid map snapshot block state: " + parsed.error().orElseThrow().message())));
		}
		List<MapSnapshot.SnapshotBlock> blocks = new ArrayList<>();
		Set<BlockPos> blockPositions = new HashSet<>();
		for (Tag element : root.getListOrEmpty("blocks")) {
			if (!(element instanceof CompoundTag entry)) {
				throw new IllegalArgumentException("Invalid map snapshot block entry");
			}
			BlockPos pos = blockPos(vec3(entry, "pos"));
			blocks.add(new MapSnapshot.SnapshotBlock(pos, entry.getIntOr("state", -1)));
			blockPositions.add(pos);
		}
		List<CompoundTag> blockEntities = readCompounds(root, "block_entities");
		for (CompoundTag blockEntity : blockEntities) {
			BlockPos pos = new BlockPos(blockEntity.getIntOr("x", Integer.MIN_VALUE),
					blockEntity.getIntOr("y", Integer.MIN_VALUE), blockEntity.getIntOr("z", Integer.MIN_VALUE));
			if (!blockPositions.contains(pos)) {
				throw new IllegalArgumentException("Map snapshot block entity has no matching block");
			}
		}
		List<CompoundTag> entities = readCompounds(root, "entities");
		for (CompoundTag entity : entities) {
			validateEntity(entity, 0, true);
		}
		return new MapSnapshot(1, size, palette, blocks, blockEntities, entities);
	}

	private static void validateEntity(CompoundTag entity, int depth, boolean requirePosition) {
		if (depth > 64) {
			throw new IllegalArgumentException("Map snapshot passenger nesting is too deep");
		}
		String id = entity.getStringOr("id", "");
		if (id.isBlank() || id.equals("minecraft:player")) {
			throw new IllegalArgumentException("Invalid or player map snapshot entity");
		}
		if (requirePosition && !validPosition(entity.get("Pos"))) {
			throw new IllegalArgumentException("Map snapshot entity is missing a local position");
		}
		for (Tag passenger : entity.getListOrEmpty("Passengers")) {
			if (!(passenger instanceof CompoundTag tag)) {
				throw new IllegalArgumentException("Invalid map snapshot passenger");
			}
			validateEntity(tag, depth + 1, false);
		}
	}

	private static boolean validPosition(Tag tag) {
		if (!(tag instanceof ListTag list) || list.size() != 3) {
			return false;
		}
		for (Tag element : list) {
			if (!(element instanceof NumericTag number) || !Double.isFinite(number.doubleValue())) {
				return false;
			}
		}
		return true;
	}

	private static ListTag intList(Vec3i value) {
		ListTag list = new ListTag();
		list.add(net.minecraft.nbt.IntTag.valueOf(value.getX()));
		list.add(net.minecraft.nbt.IntTag.valueOf(value.getY()));
		list.add(net.minecraft.nbt.IntTag.valueOf(value.getZ()));
		return list;
	}

	private static Vec3i vec3(CompoundTag root, String key) {
		if (!(root.get(key) instanceof ListTag list) || list.size() != 3) {
			throw new IllegalArgumentException("Invalid map snapshot vector: " + key);
		}
		return new Vec3i(list.getIntOr(0, 0), list.getIntOr(1, 0), list.getIntOr(2, 0));
	}

	private static BlockPos blockPos(Vec3i value) {
		return new BlockPos(value.getX(), value.getY(), value.getZ());
	}

	private static List<CompoundTag> readCompounds(CompoundTag root, String key) {
		List<CompoundTag> result = new ArrayList<>();
		for (Tag element : root.getListOrEmpty(key)) {
			if (!(element instanceof CompoundTag tag)) {
				throw new IllegalArgumentException("Invalid map snapshot compound list: " + key);
			}
			result.add(tag.copy());
		}
		return result;
	}

	private static ListTag compounds(List<CompoundTag> values) {
		ListTag list = new ListTag();
		values.forEach(value -> list.add(value.copy()));
		return list;
	}
}
