package spectrum255.rogueengine.map;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import spectrum255.rogueengine.persistence.AtomicFiles;

public final class SaveImportService {
	private static final int MAX_SOURCE_BYTES = 512 * 1024 * 1024;
	private static final int MAX_SNAPSHOT_BYTES = 16 * 1024 * 1024;
	private static final int MAX_CHUNKS = 4096;
	private final Path worldRoot;
	private final RegistryAccess registries;

	public SaveImportService(Path worldRoot, RegistryAccess registries) {
		this.worldRoot = worldRoot.toAbsolutePath().normalize();
		this.registries = Objects.requireNonNull(registries, "registries");
	}

	public Imported importSnapshot(Identifier id, Path source, ResourceKey<Level> dimension,
			BlockPos from, BlockPos to, ImportOptions options) throws IOException {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(source, "source");
		Objects.requireNonNull(dimension, "dimension");
		Objects.requireNonNull(options, "options");
		BoundingBox region = SnapshotCaptureService.validateRegion(from, to);
		Path normalizedSource = source.toAbsolutePath().normalize();
		boolean zip = Files.isRegularFile(normalizedSource, LinkOption.NOFOLLOW_LINKS)
				&& normalizedSource.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".zip");
		Path tempRoot = null;
		try {
			Path sourceRoot;
			if (zip) {
				tempRoot = worldRoot.resolve("data/rogueengine/imports").resolve(UUID.randomUUID().toString());
				Set<String> allowed = allowedZipEntries(normalizedSource, dimension);
				SafeZipExtractor.extract(normalizedSource, tempRoot, allowed);
				sourceRoot = tempRoot;
			} else {
				sourceRoot = normalizedSource;
			}
			SavedWorldReader.validateWorld(sourceRoot);
			validateSourceFiles(sourceRoot, dimension);
			return importFromWorld(id, sourceRoot, dimension, region, options);
		} finally {
			if (tempRoot != null) {
				deleteTree(tempRoot);
			}
		}
	}

	private Imported importFromWorld(Identifier id, Path sourceRoot, ResourceKey<Level> dimension,
			BoundingBox region, ImportOptions options) throws IOException {
		Map<BlockState, Integer> palette = new LinkedHashMap<>();
		List<MapSnapshot.SnapshotBlock> blocks = new ArrayList<>();
		List<CompoundTag> blockEntities = new ArrayList<>();
		List<CompoundTag> rawEntities = new ArrayList<>();
		List<ChunkPos> chunks = region.intersectingChunks().toList();
		if (chunks.size() > MAX_CHUNKS) {
			throw new IllegalArgumentException("Snapshot region intersects too many chunks: " + chunks.size());
		}
		try (SavedWorldReader reader = new SavedWorldReader(sourceRoot, dimension)) {
			for (ChunkPos chunkPos : chunks) {
				CompoundTag chunk = reader.readChunk(chunkPos);
				if (chunk == null) {
					continue;
				}
				SavedChunkDecoder.Decoded decoded;
				try {
					decoded = SavedChunkDecoder.decode(chunk, chunkPos, region, registries,
							options.includeBlockEntities());
				} catch (IllegalArgumentException exception) {
					if ("Saved chunk is not full".equals(exception.getMessage())) {
						continue;
					}
					throw exception;
				}
				Map<Integer, BlockState> statesByIndex = new LinkedHashMap<>();
				decoded.palette().forEach((state, index) -> statesByIndex.put(index, state));
				for (MapSnapshot.SnapshotBlock block : decoded.blocks()) {
					BlockState state = statesByIndex.get(block.paletteIndex());
					int globalIndex = palette.computeIfAbsent(state, ignored -> palette.size());
					blocks.add(new MapSnapshot.SnapshotBlock(block.pos(), globalIndex));
				}
				blockEntities.addAll(decoded.blockEntities());
				if (options.includeEntities()) {
					rawEntities.addAll(readLegacyEntities(chunk));
					rawEntities.addAll(reader.readEntities(chunkPos));
				}
			}
		}
		if (palette.isEmpty()) {
			palette.put(Blocks.AIR.defaultBlockState(), 0);
		}
		List<CompoundTag> entities = options.includeEntities() ? normalizeEntities(rawEntities, region) : List.of();
		Vec3i size = new Vec3i(region.maxX() - region.minX() + 1,
				region.maxY() - region.minY() + 1, region.maxZ() - region.minZ() + 1);
		MapSnapshot snapshot = new MapSnapshot(1, size, new ArrayList<>(palette.keySet()), blocks,
				blockEntities, entities);
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		NbtIo.writeCompressed(MapSnapshotCodec.write(snapshot, registries), output);
		if (output.size() > MAX_SNAPSHOT_BYTES) {
			throw new IllegalArgumentException("Imported snapshot exceeds " + MAX_SNAPSHOT_BYTES + " bytes");
		}
		Path target = worldRoot.resolve("data/rogueengine/map-snapshots")
				.resolve(id.getNamespace()).resolve(id.getPath() + ".nbt");
		AtomicFiles.verifyManagedPath(worldRoot, target);
		AtomicFiles.writeAtomic(target, output.toByteArray());
		return new Imported(target, sha256(output.toByteArray()), blocks.size(), blockEntities.size(), entities.size());
	}

	private Set<String> allowedZipEntries(Path zip, ResourceKey<Level> dimension) throws IOException {
		Path relativeDimension = SavedWorldPaths.dimensionFolder(Path.of(""), dimension);
		String regionPrefix = relativeDimension.toString().replace('\\', '/') + "/";
		String entitiesPrefix = relativeDimension.resolveSibling("entities").toString().replace('\\', '/') + "/";
		Set<String> allowed = new HashSet<>();
		try (java.util.zip.ZipFile archive = new java.util.zip.ZipFile(zip.toFile())) {
			var entries = archive.entries();
			while (entries.hasMoreElements()) {
				String name = entries.nextElement().getName().replace('\\', '/');
				if (name.equals("level.dat")
						|| name.startsWith(regionPrefix) && name.endsWith(".mca")
						|| name.startsWith(entitiesPrefix) && name.endsWith(".mca")) {
					allowed.add(name);
				}
			}
		}
		return allowed;
	}

	private void validateSourceFiles(Path sourceRoot, ResourceKey<Level> dimension) throws IOException {
		Path region = SavedWorldPaths.dimensionFolder(sourceRoot, dimension);
		if (!Files.isDirectory(region, LinkOption.NOFOLLOW_LINKS)) {
			throw new IOException("Source dimension has no region directory");
		}
		long total = 0L;
		for (Path directory : List.of(region, region.resolveSibling("entities"))) {
			if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
				continue;
			}
			try (Stream<Path> files = Files.list(directory)) {
				for (Path file : files.filter(path -> path.getFileName().toString().endsWith(".mca")).toList()) {
					AtomicFiles.verifyManagedPath(sourceRoot, file);
					if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
						throw new IOException("Invalid region file: " + file);
					}
					total += Files.size(file);
					if (total > MAX_SOURCE_BYTES) {
						throw new IOException("Source region files exceed the allowed total size");
					}
				}
			}
		}
	}

	private static List<CompoundTag> readLegacyEntities(CompoundTag chunk) {
		List<CompoundTag> result = new ArrayList<>();
		for (Tag element : chunk.getListOrEmpty("entities")) {
			if (element instanceof CompoundTag entity) {
				result.add(entity.copy());
			}
		}
		return result;
	}

	private static List<CompoundTag> normalizeEntities(List<CompoundTag> source, BoundingBox region) {
		Set<UUID> passengers = new HashSet<>();
		for (CompoundTag entity : source) {
			collectPassengerUuids(entity, passengers);
		}
		List<CompoundTag> roots = new ArrayList<>();
		Set<UUID> seenRoots = new HashSet<>();
		for (CompoundTag entity : source) {
			UUID id = uuid(entity);
			if (id != null && (passengers.contains(id) || !seenRoots.add(id))) {
				continue;
			}
			if (!insideRegion(entity, region)) {
				continue;
			}
			CompoundTag copy = entity.copy();
			localizeEntity(copy, region);
			stripUuidAndRejectPlayer(copy, 0);
			roots.add(copy);
		}
		if (roots.size() > 100_000) {
			throw new IllegalArgumentException("Snapshot entity count is too large");
		}
		return roots;
	}

	private static void collectPassengerUuids(CompoundTag entity, Set<UUID> passengers) {
		for (Tag passenger : entity.getListOrEmpty("Passengers")) {
			if (passenger instanceof CompoundTag tag) {
				UUID id = uuid(tag);
				if (id != null) {
					passengers.add(id);
				}
				collectPassengerUuids(tag, passengers);
			}
		}
	}

	private static boolean insideRegion(CompoundTag entity, BoundingBox region) {
		if (!(entity.get("Pos") instanceof ListTag position) || position.size() != 3) {
			return false;
		}
		double x = ((NumericTag) position.get(0)).doubleValue();
		double y = ((NumericTag) position.get(1)).doubleValue();
		double z = ((NumericTag) position.get(2)).doubleValue();
		return x >= region.minX() && x <= region.maxX() + 1.0D
				&& y >= region.minY() && y <= region.maxY() + 1.0D
				&& z >= region.minZ() && z <= region.maxZ() + 1.0D;
	}

	private static void localizeEntity(CompoundTag entity, BoundingBox region) {
		if (entity.get("Pos") instanceof ListTag position && position.size() == 3) {
			ListTag local = new ListTag();
			local.add(DoubleTag.valueOf(((NumericTag) position.get(0)).doubleValue() - region.minX()));
			local.add(DoubleTag.valueOf(((NumericTag) position.get(1)).doubleValue() - region.minY()));
			local.add(DoubleTag.valueOf(((NumericTag) position.get(2)).doubleValue() - region.minZ()));
			entity.put("Pos", local);
		}
		for (Tag passenger : entity.getListOrEmpty("Passengers")) {
			if (passenger instanceof CompoundTag tag) {
				localizeEntity(tag, region);
			}
		}
	}

	private static void stripUuidAndRejectPlayer(CompoundTag entity, int depth) {
		if (depth > 64) {
			throw new IllegalArgumentException("Snapshot passenger nesting is too deep");
		}
		if (entity.getStringOr("id", "").equals("minecraft:player")) {
			throw new IllegalArgumentException("Player entities cannot be imported");
		}
		entity.remove("UUID");
		for (Tag passenger : entity.getListOrEmpty("Passengers")) {
			if (passenger instanceof CompoundTag tag) {
				stripUuidAndRejectPlayer(tag, depth + 1);
			}
		}
	}

	private static UUID uuid(CompoundTag entity) {
		return entity.getString("UUID").flatMap(value -> {
			try {
				return Optional.of(UUID.fromString(value));
			} catch (IllegalArgumentException exception) {
				return Optional.empty();
			}
		}).orElse(null);
	}

	private static String sha256(byte[] bytes) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is unavailable", exception);
		}
	}

	private static void deleteTree(Path root) throws IOException {
		if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
			return;
		}
		try (Stream<Path> paths = Files.walk(root)) {
			for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
				Files.delete(path);
			}
		}
	}

	public record ImportOptions(boolean includeEntities, boolean includeBlockEntities) {
	}

	public record Imported(Path file, String hash, int blocks, int blockEntities, int entities) {
	}
}
