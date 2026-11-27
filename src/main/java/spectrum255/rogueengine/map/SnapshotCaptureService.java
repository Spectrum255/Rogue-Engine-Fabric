package spectrum255.rogueengine.map;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;

import spectrum255.rogueengine.persistence.AtomicFiles;

public final class SnapshotCaptureService {
	private static final int MAX_FILE_BYTES = 16 * 1024 * 1024;
	private final Path worldRoot;
	private final HolderLookup.Provider registries;

	public SnapshotCaptureService(Path worldRoot, HolderLookup.Provider registries) {
		this.worldRoot = worldRoot.toAbsolutePath().normalize();
		this.registries = Objects.requireNonNull(registries, "registries");
	}

	public static BoundingBox validateRegion(BlockPos from, BlockPos to) {
		Objects.requireNonNull(from, "from");
		Objects.requireNonNull(to, "to");
		int minX = Math.min(from.getX(), to.getX());
		int minY = Math.min(from.getY(), to.getY());
		int minZ = Math.min(from.getZ(), to.getZ());
		int maxX = Math.max(from.getX(), to.getX());
		int maxY = Math.max(from.getY(), to.getY());
		int maxZ = Math.max(from.getZ(), to.getZ());
		long sizeX = (long) maxX - minX + 1L;
		long sizeY = (long) maxY - minY + 1L;
		long sizeZ = (long) maxZ - minZ + 1L;
		if (sizeX > 512L || sizeY > 512L || sizeZ > 512L || sizeX * sizeY * sizeZ > 1_000_000L) {
			throw new IllegalArgumentException("Snapshot region is too large");
		}
		return new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
	}

	static BlockPos localizeBlock(BlockPos pos, BoundingBox region) {
		return pos.offset(-region.minX(), -region.minY(), -region.minZ());
	}

	public Captured capture(ServerLevel level, BlockPos from, BlockPos to, Identifier id) throws IOException {
		BoundingBox region = validateRegion(from, to);
		Map<BlockState, Integer> palette = new LinkedHashMap<>();
		List<MapSnapshot.SnapshotBlock> blocks = new ArrayList<>();
		List<CompoundTag> blockEntities = new ArrayList<>();
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		for (int y = region.minY(); y <= region.maxY(); y++) {
			for (int x = region.minX(); x <= region.maxX(); x++) {
				for (int z = region.minZ(); z <= region.maxZ(); z++) {
					cursor.set(x, y, z);
					BlockState state = level.getBlockState(cursor);
					if (state.isAir()) {
						continue;
					}
					int paletteIndex = palette.computeIfAbsent(state, ignored -> palette.size());
					blocks.add(new MapSnapshot.SnapshotBlock(localizeBlock(cursor, region), paletteIndex));
					CompoundTag blockEntity = level.getChunkAt(cursor).getBlockEntityNbtForSaving(cursor, registries);
					if (blockEntity != null) {
						blockEntity.putInt("x", x - region.minX());
						blockEntity.putInt("y", y - region.minY());
						blockEntity.putInt("z", z - region.minZ());
						blockEntities.add(blockEntity);
					}
				}
			}
		}
		if (palette.isEmpty()) {
			palette.put(Blocks.AIR.defaultBlockState(), 0);
		}
		if (palette.size() > 4096 || blockEntities.size() > 100_000) {
			throw new IllegalArgumentException("Snapshot palette or block entity count is too large");
		}

		AABB bounds = new AABB(region.minX(), region.minY(), region.minZ(),
				region.maxX() + 1.0D, region.maxY() + 1.0D, region.maxZ() + 1.0D);
		List<Entity> entities = new ArrayList<>();
		level.getEntities(EntityTypeTest.forClass(Entity.class), bounds,
				entity -> !(entity instanceof ServerPlayer) && entity.getVehicle() == null, entities);
		if (entities.size() > 100_000) {
			throw new IllegalArgumentException("Snapshot entity count is too large");
		}
		List<CompoundTag> entityTags = new ArrayList<>();
		for (Entity entity : entities) {
			TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
			if (!entity.save(output)) {
				throw new IllegalArgumentException("Entity cannot be captured: " + entity.getUUID());
			}
			CompoundTag tag = output.buildResult();
			localizeEntity(tag, region);
			stripUuidAndRejectPlayer(tag, 0);
			entityTags.add(tag);
		}

		Vec3i size = new Vec3i((int) (region.maxX() - region.minX() + 1L),
				(int) (region.maxY() - region.minY() + 1L), (int) (region.maxZ() - region.minZ() + 1L));
		MapSnapshot snapshot = new MapSnapshot(1, size, new ArrayList<>(palette.keySet()), blocks, blockEntities, entityTags);
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		NbtIo.writeCompressed(MapSnapshotCodec.write(snapshot, registries), output);
		if (output.size() > MAX_FILE_BYTES) {
			throw new IllegalArgumentException("Snapshot file exceeds " + MAX_FILE_BYTES + " bytes");
		}
		Path file = worldRoot.resolve("data/rogue-engine-fabric/map-snapshots")
				.resolve(id.getNamespace()).resolve(id.getPath() + ".nbt");
		AtomicFiles.verifyManagedPath(worldRoot, file);
		AtomicFiles.writeAtomic(file, output.toByteArray());
		return new Captured(file, sha256(output.toByteArray()), blocks.size(), blockEntities.size(), entityTags.size());
	}

	private static void localizeEntity(CompoundTag entity, BoundingBox region) {
		if (entity.get("Pos") instanceof ListTag position && position.size() == 3) {
			ListTag local = new ListTag();
			local.add(DoubleTag.valueOf(((NumericTag) position.get(0)).doubleValue() - region.minX()));
			local.add(DoubleTag.valueOf(((NumericTag) position.get(1)).doubleValue() - region.minY()));
			local.add(DoubleTag.valueOf(((NumericTag) position.get(2)).doubleValue() - region.minZ()));
			entity.put("Pos", local);
		}
		for (Tag passenger : entity.getListOrEmpty("Passengers")) {
			if (passenger instanceof CompoundTag tag) {
				localizeEntity(tag, region);
			}
		}
	}

	private static void stripUuidAndRejectPlayer(CompoundTag entity, int depth) {
		if (depth > 64) {
			throw new IllegalArgumentException("Snapshot passenger nesting is too deep");
		}
		if (entity.getStringOr("id", "").equals("minecraft:player")) {
			throw new IllegalArgumentException("Player entities cannot be captured");
		}
		entity.remove("UUID");
		for (Tag passenger : entity.getListOrEmpty("Passengers")) {
			if (passenger instanceof CompoundTag tag) {
				stripUuidAndRejectPlayer(tag, depth + 1);
			}
		}
	}

	private static String sha256(byte[] bytes) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is unavailable", exception);
		}
	}

	public record Captured(Path file, String hash, int blocks, int blockEntities, int entities) {
	}
}
