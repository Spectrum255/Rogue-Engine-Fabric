package spectrum255.rogueengine.map;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.resources.Identifier;

import spectrum255.rogueengine.persistence.AtomicFiles;

public final class MapPoolDrawService {
	private static final long MIX = 0x9E3779B97F4A7C15L;
	private static final Gson GSON = new Gson();

	private MapPoolDrawService() {
	}

	public static DrawResult draw(MapPool pool, String poolHash, long seed, Identifier poolId,
			MapPool.SelectionRange templateRange, SelectionRequest request, Map<Identifier, Set<Identifier>> tagScenes,
			MapPoolDrawState persistedState) {
		pool.validate().getOrThrow();
		Objects.requireNonNull(poolHash, "poolHash");
		Objects.requireNonNull(poolId, "poolId");
		Objects.requireNonNull(templateRange, "templateRange");
		Objects.requireNonNull(request, "request");
		List<MapPool.WeightedScene> candidates = pool.entries().stream()
				.filter(entry -> matches(pool.defaultRange(), entry.sceneId(), tagScenes))
				.filter(entry -> matches(templateRange, entry.sceneId(), tagScenes))
				.filter(entry -> matches(request.toRange(), entry.sceneId(), tagScenes))
				.toList();
		if (candidates.isEmpty()) {
			throw new IllegalArgumentException("Map pool selection range is empty");
		}

		long cycle = 0L;
		List<Identifier> remaining = new ArrayList<>(candidates.stream().map(MapPool.WeightedScene::sceneId).toList());
		if (pool.repeatPolicy() == MapPool.RepeatPolicy.CYCLE_NO_REPEAT && persistedState != null
				&& persistedState.poolId().equals(poolId) && persistedState.poolHash().equals(poolHash)) {
			cycle = persistedState.cycle();
			remaining = new ArrayList<>(persistedState.remaining());
		}

		Set<Identifier> remainingSet = new HashSet<>(remaining);
		List<MapPool.WeightedScene> eligible = candidates.stream()
				.filter(entry -> remainingSet.contains(entry.sceneId()))
				.toList();
		if (eligible.isEmpty()) {
			cycle++;
			remaining = new ArrayList<>(candidates.stream().map(MapPool.WeightedScene::sceneId).toList());
			remainingSet.clear();
			remainingSet.addAll(remaining);
			eligible = candidates;
		}

		long mixedSeed = mix64(seed ^ (poolHash.hashCode() * MIX) ^ (poolId.hashCode() * MIX));
		Random random = new Random(mixedSeed);
		MapPool.WeightedScene selected = weighted(eligible, random);
		remaining.remove(selected.sceneId());
		MapSelection selection = new MapSelection(poolId, poolHash, selected.sceneId(), selected.weight(), seed, cycle);
		MapPoolDrawState nextState = new MapPoolDrawState(poolId, poolHash, List.copyOf(remaining), cycle);
		return new DrawResult(selection, nextState);
	}

	private static long mix64(long value) {
		value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
		value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
		return value ^ (value >>> 31);
	}

	private static boolean matches(MapPool.SelectionRange range, Identifier sceneId, Map<Identifier, Set<Identifier>> tagScenes) {
		if (range.isAll()) {
			return true;
		}
		Set<Identifier> allowed = new HashSet<>(range.sceneIds());
		for (Identifier tag : range.tags()) {
			allowed.addAll(tagScenes.getOrDefault(tag, Set.of()));
		}
		return allowed.contains(sceneId);
	}

	private static MapPool.WeightedScene weighted(List<MapPool.WeightedScene> entries, Random random) {
		long total = entries.stream().mapToLong(MapPool.WeightedScene::weight).sum();
		if (total <= 0) {
			throw new IllegalArgumentException("Map pool total weight must be positive");
		}
		long roll = Math.min(total - 1, (long) (random.nextDouble() * total));
		for (MapPool.WeightedScene entry : entries) {
			roll -= entry.weight();
			if (roll < 0) {
				return entry;
			}
		}
		return entries.getLast();
	}

	public static void saveState(Path file, MapPoolDrawState state) throws IOException {
		Objects.requireNonNull(file, "file");
		Objects.requireNonNull(state, "state");
		JsonObject root = new JsonObject();
		root.addProperty("pool_id", state.poolId().toString());
		root.addProperty("pool_hash", state.poolHash());
		JsonArray remaining = new JsonArray();
		state.remaining().forEach(id -> remaining.add(id.toString()));
		root.add("remaining", remaining);
		root.addProperty("cycle", state.cycle());
		AtomicFiles.writeAtomic(file, GSON.toJson(root).getBytes(StandardCharsets.UTF_8));
	}

	public static Optional<MapPoolDrawState> loadState(Path file, Identifier expectedPoolId) throws IOException {
		Objects.requireNonNull(file, "file");
		Objects.requireNonNull(expectedPoolId, "expectedPoolId");
		if (!Files.exists(file)) {
			return Optional.empty();
		}
		JsonElement parsed = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
		if (!parsed.isJsonObject()) {
			throw new IOException("Invalid map pool draw state");
		}
		JsonObject root = parsed.getAsJsonObject();
		Identifier poolId = Identifier.parse(root.get("pool_id").getAsString());
		if (!poolId.equals(expectedPoolId)) {
			throw new IOException("Map pool draw state ID mismatch");
		}
		String poolHash = root.get("pool_hash").getAsString();
		JsonArray remainingJson = root.getAsJsonArray("remaining");
		List<Identifier> remaining = new ArrayList<>();
		for (JsonElement element : remainingJson) {
			remaining.add(Identifier.parse(element.getAsString()));
		}
		long cycle = root.get("cycle").getAsLong();
		return Optional.of(new MapPoolDrawState(poolId, poolHash, List.copyOf(remaining), cycle));
	}

	public record DrawResult(MapSelection selection, MapPoolDrawState nextState) {
	}
}
