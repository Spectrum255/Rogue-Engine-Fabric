package spectrum255.rogueengine.persistence;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.stream.Stream;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public final class QuarantineService {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private final Path worldRoot;
	private final Path quarantineRoot;
	private final int retentionDays;
	private final LongSupplier clock;

	public QuarantineService(Path worldRoot, int retentionDays, LongSupplier clock) {
		this.worldRoot = worldRoot.toAbsolutePath().normalize();
		this.quarantineRoot = this.worldRoot.resolve("rogueengine").resolve("quarantine");
		this.retentionDays = retentionDays;
		this.clock = clock;
	}

	public Path quarantine(Path source, UUID instanceId, String contentHash) throws IOException {
		return quarantine(UUID.randomUUID(), source, instanceId, contentHash);
	}

	public Path quarantine(UUID operationId, Path source, UUID instanceId, String contentHash) throws IOException {
		Path normalizedSource = source.toAbsolutePath().normalize();
		AtomicFiles.verifyManagedPath(worldRoot, normalizedSource);
		Path operation = quarantineRoot.resolve(operationId.toString());
		Path target = operation.resolve("dimension");
		AtomicFiles.moveDirectorySameVolume(normalizedSource, target);
		writeManifest(operation, normalizedSource, instanceId, contentHash);
		return operation;
	}

	public Path quarantineInventories(UUID operationId, UUID instanceId, Path sourceRoot) throws IOException {
		Path normalizedSource = sourceRoot.toAbsolutePath().normalize();
		AtomicFiles.verifyManagedPath(worldRoot, normalizedSource);
		Path targetRoot = quarantineRoot.resolve(operationId.toString()).resolve("inventories");
		AtomicFiles.verifyManagedPath(worldRoot, targetRoot);
		Files.createDirectories(targetRoot);
		if (Files.isDirectory(normalizedSource)) {
			try (Stream<Path> players = Files.list(normalizedSource)) {
				for (Path player : players.filter(Files::isDirectory).toList()) {
					for (String suffix : List.of(".dat", ".dat.bak")) {
						Path source = player.resolve(instanceId + suffix);
						Path target = targetRoot.resolve(player.getFileName()).resolve(instanceId + suffix);
						if (!Files.exists(source)) {
							continue;
						}
						if (Files.exists(target)) {
							throw new IOException("Both source and quarantine inventory exist: " + source);
						}
						Files.createDirectories(target.getParent());
						if (!Files.getFileStore(source).equals(Files.getFileStore(target.getParent()))) {
							throw new IOException("Source and quarantine inventory are on different volumes");
						}
						Files.move(source, target);
					}
				}
			}
		}
		InventoryManifest manifest = new InventoryManifest(operationId.toString(), instanceId.toString(),
				worldRoot.relativize(normalizedSource).toString(), clock.getAsLong());
		AtomicFiles.writeAtomic(targetRoot.resolve("inventory-manifest.json"),
				GSON.toJson(manifest).getBytes(StandardCharsets.UTF_8));
		return targetRoot;
	}

	public Path quarantineScenes(UUID operationId, UUID instanceId, Path sourceRoot) throws IOException {
		Path normalizedRoot = sourceRoot.toAbsolutePath().normalize();
		AtomicFiles.verifyManagedPath(worldRoot, normalizedRoot);
		Path targetRoot = quarantineRoot.resolve(operationId.toString()).resolve("scenes");
		Path source = normalizedRoot.resolve(instanceId.toString());
		if (!Files.exists(source)) {
			Path legacy = normalizedRoot.resolve("scenes").resolve(instanceId.toString());
			if (Files.exists(legacy)) {
				source = legacy;
			}
		}
		Path target = targetRoot.resolve(instanceId.toString());
		if (!Files.exists(source)) {
			return targetRoot;
		}
		if (Files.exists(target)) {
			throw new IOException("Both source and quarantine scene cache exist: " + instanceId);
		}
		AtomicFiles.moveDirectorySameVolume(source, target);
		SceneManifest manifest = new SceneManifest(operationId.toString(), instanceId.toString(),
				worldRoot.relativize(normalizedRoot).toString(), clock.getAsLong());
		AtomicFiles.writeAtomic(targetRoot.resolve("scene-manifest.json"),
				GSON.toJson(manifest).getBytes(StandardCharsets.UTF_8));
		return targetRoot;
	}

	public Path recover(UUID operationId, UUID instanceId, Path source) throws IOException {
		Path normalizedSource = source.toAbsolutePath().normalize();
		AtomicFiles.verifyManagedPath(worldRoot, normalizedSource);
		Path operation = quarantineRoot.resolve(operationId.toString());
		Path target = operation.resolve("dimension");
		Path manifestPath = operation.resolve("manifest.json");
		boolean sourceExists = Files.exists(source);
		boolean targetExists = Files.exists(target);
		if (sourceExists && targetExists) {
			throw new IOException("Both source and quarantine dimension exist: " + operationId);
		}
		if (!sourceExists && !targetExists) {
			throw new IOException("Neither source nor quarantine dimension exists: " + operationId);
		}
		if (Files.exists(manifestPath)) {
			if (!targetExists || !isQuarantined(operationId, instanceId)) {
				throw new IOException("Quarantine manifest does not match operation: " + operationId);
			}
			return operation;
		}
		String contentHash;
		if (targetExists) {
			contentHash = DirectoryHash.of(target);
		} else {
			contentHash = DirectoryHash.of(normalizedSource);
			AtomicFiles.moveDirectorySameVolume(normalizedSource, target);
		}
		writeManifest(operation, normalizedSource, instanceId, contentHash);
		return operation;
	}

	private void writeManifest(Path operation, Path source, UUID instanceId, String contentHash) throws IOException {
		Manifest manifest = new Manifest(operation.getFileName().toString(), instanceId.toString(), contentHash,
				worldRoot.relativize(source).toString(), clock.getAsLong());
		AtomicFiles.writeAtomic(operation.resolve("manifest.json"), GSON.toJson(manifest).getBytes(StandardCharsets.UTF_8));
	}

	public boolean isQuarantined(UUID operationId, UUID instanceId) throws IOException {
		Path operation = quarantineRoot.resolve(operationId.toString());
		if (!Files.isRegularFile(operation.resolve("manifest.json")) || !Files.isDirectory(operation.resolve("dimension"))) {
			return false;
		}
		Manifest manifest = GSON.fromJson(Files.readString(operation.resolve("manifest.json"), StandardCharsets.UTF_8), Manifest.class);
		return manifest != null && instanceId.toString().equals(manifest.instanceId());
	}

	public void purgeExpired() throws IOException {
		if (!Files.isDirectory(quarantineRoot)) {
			return;
		}
		long cutoff = clock.getAsLong() - retentionDays * 86_400_000L;
		try (Stream<Path> operations = Files.list(quarantineRoot)) {
			for (Path operation : operations.filter(Files::isDirectory).toList()) {
				Path manifestPath = operation.resolve("manifest.json");
				if (!Files.isRegularFile(manifestPath)) {
					continue;
				}
				Manifest manifest = GSON.fromJson(Files.readString(manifestPath, StandardCharsets.UTF_8), Manifest.class);
				if (manifest == null || manifest.createdAtMillis() >= cutoff) {
					continue;
				}
				AtomicFiles.verifyManagedPath(worldRoot, operation);
				try (Stream<Path> paths = Files.walk(operation)) {
					for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
						Files.delete(path);
					}
				}
			}
		}
	}

	private record Manifest(String operationId, String instanceId, String contentHash, String originalPath, long createdAtMillis) {
	}

	private record InventoryManifest(String operationId, String instanceId, String originalPath, long createdAtMillis) {
	}

	private record SceneManifest(String operationId, String instanceId, String originalPath, long createdAtMillis) {
	}
}
