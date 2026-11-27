package spectrum255.rogueengine.map;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.storage.RegionFileStorage;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;

import spectrum255.rogueengine.persistence.AtomicFiles;

public final class SavedWorldReader implements AutoCloseable {
	private static final int MAX_LEVEL_DATA_BYTES = 16 * 1024 * 1024;
	private final RegionFileStorage regionStorage;
	private final RegionFileStorage entityStorage;

	public SavedWorldReader(Path worldRoot, ResourceKey<Level> dimension) throws IOException {
		Objects.requireNonNull(worldRoot, "worldRoot");
		Objects.requireNonNull(dimension, "dimension");
		Path regionDirectory = SavedWorldPaths.dimensionFolder(worldRoot, dimension);
		AtomicFiles.verifyManagedPath(worldRoot, regionDirectory);
		RegionStorageInfo info = new RegionStorageInfo("rogue-engine-import", dimension, "region");
		this.regionStorage = new RegionFileStorage(info, regionDirectory, true);
		Path entitiesDirectory = regionDirectory.resolveSibling("entities");
		this.entityStorage = Files.isDirectory(entitiesDirectory, LinkOption.NOFOLLOW_LINKS)
				? new RegionFileStorage(info.withTypeSuffix("entities"), entitiesDirectory, true) : null;
	}

	public static void validateWorld(Path worldRoot) throws IOException {
		Path root = worldRoot.toAbsolutePath().normalize();
		Path levelData = root.resolve("level.dat");
		AtomicFiles.verifyManagedPath(root, levelData);
		if (!Files.isRegularFile(levelData, LinkOption.NOFOLLOW_LINKS)
				|| Files.size(levelData) > MAX_LEVEL_DATA_BYTES) {
			throw new IOException("Invalid source level.dat");
		}
		if (Files.exists(root.resolve("session.lock"), LinkOption.NOFOLLOW_LINKS)) {
			throw new IOException("Source world is locked");
		}
		CompoundTag tag = NbtIo.readCompressed(levelData, NbtAccounter.create(MAX_LEVEL_DATA_BYTES));
		CompoundTag data = tag.get("Data") instanceof CompoundTag nested ? nested : tag;
		int version = data.getIntOr("DataVersion", -1);
		if (version != SharedConstants.getCurrentVersion().dataVersion().version()) {
			throw new IOException("Unsupported source DataVersion: " + version);
		}
	}

	public CompoundTag readChunk(ChunkPos pos) throws IOException {
		return regionStorage.read(pos);
	}

	public List<CompoundTag> readEntities(ChunkPos pos) throws IOException {
		if (entityStorage == null) {
			return List.of();
		}
		CompoundTag tag = entityStorage.read(pos);
		if (tag == null) {
			return List.of();
		}
		ChunkPos stored = ChunkPos.CODEC.parse(NbtOps.INSTANCE, tag.get("Position")).result().orElseThrow();
		if (!stored.equals(pos)) {
			throw new IOException("Entity chunk position mismatch");
		}
		List<CompoundTag> entities = new ArrayList<>();
		for (Tag element : tag.getListOrEmpty("Entities")) {
			if (element instanceof CompoundTag entity) {
				entities.add(entity.copy());
			}
		}
		return entities;
	}

	@Override
	public void close() throws IOException {
		IOException failure = null;
		try {
			regionStorage.close();
		} catch (IOException exception) {
			failure = exception;
		}
		if (entityStorage != null) {
			try {
				entityStorage.close();
			} catch (IOException exception) {
				if (failure == null) {
					failure = exception;
				} else {
					failure.addSuppressed(exception);
				}
			}
		}
		if (failure != null) {
			throw failure;
		}
	}
}
