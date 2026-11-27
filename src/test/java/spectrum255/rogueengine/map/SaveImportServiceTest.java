package spectrum255.rogueengine.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.Strategy;
import net.minecraft.world.level.chunk.storage.RegionFileStorage;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import com.mojang.serialization.Codec;

class SaveImportServiceTest {
	@TempDir
	Path temp;

	@BeforeAll
	static void bootstrap() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	void importsFolderAndZipEquivalently() throws IOException {
		Path source = createFixture(temp.resolve("source"));
		RegistryAccess registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
		SaveImportService service = new SaveImportService(temp.resolve("world"), registries);
		SaveImportService.ImportOptions options = new SaveImportService.ImportOptions(true, true);
		ResourceKey<Level> overworld = ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
				Identifier.withDefaultNamespace("overworld"));
		BoundingBox region = new BoundingBox(0, 128, 0, 4, 128, 4);

		SaveImportService.Imported folder = service.importSnapshot(Identifier.parse("example:folder"),
				source, overworld, new BlockPos(0, 128, 0), new BlockPos(4, 128, 4), options);
		Path zip = zipFixture(temp.resolve("source.zip"), source);
		SaveImportService.Imported zipped = service.importSnapshot(Identifier.parse("example:zip"),
				zip, overworld, new BlockPos(0, 128, 0), new BlockPos(4, 128, 4), options);

		assertEquals(folder.hash(), zipped.hash());
		assertEquals(25, folder.blocks());
		assertEquals(1, folder.blockEntities());
		assertEquals(1, folder.entities());
	}

	@Test
	void failureDoesNotOverwriteExistingSnapshot() throws IOException {
		Path source = createFixture(temp.resolve("source"));
		RegistryAccess registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
		SaveImportService service = new SaveImportService(temp.resolve("world"), registries);
		Path target = temp.resolve("world/data/rogue-engine-fabric/map-snapshots/example/existing.nbt");
		Files.createDirectories(target.getParent());
		Files.writeString(target, "existing");
		Files.writeString(source.resolve("session.lock"), "locked");

		assertThrows(IOException.class, () -> service.importSnapshot(Identifier.parse("example:existing"),
				source, ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
						Identifier.withDefaultNamespace("overworld")),
				new BlockPos(0, 128, 0), new BlockPos(4, 128, 4),
				new SaveImportService.ImportOptions(true, true)));
		assertEquals("existing", Files.readString(target));
	}

	private Path createFixture(Path source) throws IOException {
		Files.createDirectories(source);
		CompoundTag level = new CompoundTag();
		CompoundTag data = new CompoundTag();
		data.putInt("DataVersion", SharedConstants.getCurrentVersion().dataVersion().version());
		level.put("Data", data);
		net.minecraft.nbt.NbtIo.writeCompressed(level, source.resolve("level.dat"));

		RegistryAccess registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
		Strategy<BlockState> strategy = Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY);
		Codec<PalettedContainer<BlockState>> codec = PalettedContainer.codecRW(
				BlockState.FULL_CODEC, strategy, Blocks.AIR.defaultBlockState());
		PalettedContainer<BlockState> states = new PalettedContainer<>(Blocks.AIR.defaultBlockState(), strategy);
		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 16; x++) {
				for (int z = 0; z < 16; z++) {
					states.set(x, y, z, Blocks.STONE.defaultBlockState());
				}
			}
		}
		CompoundTag section = new CompoundTag();
		section.putByte("Y", (byte) 8);
		section.store("block_states", codec, states);
		ListTag sections = new ListTag();
		sections.add(section);
		CompoundTag chunk = new CompoundTag();
		chunk.putInt("xPos", 0);
		chunk.putInt("zPos", 0);
		chunk.putString("Status", "minecraft:full");
		chunk.put("sections", sections);
		CompoundTag chest = new CompoundTag();
		chest.putString("id", "minecraft:chest");
		chest.putInt("x", 2);
		chest.putInt("y", 128);
		chest.putInt("z", 2);
		ListTag blockEntities = new ListTag();
		blockEntities.add(chest);
		chunk.put("block_entities", blockEntities);
		CompoundTag item = new CompoundTag();
		item.putString("id", "minecraft:item");
		item.putString("UUID", "12345678-1234-1234-1234-123456789abc");
		ListTag position = new ListTag();
		position.add(DoubleTag.valueOf(2.5));
		position.add(DoubleTag.valueOf(129.0));
		position.add(DoubleTag.valueOf(2.5));
		item.put("Pos", position);
		ListTag entities = new ListTag();
		entities.add(item);
		ListTag legacyEntities = new ListTag();
		legacyEntities.add(item.copy());
		chunk.put("entities", legacyEntities);
		ResourceKey<Level> overworld = ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
				Identifier.withDefaultNamespace("overworld"));
		RegionStorageInfo info = new RegionStorageInfo("fixture", overworld, "region");
		try (RegionFileStorage storage = new RegionFileStorage(info, source.resolve("region"), false)) {
			storage.write(new ChunkPos(0, 0), chunk);
		}

		CompoundTag entityChunk = new CompoundTag();
		entityChunk.put("Entities", entities);
		entityChunk.store("Position", ChunkPos.CODEC, new ChunkPos(0, 0));
		try (RegionFileStorage storage = new RegionFileStorage(info.withTypeSuffix("entities"),
				source.resolve("entities"), false)) {
			storage.write(new ChunkPos(0, 0), entityChunk);
		}
		return source;
	}

	private Path zipFixture(Path zip, Path source) throws IOException {
		try (ZipArchiveOutputStream out = new ZipArchiveOutputStream(zip.toFile())) {
			for (Path file : List.of(source.resolve("level.dat"), source.resolve("region/r.0.0.mca"),
					source.resolve("entities/r.0.0.mca"))) {
				out.putArchiveEntry(new ZipArchiveEntry(source.relativize(file).toString().replace('\\', '/')));
				out.write(Files.readAllBytes(file));
				out.closeArchiveEntry();
			}
		}
		return zip;
	}
}
