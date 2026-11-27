package spectrum255.rogueengine.map;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

import net.minecraft.resources.Identifier;

import spectrum255.rogueengine.persistence.AtomicFiles;

public final class SceneCacheService {
	private static final int MAX_SOURCE_BYTES = 16 * 1024 * 1024;

	private final Path root;

	public SceneCacheService(Path root) {
		this.root = root.toAbsolutePath().normalize();
	}

	public Path root() {
		return root;
	}

	public CachedScene cache(UUID instanceId, Identifier sceneId, Path source) throws IOException {
		Objects.requireNonNull(instanceId, "instanceId");
		Objects.requireNonNull(sceneId, "sceneId");
		Objects.requireNonNull(source, "source");
		if (!Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)) {
			throw new IOException("Scene source is not a regular file: " + source);
		}
		long size = Files.size(source);
		if (size <= 0 || size > MAX_SOURCE_BYTES) {
			throw new IOException("Scene source size is outside the allowed range: " + size);
		}
		return cache(instanceId, sceneId, Files.readAllBytes(source));
	}

	public CachedScene cache(UUID instanceId, Identifier sceneId, byte[] content) throws IOException {
		Objects.requireNonNull(instanceId, "instanceId");
		Objects.requireNonNull(sceneId, "sceneId");
		Objects.requireNonNull(content, "content");
		if (content.length == 0 || content.length > MAX_SOURCE_BYTES) {
			throw new IOException("Scene source size is outside the allowed range: " + content.length);
		}
		String hash = sha256(content);
		String relativePath = "scenes/" + instanceId + "/source.nbt";
		AtomicFiles.writeAtomic(resolve(relativePath), content);
		return new CachedScene(relativePath, hash);
	}

	public Path resolve(String relativePath) throws IOException {
		Objects.requireNonNull(relativePath, "relativePath");
		Path candidate = root.resolve(relativePath);
		AtomicFiles.verifyManagedPath(root, candidate);
		if (!Files.exists(candidate, LinkOption.NOFOLLOW_LINKS) && relativePath.startsWith("scenes/")) {
			Path legacy = root.resolve("scenes").resolve(relativePath);
			AtomicFiles.verifyManagedPath(root, legacy);
			if (Files.exists(legacy, LinkOption.NOFOLLOW_LINKS)) {
				return legacy;
			}
		}
		return candidate;
	}

	public boolean verify(String relativePath, String expectedHash) throws IOException {
		Path path = resolve(relativePath);
		if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
			return false;
		}
		return sha256(Files.readAllBytes(path)).equals(expectedHash);
	}

	private static String sha256(byte[] content) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(content);
			return HexFormat.of().formatHex(digest);
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is unavailable", exception);
		}
	}

	public record CachedScene(String relativePath, String hash) {
	}
}
