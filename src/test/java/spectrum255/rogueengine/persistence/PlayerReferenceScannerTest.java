package spectrum255.rogueengine.persistence;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;

import spectrum255.rogueengine.instance.BoxInstance;

class PlayerReferenceScannerTest {
	@TempDir
	Path tempDirectory;

	@Test
	void detectsOfflinePlayerDimension() throws IOException {
		var key = BoxInstance.dimensionKey(UUID.randomUUID());
		writePlayer("a", key.identifier().toString());

		assertTrue(PlayerReferenceScanner.isReferenced(tempDirectory, key));
	}

	@Test
	void ignoresPlayersInOtherDimensions() throws IOException {
		var key = BoxInstance.dimensionKey(UUID.randomUUID());
		writePlayer("a", "minecraft:overworld");

		assertFalse(PlayerReferenceScanner.isReferenced(tempDirectory, key));
	}

	@Test
	void ignoresSavedDataForOnlinePlayers() throws IOException {
		var key = BoxInstance.dimensionKey(UUID.randomUUID());
		UUID onlinePlayer = UUID.randomUUID();
		writePlayer(onlinePlayer.toString(), key.identifier().toString());

		assertFalse(PlayerReferenceScanner.isReferenced(tempDirectory, key, Set.of(onlinePlayer)));
	}

	private void writePlayer(String name, String dimension) throws IOException {
		CompoundTag tag = new CompoundTag();
		tag.putString("Dimension", dimension);
		NbtIo.writeCompressed(tag, tempDirectory.resolve(name + ".dat"));
	}
}
