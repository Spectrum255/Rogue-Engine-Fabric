package spectrum255.rogueengine.map;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.Identifier;

import spectrum255.rogueengine.persistence.AtomicFiles;

public final class MapSnapshotCatalog {
	private static final int MAX_FILE_BYTES = 16 * 1024 * 1024;
	private static final int MAX_NBT_BYTES = 64 * 1024 * 1024;
	private final Path worldRoot;
	private final HolderLookup.Provider registries;
	private final Function<Identifier, Optional<byte[]>> dataPackReader;

	public MapSnapshotCatalog(Path worldRoot, HolderLookup.Provider registries,
			Function<Identifier, Optional<byte[]>> dataPackReader) {
		this.worldRoot = worldRoot.toAbsolutePath().normalize();
		this.registries = Objects.requireNonNull(registries, "registries");
		this.dataPackReader = Objects.requireNonNull(dataPackReader, "dataPackReader");
	}

	public Optional<Loaded> load(Identifier id) throws IOException {
		Path worldFile = worldRoot.resolve("data/rogue-engine-fabric/map-snapshots")
				.resolve(id.getNamespace()).resolve(id.getPath() + ".nbt");
		AtomicFiles.verifyManagedPath(worldRoot, worldFile);
		byte[] bytes = Files.exists(worldFile, LinkOption.NOFOLLOW_LINKS)
				? readWorldFile(worldFile) : readDataPack(id);
		if (bytes == null) {
			return Optional.empty();
		}
		CompoundTag tag = NbtIo.readCompressed(new ByteArrayInputStream(bytes), NbtAccounter.create(MAX_NBT_BYTES));
		return Optional.of(new Loaded(MapSnapshotCodec.read(tag, registries), bytes, sha256(bytes)));
	}

	private byte[] readWorldFile(Path path) throws IOException {
		if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.size(path) > MAX_FILE_BYTES) {
			throw new IOException("Invalid map snapshot file: " + path);
		}
		return Files.readAllBytes(path);
	}

	private byte[] readDataPack(Identifier id) {
		return dataPackReader.apply(id).filter(bytes -> bytes.length > 0 && bytes.length <= MAX_FILE_BYTES)
				.orElse(null);
	}

	private static String sha256(byte[] bytes) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is unavailable", exception);
		}
	}

	public record Loaded(MapSnapshot snapshot, byte[] content, String hash) {
	}
}
