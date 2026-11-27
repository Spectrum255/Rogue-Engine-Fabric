package spectrum255.rogueengine.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AtomicFilesTest {
	@TempDir
	Path tempDirectory;

	@Test
	void atomicWriteCreatesAndReplacesFile() throws IOException {
		Path target = tempDirectory.resolve("data/catalog.json");

		AtomicFiles.writeAtomic(target, "first".getBytes(StandardCharsets.UTF_8));
		AtomicFiles.writeAtomic(target, "second".getBytes(StandardCharsets.UTF_8));

		assertEquals("second", Files.readString(target));
		assertFalse(Files.exists(tempDirectory.resolve("data/catalog.json.tmp")));
	}

	@Test
	void managedPathRejectsTraversal() throws IOException {
		Path root = tempDirectory.resolve("world");
		Files.createDirectories(root);

		assertThrows(IOException.class, () -> AtomicFiles.verifyManagedPath(root, root.resolve("../outside")));
	}

	@Test
	void directoryMoveStaysOnOneVolume() throws IOException {
		Path source = tempDirectory.resolve("source/dimension");
		Path target = tempDirectory.resolve("quarantine/operation/dimension");
		Files.createDirectories(source);
		Files.writeString(source.resolve("level.dat"), "probe");

		AtomicFiles.moveDirectorySameVolume(source, target);

		assertFalse(Files.exists(source));
		assertEquals("probe", Files.readString(target.resolve("level.dat")));
	}

	@Test
	void directoryMoveRejectsExistingTarget() throws IOException {
		Path source = tempDirectory.resolve("source");
		Path target = tempDirectory.resolve("target");
		Files.createDirectories(source);
		Files.createDirectories(target);

		assertThrows(IOException.class, () -> AtomicFiles.moveDirectorySameVolume(source, target));
		assertTrue(Files.exists(source.resolve(".")));
	}
}
