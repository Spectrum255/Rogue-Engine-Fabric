package spectrum255.rogueengine.map;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;

class MapSnapshotCatalogTest {
	@TempDir
	Path world;

	@BeforeAll
	static void bootstrap() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	void loadsWorldSnapshotAndComputesHash() throws IOException {
		MapSnapshot snapshot = snapshot();
		Path file = world.resolve("data/rogue-engine-fabric/map-snapshots/example/room.nbt");
		Files.createDirectories(file.getParent());
		NbtIo.writeCompressed(MapSnapshotCodec.write(snapshot, registries()), file);
		MapSnapshotCatalog catalog = new MapSnapshotCatalog(world, registries(), id -> {
			throw new AssertionError("Data pack should not be read when world file exists");
		});

		MapSnapshotCatalog.Loaded loaded = catalog.load(Identifier.parse("example:room")).orElseThrow();

		assertEquals(snapshot.size(), loaded.snapshot().size());
		assertEquals(64, loaded.hash().length());
	}

	@Test
	void fallsBackToDataPackReader() throws IOException {
		MapSnapshot snapshot = snapshot();
		java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
		NbtIo.writeCompressed(MapSnapshotCodec.write(snapshot, registries()), output);
		byte[] content = output.toByteArray();
		MapSnapshotCatalog catalog = new MapSnapshotCatalog(world, registries(), id -> Optional.of(content));

		assertEquals(snapshot.size(), catalog.load(Identifier.parse("example:room")).orElseThrow().snapshot().size());
	}

	private MapSnapshot snapshot() {
		return new MapSnapshot(1, new Vec3i(1, 1, 1), List.of(Blocks.STONE.defaultBlockState()),
				List.of(), List.of(), List.of());
	}

	private RegistryAccess registries() {
		return RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
	}
}
