package spectrum255.rogueengine.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;

import spectrum255.rogueengine.instance.BoxInstance;

class PlayerInventoryStoreTest {
	@TempDir
	Path tempDirectory;

	@Test
	void persistsPhaseAndSnapshotsAtomically() throws IOException {
		PlayerInventoryStore store = new PlayerInventoryStore(tempDirectory);
		UUID player = UUID.randomUUID();
		UUID instance = UUID.randomUUID();
		PlayerInventoryRecord record = new PlayerInventoryRecord(1, player, instance,
				PlayerInventoryRecord.Phase.ENTERING, true, PlayerInventorySnapshot.empty(), PlayerInventorySnapshot.empty(),
				BoxInstance.dimensionKey(instance), null);

		store.write(record);
		assertEquals(record, store.read(player, instance).orElseThrow());
		assertTrue(Files.exists(store.path(player, instance).resolveSibling(instance + ".dat.bak")) == false);

		store.write(record);
		assertTrue(Files.exists(store.path(player, instance).resolveSibling(instance + ".dat.bak")));
	}

	@Test
	void corruptSnapshotFailsClosed() throws IOException {
		PlayerInventoryStore store = new PlayerInventoryStore(tempDirectory);
		UUID player = UUID.randomUUID();
		UUID instance = UUID.randomUUID();
		Files.createDirectories(store.path(player, instance).getParent());
		Files.write(store.path(player, instance), new byte[] {1, 2, 3});

		assertThrows(IOException.class, () -> store.read(player, instance));
	}

	@Test
	void unknownSchemaFailsClosed() throws IOException {
		PlayerInventoryStore store = new PlayerInventoryStore(tempDirectory);
		UUID player = UUID.randomUUID();
		UUID instance = UUID.randomUUID();
		CompoundTag tag = new CompoundTag();
		tag.putInt("schema_version", 2);
		Files.createDirectories(store.path(player, instance).getParent());
		NbtIo.write(tag, store.path(player, instance));

		assertThrows(IOException.class, () -> store.read(player, instance));
	}
}
