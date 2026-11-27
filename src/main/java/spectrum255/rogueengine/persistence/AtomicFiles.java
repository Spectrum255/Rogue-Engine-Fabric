package spectrum255.rogueengine.persistence;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

public final class AtomicFiles {
	private AtomicFiles() {
	}

	public static void writeAtomic(Path target, byte[] content) throws IOException {
		Path normalizedTarget = target.toAbsolutePath().normalize();
		Files.createDirectories(normalizedTarget.getParent());
		Path temporary = normalizedTarget.resolveSibling(normalizedTarget.getFileName() + ".tmp-" + UUID.randomUUID());
		try {
			try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
				ByteBuffer buffer = ByteBuffer.wrap(content);
				while (buffer.hasRemaining()) {
					channel.write(buffer);
				}
				channel.force(true);
			}
			Files.move(temporary, normalizedTarget, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException exception) {
			Files.deleteIfExists(temporary);
			throw exception;
		}
	}

	public static void verifyManagedPath(Path root, Path candidate) throws IOException {
		Path normalizedRoot = root.toAbsolutePath().normalize();
		Path normalizedCandidate = candidate.toAbsolutePath().normalize();
		if (!normalizedCandidate.startsWith(normalizedRoot)) {
			throw new IOException("Path escapes managed root: " + normalizedCandidate);
		}
		rejectReparsePoint(normalizedRoot);
		Path current = normalizedRoot;
		for (Path segment : normalizedRoot.relativize(normalizedCandidate)) {
			current = current.resolve(segment);
			rejectReparsePoint(current);
		}
	}

	public static void moveDirectorySameVolume(Path source, Path target) throws IOException {
		Path normalizedSource = source.toAbsolutePath().normalize();
		Path normalizedTarget = target.toAbsolutePath().normalize();
		if (!Files.isDirectory(normalizedSource, LinkOption.NOFOLLOW_LINKS)) {
			throw new IOException("Source directory does not exist: " + normalizedSource);
		}
		if (Files.exists(normalizedTarget, LinkOption.NOFOLLOW_LINKS)) {
			throw new IOException("Target already exists: " + normalizedTarget);
		}
		rejectReparsePoint(normalizedSource);
		Files.createDirectories(normalizedTarget.getParent());
		FileStore sourceStore = Files.getFileStore(normalizedSource);
		FileStore targetStore = Files.getFileStore(normalizedTarget.getParent());
		if (!sourceStore.equals(targetStore)) {
			throw new IOException("Source and target are on different volumes");
		}
		Files.move(normalizedSource, normalizedTarget);
	}

	private static void rejectReparsePoint(Path path) throws IOException {
		if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
			return;
		}
		if (Files.isSymbolicLink(path)) {
			throw new IOException("Symbolic links are not allowed: " + path);
		}
		Path realPath = path.toRealPath();
		if (!Files.isSameFile(path, realPath)) {
			throw new IOException("Reparse points are not allowed: " + path);
		}
	}
}
