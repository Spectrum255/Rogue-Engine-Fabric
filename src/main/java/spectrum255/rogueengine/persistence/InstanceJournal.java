package spectrum255.rogueengine.persistence;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.mojang.serialization.JsonOps;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import spectrum255.rogueengine.map.SceneSelectionSnapshot;
import spectrum255.rogueengine.map.SceneSelectionSnapshotCodec;
import spectrum255.rogueengine.instance.BoxDefinition;
import spectrum255.rogueengine.instance.BoxInstance;
import spectrum255.rogueengine.instance.BoxState;

public final class InstanceJournal {
	private static final int SCHEMA_VERSION = 1;
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final Gson COMPACT_GSON = new GsonBuilder().disableHtmlEscaping().create();

	private final Path dataDirectory;
	private final Path catalogFile;
	private final Path backupFile;
	private final Path walFile;
	private final Map<UUID, BoxInstance> instances = new LinkedHashMap<>();
	private final Map<UUID, BoxDefinition> definitions = new LinkedHashMap<>();

	public InstanceJournal(Path dataDirectory) throws IOException {
		this.dataDirectory = dataDirectory.toAbsolutePath().normalize();
		this.catalogFile = this.dataDirectory.resolve("catalog-v1.json");
		this.backupFile = this.dataDirectory.resolve("catalog-v1.json.bak");
		this.walFile = this.dataDirectory.resolve("operations-v1.wal");
		LoadResult loaded = load();
		this.instances.putAll(loaded.instances());
		this.definitions.putAll(loaded.definitions());
	}

	public void begin(BoxInstance instance) throws IOException {
		begin(instance, null);
	}

	public void begin(BoxInstance instance, BoxDefinition definition) throws IOException {
		if (definition != null && !definition.instanceId().equals(instance.id())) {
			throw new IOException("Definition UUID does not match box UUID");
		}
		if (instance.state() != BoxState.CREATING || instance.stateVersion() != 1) {
			throw new IOException("A new box must start in CREATING at version 1");
		}
		if (instances.containsKey(instance.id())) {
			throw new IOException("Box already exists: " + instance.id());
		}
		Map<UUID, BoxInstance> candidate = new LinkedHashMap<>(instances);
		Map<UUID, BoxDefinition> candidateDefinitions = new LinkedHashMap<>(definitions);
		candidate.put(instance.id(), instance);
		if (definition != null) {
			candidateDefinitions.put(instance.id(), definition);
		}
		writeCatalog(candidate, candidateDefinitions);
		instances.put(instance.id(), instance);
		if (definition != null) {
			definitions.put(instance.id(), definition);
		}
	}

	public void appendIntent(WalRecord record) throws IOException {
		validateIntent(record);
		Files.createDirectories(dataDirectory);
		byte[] line = (COMPACT_GSON.toJson(record) + "\n").getBytes(StandardCharsets.UTF_8);
		try (FileChannel channel = FileChannel.open(walFile, StandardOpenOption.CREATE, StandardOpenOption.APPEND, StandardOpenOption.WRITE)) {
			ByteBuffer buffer = ByteBuffer.wrap(line);
			while (buffer.hasRemaining()) {
				channel.write(buffer);
			}
			channel.force(true);
		}
	}

	public void commit(BoxInstance updated) throws IOException {
		BoxInstance current = instances.get(updated.id());
		if (current == null) {
			throw new IOException("Cannot commit unknown box: " + updated.id());
		}
		if (updated.stateVersion() <= current.stateVersion() || !current.state().canTransitionTo(updated.state())) {
			throw new IOException("Illegal catalog transition for " + updated.id() + ": " + current.state() + " -> " + updated.state());
		}
		Map<UUID, BoxInstance> candidate = new LinkedHashMap<>(instances);
		candidate.put(updated.id(), updated);
		writeCatalog(candidate, definitions);
		instances.put(updated.id(), updated);
	}

	public void updateDefinition(BoxDefinition updated) throws IOException {
		if (!instances.containsKey(updated.instanceId())) {
			throw new IOException("Cannot update definition for unknown box: " + updated.instanceId());
		}
		Map<UUID, BoxDefinition> candidate = new LinkedHashMap<>(definitions);
		candidate.put(updated.instanceId(), updated);
		writeCatalog(instances, candidate);
		definitions.put(updated.instanceId(), updated);
	}

	public LoadResult load() throws IOException {
		Map<UUID, BoxInstance> loadedInstances = readCatalog();
		List<WalRecord> pending = new ArrayList<>();
		if (Files.exists(walFile)) {
			truncateIncompleteWalTail();
			List<String> lines = Files.readAllLines(walFile, StandardCharsets.UTF_8);
			for (int index = 0; index < lines.size(); index++) {
				String line = lines.get(index);
				if (line.isBlank()) {
					continue;
				}
				WalRecord record = parseWalRecord(line, index + 1);
				BoxInstance current = loadedInstances.get(record.instanceId());
				if (current != null && current.stateVersion() >= record.targetVersion()) {
					continue;
				}
				validateIntent(record, loadedInstances);
				pending.add(record);
			}
		}
		return new LoadResult(Collections.unmodifiableMap(loadedInstances), List.copyOf(pending), readDefinitions(catalogFile, loadedInstances));
	}

	private void truncateIncompleteWalTail() throws IOException {
		byte[] bytes = Files.readAllBytes(walFile);
		if (bytes.length == 0 || bytes[bytes.length - 1] == '\n') {
			return;
		}
		int completeEnd = bytes.length - 1;
		while (completeEnd >= 0 && bytes[completeEnd] != '\n') {
			completeEnd--;
		}
		completeEnd++;
		try (FileChannel channel = FileChannel.open(walFile, StandardOpenOption.WRITE)) {
			channel.truncate(completeEnd);
			channel.force(true);
		}
	}

	private Map<UUID, BoxInstance> readCatalog() throws IOException {
		if (!Files.exists(catalogFile)) {
			return new LinkedHashMap<>();
		}
		JsonElement json;
		try {
			json = JsonParser.parseString(Files.readString(catalogFile, StandardCharsets.UTF_8));
		} catch (JsonSyntaxException exception) {
			throw new IOException("Corrupt box catalog", exception);
		}
		if (!json.isJsonObject() || json.getAsJsonObject().get("schema_version").getAsInt() != SCHEMA_VERSION) {
			throw new IOException("Unsupported box catalog schema");
		}
		Map<UUID, BoxInstance> result = new LinkedHashMap<>();
		for (JsonElement element : json.getAsJsonObject().getAsJsonArray("instances")) {
			CatalogEntry entry = GSON.fromJson(element, CatalogEntry.class);
			UUID id = UUID.fromString(entry.uuid());
			Identifier dimensionIdentifier = Identifier.parse(entry.dimension_key());
			ResourceKey<Level> dimensionKey = ResourceKey.create(Registries.DIMENSION, dimensionIdentifier);
			if (!BoxInstance.dimensionKey(id).equals(dimensionKey)) {
				throw new IOException("Box dimension key does not match its UUID: " + id);
			}
			BoxInstance instance = new BoxInstance(id, entry.name(), dimensionKey, BoxState.valueOf(entry.state()), entry.state_version());
			if (result.putIfAbsent(id, instance) != null) {
				throw new IOException("Duplicate box in catalog: " + id);
			}
		}
		return result;
	}

	private WalRecord parseWalRecord(String line, int lineNumber) throws IOException {
		try {
			WalRecord record = GSON.fromJson(line, WalRecord.class);
			if (record == null || record.operationId() == null || record.instanceId() == null
					|| record.fromState() == null || record.toState() == null || record.recoveryAction() == null) {
				throw new IOException("Incomplete WAL record at line " + lineNumber);
			}
			return record;
		} catch (JsonSyntaxException | IllegalArgumentException exception) {
			throw new IOException("Corrupt WAL record at line " + lineNumber, exception);
		}
	}

	private void validateIntent(WalRecord record) throws IOException {
		validateIntent(record, instances);
	}

	private void validateIntent(WalRecord record, Map<UUID, BoxInstance> source) throws IOException {
		if (record.targetVersion() != record.expectedVersion() + 1) {
			throw new IOException("WAL target version must be expectedVersion + 1");
		}
		if (!record.fromState().canTransitionTo(record.toState())) {
			throw new IOException("Illegal WAL transition: " + record.fromState() + " -> " + record.toState());
		}
		BoxInstance current = source.get(record.instanceId());
		if (current == null || current.state() != record.fromState() || current.stateVersion() != record.expectedVersion()) {
			throw new IOException("WAL record does not match the catalog: " + record.instanceId());
		}
	}

	private void writeCatalog(Map<UUID, BoxInstance> snapshot, Map<UUID, BoxDefinition> definitionSnapshot) throws IOException {
		Files.createDirectories(dataDirectory);
		if (Files.exists(catalogFile)) {
			AtomicFiles.writeAtomic(backupFile, Files.readAllBytes(catalogFile));
		}
		Catalog catalog = new Catalog(SCHEMA_VERSION, snapshot.values().stream()
				.map(instance -> CatalogEntry.from(instance, definitionSnapshot.get(instance.id())))
				.toList());
		AtomicFiles.writeAtomic(catalogFile, GSON.toJson(catalog).getBytes(StandardCharsets.UTF_8));
	}

	public record WalRecord(
			UUID operationId,
			UUID instanceId,
			BoxState fromState,
			BoxState toState,
			long expectedVersion,
			long targetVersion,
			String recoveryAction,
			long timestampMillis) {
	}

	public Optional<BoxDefinition> definition(UUID id) {
		return Optional.ofNullable(definitions.get(id));
	}

	public record LoadResult(Map<UUID, BoxInstance> instances, List<WalRecord> pendingOperations,
			Map<UUID, BoxDefinition> definitions) {
	}

	private record Catalog(int schema_version, List<CatalogEntry> instances) {
	}

	private record CatalogEntry(String uuid, String name, String dimension_key, String state, long state_version,
			DefinitionEntry definition) {
		private static CatalogEntry from(BoxInstance instance, BoxDefinition definition) {
			return new CatalogEntry(
					instance.id().toString(),
					instance.name(),
					instance.dimensionKey().identifier().toString(),
					instance.state().name(),
					instance.stateVersion(),
					definition == null ? null : DefinitionEntry.from(definition));
		}
	}

	private record DefinitionEntry(String instance_id, String profile_id, String profile_json, String profile_hash,
			String dependency_hash, String level_stem_json, long seed, JsonElement scene_snapshot) {
		private static DefinitionEntry from(BoxDefinition definition) {
			JsonElement sceneSnapshot = definition.sceneSnapshot() == null ? null
					: SceneSelectionSnapshotCodec.CODEC.encodeStart(JsonOps.INSTANCE, definition.sceneSnapshot())
							.result().orElseThrow();
			return new DefinitionEntry(definition.instanceId().toString(), definition.profileId().toString(),
					definition.profileJson(), definition.profileHash(), definition.dependencyHash(),
					definition.levelStemJson(), definition.seed(), sceneSnapshot);
		}
	}

	private Map<UUID, BoxDefinition> readDefinitions(Path catalogPath, Map<UUID, BoxInstance> instances) throws IOException {
		Map<UUID, BoxDefinition> result = new LinkedHashMap<>();
		if (!Files.exists(catalogPath)) {
			return result;
		}
		JsonElement json = JsonParser.parseString(Files.readString(catalogPath, StandardCharsets.UTF_8));
		for (JsonElement element : json.getAsJsonObject().getAsJsonArray("instances")) {
			CatalogEntry entry = GSON.fromJson(element, CatalogEntry.class);
			if (entry.definition() == null) {
				continue;
			}
			DefinitionEntry definition = entry.definition();
			UUID id = UUID.fromString(definition.instance_id());
			if (!instances.containsKey(id)) {
				throw new IOException("Definition references unknown box: " + id);
			}
			SceneSelectionSnapshot sceneSnapshot = definition.scene_snapshot() == null ? null
					: SceneSelectionSnapshotCodec.CODEC.parse(JsonOps.INSTANCE, definition.scene_snapshot()).getOrThrow();
			result.put(id, new BoxDefinition(id, Identifier.parse(definition.profile_id()), definition.profile_json(),
					definition.profile_hash(), definition.dependency_hash(), definition.level_stem_json(), definition.seed(),
					sceneSnapshot));
		}
		return Collections.unmodifiableMap(result);
	}
}
