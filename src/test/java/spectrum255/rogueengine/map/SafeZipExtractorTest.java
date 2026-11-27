package spectrum255.rogueengine.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SafeZipExtractorTest {
	@TempDir
	Path temp;

	@Test
	void rejectsPathTraversal() throws IOException {
		Path zip = temp.resolve("bad.zip");
		try (ZipArchiveOutputStream out = new ZipArchiveOutputStream(zip.toFile())) {
			entry(out, "../escape.txt", "x");
		}

		assertThrows(IOException.class, () -> SafeZipExtractor.extract(zip, temp.resolve("out"),
				new HashSet<>(Set.of("level.dat"))));
	}

	@Test
	void rejectsSymbolicLinkEntry() throws IOException {
		Path zip = temp.resolve("link.zip");
		try (ZipArchiveOutputStream out = new ZipArchiveOutputStream(zip.toFile())) {
			ZipArchiveEntry link = new ZipArchiveEntry("level.dat");
			link.setUnixMode(0120777);
			out.putArchiveEntry(link);
			out.write("x".getBytes(StandardCharsets.UTF_8));
			out.closeArchiveEntry();
		}

		assertThrows(IOException.class, () -> SafeZipExtractor.extract(zip, temp.resolve("out"),
				new HashSet<>(Set.of("level.dat"))));
	}

	@Test
	void extractsOnlyAllowedEntries() throws IOException {
		Path zip = temp.resolve("good.zip");
		try (ZipArchiveOutputStream out = new ZipArchiveOutputStream(zip.toFile())) {
			entry(out, "level.dat", "data");
			entry(out, "region/r.0.0.mca", "region");
			entry(out, "playerdata/ignored.dat", "ignored");
		}
		Path target = temp.resolve("out");

		SafeZipExtractor.extract(zip, target, new HashSet<>(Set.of("level.dat", "region/r.0.0.mca")));

		assertEquals("data", Files.readString(target.resolve("level.dat")));
		assertEquals("region", Files.readString(target.resolve("region/r.0.0.mca")));
		assertFalse(Files.exists(target.resolve("playerdata/ignored.dat")));
	}

	private void entry(ZipArchiveOutputStream out, String name, String content) throws IOException {
		out.putArchiveEntry(new ZipArchiveEntry(name));
		out.write(content.getBytes(StandardCharsets.UTF_8));
		out.closeArchiveEntry();
	}
}
