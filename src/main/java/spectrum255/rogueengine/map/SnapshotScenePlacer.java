package spectrum255.rogueengine.map;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntitySpawnRequest;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class SnapshotScenePlacer {
	private SnapshotScenePlacer() {
	}

	public static Prepared prepare(MapSnapshot snapshot, MapScene scene, BlockPos origin) {
		Objects.requireNonNull(snapshot, "snapshot");
		Objects.requireNonNull(scene, "scene");
		Objects.requireNonNull(origin, "origin");
		BoundingBox bounds = SnapshotTransform.bounds(snapshot, scene.rotation(), scene.mirror(), origin);
		if (bounds.minX() < scene.bounds().min().getX() || bounds.minY() < scene.bounds().min().getY()
				|| bounds.minZ() < scene.bounds().min().getZ() || bounds.maxX() > scene.bounds().max().getX()
				|| bounds.maxY() > scene.bounds().max().getY() || bounds.maxZ() > scene.bounds().max().getZ()) {
			throw new IllegalArgumentException("Snapshot placement is outside the map scene bounds: " + bounds
					+ " allowed=" + scene.bounds());
		}
		Map<ChunkPos, List<PlacedBlock>> blocks = new HashMap<>();
		for (MapSnapshot.SnapshotBlock block : snapshot.blocks()) {
			BlockPos pos = SnapshotTransform.transformPos(block.pos(), scene.rotation(), scene.mirror(), origin);
			BlockState state = SnapshotTransform.transformState(snapshot.palette().get(block.paletteIndex()),
					scene.rotation(), scene.mirror());
			blocks.computeIfAbsent(new ChunkPos(pos.getX() >> 4, pos.getZ() >> 4), ignored -> new ArrayList<>()).add(new PlacedBlock(pos, state));
		}
		Map<ChunkPos, List<PlacedBlockEntity>> blockEntities = new HashMap<>();
		for (CompoundTag source : snapshot.blockEntities()) {
			CompoundTag tag = source.copy();
			BlockPos local = new BlockPos(tag.getIntOr("x", 0), tag.getIntOr("y", 0), tag.getIntOr("z", 0));
			BlockPos pos = SnapshotTransform.transformPos(local, scene.rotation(), scene.mirror(), origin);
			tag.putInt("x", pos.getX());
			tag.putInt("y", pos.getY());
			tag.putInt("z", pos.getZ());
			blockEntities.computeIfAbsent(new ChunkPos(pos.getX() >> 4, pos.getZ() >> 4), ignored -> new ArrayList<>())
					.add(new PlacedBlockEntity(pos, tag));
		}
		List<CompoundTag> entities = new ArrayList<>();
		for (CompoundTag source : snapshot.entities()) {
			entities.add(transformEntityRoot(source, scene, origin));
		}
		return new Prepared(bounds, blocks, blockEntities, entities);
	}

	public static void placeChunk(ServerLevel level, Prepared prepared, ChunkPos chunkPos) {
		LevelChunk chunk = (LevelChunk) level.getChunk(chunkPos.x(), chunkPos.z(), ChunkStatus.FULL, true);
		BoundingBox area = intersection(prepared.bounds(), chunkPos);
		for (int y = area.minY(); y <= area.maxY(); y++) {
			for (int x = area.minX(); x <= area.maxX(); x++) {
				for (int z = area.minZ(); z <= area.maxZ(); z++) {
					chunk.setBlockState(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
				}
			}
		}
		AABB entityBounds = new AABB(area.minX(), area.minY(), area.minZ(),
				area.maxX() + 1.0D, area.maxY() + 1.0D, area.maxZ() + 1.0D);
		List<Entity> entities = new ArrayList<>();
		level.getEntities(EntityTypeTest.forClass(Entity.class), entityBounds, entity -> true, entities);
		entities.forEach(Entity::discard);
		for (PlacedBlock block : prepared.blocks().getOrDefault(chunkPos, List.of())) {
			chunk.setBlockState(block.pos(), block.state(), 3);
		}
		for (PlacedBlockEntity blockEntity : prepared.blockEntities().getOrDefault(chunkPos, List.of())) {
			BlockEntity loaded = BlockEntity.loadStatic(blockEntity.pos(),
					chunk.getBlockState(blockEntity.pos()), blockEntity.tag(), level.registryAccess());
			if (loaded != null) {
				chunk.setBlockEntity(loaded);
			}
		}
	}

	public static Entity spawnEntity(ServerLevel level, CompoundTag tag) {
		Entity entity = EntityType.loadEntityRecursive(tag, level,
				new EntitySpawnRequest(EntitySpawnReason.LOAD, true), loaded -> {
					loaded.setUUID(java.util.UUID.randomUUID());
					return loaded;
				});
		if (entity == null) {
			throw new IllegalArgumentException("Could not restore snapshot entity: " + tag.getStringOr("id", ""));
		}
		if (!level.addFreshEntity(entity)) {
			throw new IllegalStateException("Could not add snapshot entity to the level");
		}
		return entity;
	}

	private static BoundingBox intersection(BoundingBox bounds, ChunkPos chunkPos) {
		int minX = Math.max(bounds.minX(), chunkPos.getMinBlockX());
		int minZ = Math.max(bounds.minZ(), chunkPos.getMinBlockZ());
		int maxX = Math.min(bounds.maxX(), chunkPos.getMaxBlockX());
		int maxZ = Math.min(bounds.maxZ(), chunkPos.getMaxBlockZ());
		if (minX > maxX || minZ > maxZ) {
			throw new IllegalArgumentException("Snapshot chunk does not intersect the placement bounds");
		}
		return new BoundingBox(minX, bounds.minY(), minZ, maxX, bounds.maxY(), maxZ);
	}

	private static CompoundTag transformEntityRoot(CompoundTag source, MapScene scene, BlockPos origin) {
		CompoundTag tag = source.copy();
		if (tag.get("Pos") instanceof ListTag position && position.size() == 3) {
			Vec3 local = new Vec3(((NumericTag) position.get(0)).doubleValue(),
					((NumericTag) position.get(1)).doubleValue(), ((NumericTag) position.get(2)).doubleValue());
			Vec3 world = StructureTemplate.transform(local, scene.mirror(), scene.rotation(), BlockPos.ZERO)
					.add(origin.getX(), origin.getY(), origin.getZ());
			ListTag transformed = new ListTag();
			transformed.add(DoubleTag.valueOf(world.x));
			transformed.add(DoubleTag.valueOf(world.y));
			transformed.add(DoubleTag.valueOf(world.z));
			tag.put("Pos", transformed);
		}
		if (tag.get("Rotation") instanceof ListTag rotation && rotation.size() == 2) {
			float yaw = ((NumericTag) rotation.get(0)).floatValue();
			yaw = switch (scene.mirror()) {
				case LEFT_RIGHT -> 180.0F - yaw;
				case FRONT_BACK -> -yaw;
				case NONE -> yaw;
			};
			yaw = switch (scene.rotation()) {
				case CLOCKWISE_90 -> yaw - 90.0F;
				case CLOCKWISE_180 -> yaw + 180.0F;
				case COUNTERCLOCKWISE_90 -> yaw + 90.0F;
				case NONE -> yaw;
			};
			ListTag transformed = new ListTag();
			transformed.add(net.minecraft.nbt.FloatTag.valueOf(yaw));
			transformed.add(rotation.get(1).copy());
			tag.put("Rotation", transformed);
		}
		return tag;
	}

	public record Prepared(BoundingBox bounds, Map<ChunkPos, List<PlacedBlock>> blocks,
			Map<ChunkPos, List<PlacedBlockEntity>> blockEntities, List<CompoundTag> entities) {
	}

	public record PlacedBlock(BlockPos pos, BlockState state) {
	}

	public record PlacedBlockEntity(BlockPos pos, CompoundTag tag) {
	}
}
