package spectrum255.rogueengine.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;

class MapSnapshotCodecTest {
	@BeforeAll
	static void bootstrap() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	void roundTripsSparseBlocksBlockEntitiesAndEntities() {
		RegistryAccess registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
		CompoundTag blockEntity = new CompoundTag();
		blockEntity.putString("id", "minecraft:chest");
		blockEntity.putInt("x", 1);
		blockEntity.putInt("y", 0);
		blockEntity.putInt("z", 1);
		CompoundTag entity = new CompoundTag();
		entity.putString("id", "minecraft:item");
		ListTag position = new ListTag();
		position.add(DoubleTag.valueOf(1.5));
		position.add(DoubleTag.valueOf(1.0));
		position.add(DoubleTag.valueOf(1.5));
		entity.put("Pos", position);
		MapSnapshot snapshot = new MapSnapshot(1, new Vec3i(3, 2, 3),
				List.of(Blocks.STONE.defaultBlockState()),
				List.of(new MapSnapshot.SnapshotBlock(new BlockPos(1, 0, 1), 0)),
				List.of(blockEntity), List.of(entity));

		CompoundTag tag = MapSnapshotCodec.write(snapshot, registries);
		MapSnapshot restored = MapSnapshotCodec.read(tag, registries);

		assertEquals(snapshot.size(), restored.size());
		assertEquals(snapshot.blocks(), restored.blocks());
		assertEquals(snapshot.blockEntities(), restored.blockEntities());
		assertEquals(snapshot.entities(), restored.entities());
	}

	@Test
	void rejectsDuplicateBlockPositions() {
		assertThrows(IllegalArgumentException.class, () -> new MapSnapshot(1, new Vec3i(2, 1, 1),
				List.of(Blocks.STONE.defaultBlockState()),
				List.of(new MapSnapshot.SnapshotBlock(BlockPos.ZERO, 0),
						new MapSnapshot.SnapshotBlock(BlockPos.ZERO, 0)), List.of(), List.of()));
	}

	@Test
	void rejectsPlayerEntity() {
		RegistryAccess registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
		CompoundTag tag = MapSnapshotCodec.write(new MapSnapshot(1, new Vec3i(1, 1, 1),
				List.of(Blocks.STONE.defaultBlockState()), List.of(), List.of(), List.of()), registries);
		CompoundTag player = new CompoundTag();
		player.putString("id", "minecraft:player");
		ListTag players = new ListTag();
		players.add(player);
		tag.put("entities", players);

		assertThrows(IllegalArgumentException.class, () -> MapSnapshotCodec.read(tag, registries));
	}

}
