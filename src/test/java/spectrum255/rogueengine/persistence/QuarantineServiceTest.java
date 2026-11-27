package spectrum255.rogueengine.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class QuarantineServiceTest {
	@TempDir
	Path tempDirectory;

	@Test
	void recoversMovedDimensionWithoutManifest() throws IOException {
		Path world = tempDirectory.resolve("world");
		Path source = world.resolve("dimensions/rogue-engine-fabric/instance/test");
		Files.createDirectories(source);
		Files.writeString(source.resolve("level.dat"), "data");
		QuarantineService service = new QuarantineService(world, 7, () -> 1000L);
		UUID operationId = UUID.randomUUID();
		UUID instanceId = UUID.randomUUID();
		Path target = world.resolve("rogue-engine-fabric/quarantine").resolve(operationId.toString()).resolve("dimension");
		Files.createDirectories(target.getParent());
		Files.move(source, target);

		Path operation = service.recover(operationId, instanceId, source);

		assertTrue(Files.exists(operation.resolve("manifest.json")));
		assertTrue(service.isQuarantined(operationId, instanceId));
	}

	@Test
	void quarantinesPlayerInventoryFilesWithDimension() throws IOException {
		Path world = tempDirectory.resolve("world");
		Path inventoryRoot = world.resolve("data/rogue-engine-fabric/inventories");
		UUID player = UUID.randomUUID();
		UUID instance = UUID.randomUUID();
		UUID operation = UUID.randomUUID();
		Path playerDirectory = inventoryRoot.resolve(player.toString());
		Files.createDirectories(playerDirectory);
		Files.writeString(playerDirectory.resolve(instance + ".dat"), "inventory");
		Files.writeString(playerDirectory.resolve(instance + ".dat.bak"), "backup");
		QuarantineService service = new QuarantineService(world, 7, () -> 1000L);

		Path target = service.quarantineInventories(operation, instance, inventoryRoot);

		assertEquals("inventory", Files.readString(target.resolve(player.toString()).resolve(instance + ".dat")));
		assertEquals("backup", Files.readString(target.resolve(player.toString()).resolve(instance + ".dat.bak")));
		assertFalse(Files.exists(playerDirectory.resolve(instance + ".dat")));
		assertTrue(Files.exists(target.resolve("inventory-manifest.json")));
	}

	@Test
	void quarantinesSceneCacheWithOperation() throws IOException {
		Path world = tempDirectory.resolve("world");
		Path sceneRoot = world.resolve("data/rogue-engine-fabric/scenes");
		UUID instance = UUID.randomUUID();
		UUID operation = UUID.randomUUID();
		Path source = sceneRoot.resolve(instance.toString());
		Files.createDirectories(source);
		Files.writeString(source.resolve("source.nbt"), "scene");
		QuarantineService service = new QuarantineService(world, 7, () -> 1000L);

		Path target = service.quarantineScenes(operation, instance, sceneRoot);

		assertEquals("scene", Files.readString(target.resolve(instance.toString()).resolve("source.nbt")));
		assertFalse(Files.exists(source));
		assertTrue(Files.exists(target.resolve("scene-manifest.json")));
	}

	@Test
	void quarantinesLegacyNestedSceneCache() throws IOException {
		Path world = tempDirectory.resolve("world");
		Path sceneRoot = world.resolve("data/rogue-engine-fabric/scenes");
		UUID instance = UUID.randomUUID();
		UUID operation = UUID.randomUUID();
		Path source = sceneRoot.resolve("scenes").resolve(instance.toString());
		Files.createDirectories(source);
		Files.writeString(source.resolve("source.nbt"), "legacy-scene");
		QuarantineService service = new QuarantineService(world, 7, () -> 1000L);

		Path target = service.quarantineScenes(operation, instance, sceneRoot);

		assertEquals("legacy-scene", Files.readString(target.resolve(instance.toString()).resolve("source.nbt")));
		assertFalse(Files.exists(source));
	}

	@Test
	void missingSceneCacheDoesNotCreateEmptyQuarantine() throws IOException {
		Path world = tempDirectory.resolve("world");
		Path sceneRoot = world.resolve("data/rogue-engine-fabric/scenes");
		QuarantineService service = new QuarantineService(world, 7, () -> 1000L);

		Path target = service.quarantineScenes(UUID.randomUUID(), UUID.randomUUID(), sceneRoot);

		assertFalse(Files.exists(target));
	}

	@Test
	void quarantineMovesDimensionAndWritesManifest() throws IOException {
		Path world = tempDirectory.resolve("world");
		Path source = world.resolve("dimensions/rogue-engine-fabric/instance/test");
		Files.createDirectories(source);
		Files.writeString(source.resolve("level.dat"), "data");
		QuarantineService service = new QuarantineService(world, 7, () -> 1000L);
		UUID id = UUID.randomUUID();

		Path operation = service.quarantine(source, id, "hash");

		assertFalse(Files.exists(source));
		assertEquals("data", Files.readString(operation.resolve("dimension/level.dat")));
		assertTrue(Files.exists(operation.resolve("manifest.json")));
	}
}
