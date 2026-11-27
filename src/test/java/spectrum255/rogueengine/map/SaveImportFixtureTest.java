package spectrum255.rogueengine.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

class SaveImportFixtureTest {
	private static final Path FIXTURE = Path.of("verification/fixtures/save-import");
	private static RegistryAccess registries;

	@BeforeAll
	static void bootstrap() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
	}

	@Test
	void generatedAnvilFixtureContainsExpectedChunkAndEntity() throws IOException {
		assertTrue(Files.isRegularFile(FIXTURE.resolve("level.dat")));
		assertTrue(Files.isRegularFile(FIXTURE.resolve("region/r.0.0.mca")));
		assertTrue(Files.isRegularFile(FIXTURE.resolve("entities/r.0.0.mca")));
		assertTrue(Files.isRegularFile(FIXTURE.resolveSibling("save-import.zip")));
		SavedWorldReader.validateWorld(FIXTURE);

		ResourceKey<Level> overworld = ResourceKey.create(Registries.DIMENSION,
				Identifier.withDefaultNamespace("overworld"));
		try (SavedWorldReader reader = new SavedWorldReader(FIXTURE, overworld)) {
			CompoundTag chunk = reader.readChunk(new ChunkPos(0, 0));
			assertNotNull(chunk);
			SavedChunkDecoder.Decoded decoded = SavedChunkDecoder.decode(chunk, new ChunkPos(0, 0),
					new BoundingBox(0, 128, 0, 4, 128, 4), registries, true);
			assertEquals(25, decoded.blocks().size());
			assertEquals(1, decoded.blockEntities().size());
			assertEquals(0, decoded.blockEntities().getFirst().getInt("y").orElseThrow());

			List<CompoundTag> entities = reader.readEntities(new ChunkPos(0, 0));
			assertEquals(1, entities.size());
			assertEquals("minecraft:item", entities.getFirst().getStringOr("id", ""));
		}
	}
}
