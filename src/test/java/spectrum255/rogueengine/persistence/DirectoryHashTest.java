package spectrum255.rogueengine.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DirectoryHashTest {
	@TempDir
	Path tempDirectory;

	@Test
	void hashIsDeterministicAndContentSensitive() throws IOException {
		Path first = tempDirectory.resolve("first");
		Path second = tempDirectory.resolve("second");
		Files.createDirectories(first);
		Files.createDirectories(second);
		Files.writeString(first.resolve("level.dat"), "same");
		Files.writeString(second.resolve("level.dat"), "same");

		assertEquals(DirectoryHash.of(first), DirectoryHash.of(second));

		Files.writeString(second.resolve("level.dat"), "changed");
		assertNotEquals(DirectoryHash.of(first), DirectoryHash.of(second));
	}
}
