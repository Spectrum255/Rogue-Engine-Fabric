package spectrum255.rogueengine.map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;

class SavedWorldReaderTest {
	@TempDir
	Path world;

	@BeforeAll
	static void bootstrap() {
		SharedConstants.tryDetectVersion();
	}

	@Test
	void rejectsWrongDataVersion() throws IOException {
		writeLevelData(SharedConstants.getCurrentVersion().dataVersion().version() - 1);
		Files.createDirectories(world.resolve("region"));
		Files.writeString(world.resolve("region/r.0.0.mca"), "region");

		assertThrows(IOException.class, () -> SavedWorldReader.validateWorld(world));
	}

	@Test
	void rejectsLockedWorld() throws IOException {
		writeLevelData(SharedConstants.getCurrentVersion().dataVersion().version());
		Files.createDirectories(world.resolve("region"));
		Files.writeString(world.resolve("region/r.0.0.mca"), "region");
		Files.writeString(world.resolve("session.lock"), "locked");

		assertThrows(IOException.class, () -> SavedWorldReader.validateWorld(world));
	}

	@Test
	void acceptsUnlockedCurrentWorld() throws IOException {
		writeLevelData(SharedConstants.getCurrentVersion().dataVersion().version());
		Files.createDirectories(world.resolve("region"));
		Files.writeString(world.resolve("region/r.0.0.mca"), "region");

		assertDoesNotThrow(() -> SavedWorldReader.validateWorld(world));
	}

	private void writeLevelData(int version) throws IOException {
		CompoundTag root = new CompoundTag();
		CompoundTag data = new CompoundTag();
		data.putInt("DataVersion", version);
		root.put("Data", data);
		NbtIo.writeCompressed(root, world.resolve("level.dat"));
	}
}
