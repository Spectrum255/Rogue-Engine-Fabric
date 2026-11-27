package spectrum255.rogueengine.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.minecraft.resources.Identifier;

class SceneCacheServiceTest {
	@TempDir
	Path tempDirectory;

	@Test
	void cachesSourceAndComputesHash() throws IOException {
		Path source = tempDirectory.resolve("source.nbt");
		Files.writeString(source, "structure-data");
		SceneCacheService service = new SceneCacheService(tempDirectory);
		UUID instanceId = UUID.randomUUID();

		SceneCacheService.CachedScene cached = service.cache(instanceId, Identifier.withDefaultNamespace("scene"), source);

		assertEquals("scenes/" + instanceId + "/source.nbt", cached.relativePath());
		assertEquals(64, cached.hash().length());
		assertEquals("structure-data", Files.readString(service.resolve(cached.relativePath())));
		assertTrue(service.verify(cached.relativePath(), cached.hash()));
	}

	@Test
	void cachesRawBytes() throws IOException {
		SceneCacheService service = new SceneCacheService(tempDirectory);
		UUID instanceId = UUID.randomUUID();

		SceneCacheService.CachedScene cached = service.cache(instanceId, Identifier.withDefaultNamespace("scene"),
				"raw-data".getBytes(StandardCharsets.UTF_8));

		assertEquals("raw-data", Files.readString(service.resolve(cached.relativePath())));
		assertTrue(service.verify(cached.relativePath(), cached.hash()));
	}

	@Test
	void missingSourceIsRejected() {
		SceneCacheService service = new SceneCacheService(tempDirectory);

		assertThrows(IOException.class, () -> service.cache(UUID.randomUUID(), Identifier.withDefaultNamespace("scene"),
				tempDirectory.resolve("missing.nbt")));
	}

	@Test
	void hashMismatchIsRejected() throws IOException {
		Path source = tempDirectory.resolve("source.nbt");
		Files.writeString(source, "structure-data");
		SceneCacheService service = new SceneCacheService(tempDirectory);
		SceneCacheService.CachedScene cached = service.cache(UUID.randomUUID(), Identifier.withDefaultNamespace("scene"), source);
		Files.writeString(service.resolve(cached.relativePath()), "changed");

		assertFalse(service.verify(cached.relativePath(), cached.hash()));
	}

	@Test
	void instancesDoNotOverwriteEachOther() throws IOException {
		Path sourceA = tempDirectory.resolve("a.nbt");
		Path sourceB = tempDirectory.resolve("b.nbt");
		Files.writeString(sourceA, "a");
		Files.writeString(sourceB, "b");
		SceneCacheService service = new SceneCacheService(tempDirectory);
		UUID first = UUID.randomUUID();
		UUID second = UUID.randomUUID();

		SceneCacheService.CachedScene cachedA = service.cache(first, Identifier.withDefaultNamespace("scene"), sourceA);
		SceneCacheService.CachedScene cachedB = service.cache(second, Identifier.withDefaultNamespace("scene"), sourceB);

		assertEquals("a", Files.readString(service.resolve(cachedA.relativePath())));
		assertEquals("b", Files.readString(service.resolve(cachedB.relativePath())));
	}

	@Test
	void resolvesLegacyNestedCacheAfterRootCorrection() throws IOException {
		SceneCacheService service = new SceneCacheService(tempDirectory);
		UUID instanceId = UUID.randomUUID();
		String relativePath = "scenes/" + instanceId + "/source.nbt";
		Path legacy = tempDirectory.resolve("scenes").resolve(relativePath);
		Files.createDirectories(legacy.getParent());
		Files.writeString(legacy, "legacy");

		assertEquals(legacy, service.resolve(relativePath));
	}

	@Test
	void rejectsPathOutsideManagedRoot() {
		SceneCacheService service = new SceneCacheService(tempDirectory);

		assertThrows(IOException.class, () -> service.resolve("../outside.nbt"));
	}
}
