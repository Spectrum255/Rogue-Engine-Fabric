package spectrum255.rogueengine.map;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;

import spectrum255.rogueengine.persistence.AtomicFiles;

public final class PlayerSceneStateStore {
	private static final int MAX_FILE_BYTES = 64 * 1024;
	private final Path root;
	private final HolderLookup.Provider registries;

	public PlayerSceneStateStore(Path root, HolderLookup.Provider registries) {
		this.root = root.toAbsolutePath().normalize();
		this.registries = registries;
	}

	public Path pathFor(UUID instanceId, UUID playerId) throws IOException {
		Path path = root.resolve("scenes").resolve(instanceId.toString()).resolve("players")
				.resolve(playerId + ".dat");
		AtomicFiles.verifyManagedPath(root, path);
		return path;
	}

	public Optional<PlayerSceneState> read(UUID instanceId, UUID playerId) throws IOException {
		Path path = pathFor(instanceId, playerId);
		if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
			return Optional.empty();
		}
		if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.size(path) > MAX_FILE_BYTES) {
			throw new IOException("Invalid player scene state file: " + path);
		}
		try (InputStream input = Files.newInputStream(path)) {
			CompoundTag tag = NbtIo.readCompressed(input, NbtAccounter.create(MAX_FILE_BYTES));
			PlayerSceneState state = PlayerSceneStateCodec.read(tag, registries);
			if (!state.instanceId().equals(instanceId) || !state.playerId().equals(playerId)) {
				throw new IOException("Player scene state identity mismatch");
			}
			return Optional.of(state);
		}
	}

	public void write(PlayerSceneState state) throws IOException {
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		NbtIo.writeCompressed(PlayerSceneStateCodec.write(state, registries), output);
		if (output.size() > MAX_FILE_BYTES) {
			throw new IOException("Player scene state exceeds " + MAX_FILE_BYTES + " bytes");
		}
		AtomicFiles.writeAtomic(pathFor(state.instanceId(), state.playerId()), output.toByteArray());
	}

	public void delete(UUID instanceId, UUID playerId) throws IOException {
		Files.deleteIfExists(pathFor(instanceId, playerId));
	}
}
