package spectrum255.rogueengine.persistence;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;

public final class DirectoryHash {
	private DirectoryHash() {
	}

	public static String of(Path directory) throws IOException {
		Path root = directory.toAbsolutePath().normalize();
		AtomicFiles.verifyManagedPath(root, root);
		if (!Files.isDirectory(root)) {
			throw new IOException("Directory does not exist: " + root);
		}
		List<Path> files;
		try (Stream<Path> stream = Files.walk(root)) {
			files = stream.filter(Files::isRegularFile)
					.sorted(Comparator.comparing(path -> root.relativize(path).toString()))
					.toList();
		}
		MessageDigest digest;
		try {
			digest = MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is unavailable", exception);
		}
		for (Path file : files) {
			AtomicFiles.verifyManagedPath(root, file);
			digest.update(root.relativize(file).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
			digest.update((byte) 0);
			try (InputStream input = Files.newInputStream(file)) {
				byte[] buffer = new byte[8192];
				int read;
				while ((read = input.read(buffer)) != -1) {
					digest.update(buffer, 0, read);
				}
			}
			digest.update((byte) 0);
		}
		return HexFormat.of().formatHex(digest.digest());
	}
}
