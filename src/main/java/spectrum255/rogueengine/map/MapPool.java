package spectrum255.rogueengine.map;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.mojang.serialization.DataResult;

import net.minecraft.resources.Identifier;
import net.minecraft.util.StringRepresentable;

public record MapPool(
		int schemaVersion,
		List<WeightedScene> entries,
		SelectionRange defaultRange,
		RepeatPolicy repeatPolicy,
		MapScene.RefreshPolicy refresh,
		PlacementBudget budget) {
	public MapPool {
		entries = List.copyOf(entries);
		Objects.requireNonNull(defaultRange, "defaultRange");
		Objects.requireNonNull(repeatPolicy, "repeatPolicy");
		Objects.requireNonNull(refresh, "refresh");
		Objects.requireNonNull(budget, "budget");
	}

	public DataResult<MapPool> validate() {
		if (schemaVersion != 1) {
			return DataResult.error(() -> "Unsupported map pool schema_version: " + schemaVersion);
		}
		if (entries.isEmpty()) {
			return DataResult.error(() -> "Map pool requires at least one entry");
		}
		Set<Identifier> seen = new HashSet<>();
		for (WeightedScene entry : entries) {
			if (entry.weight() <= 0) {
				return DataResult.error(() -> "Map pool weights must be positive");
			}
			if (!seen.add(entry.sceneId())) {
				return DataResult.error(() -> "Duplicate scene in map pool: " + entry.sceneId());
			}
		}
		if (!budget.valid()) {
			return DataResult.error(() -> "Map pool placement budget is invalid");
		}
		if (refresh.triggers().isEmpty()) {
			return DataResult.error(() -> "Map pool refresh requires at least one trigger");
		}
		return DataResult.success(this);
	}

	public record WeightedScene(Identifier sceneId, int weight) {
		public WeightedScene {
			Objects.requireNonNull(sceneId, "sceneId");
		}
	}

	public record SelectionRange(List<Identifier> sceneIds, List<Identifier> tags) {
		public SelectionRange {
			sceneIds = List.copyOf(sceneIds);
			tags = List.copyOf(tags);
		}

		public boolean isAll() {
			return sceneIds.isEmpty() && tags.isEmpty();
		}
	}

	public enum RepeatPolicy implements StringRepresentable {
		ALLOW("allow"),
		CYCLE_NO_REPEAT("cycle_no_repeat");

		public static final com.mojang.serialization.Codec<RepeatPolicy> CODEC =
				StringRepresentable.fromEnum(RepeatPolicy::values);
		private final String id;

		RepeatPolicy(String id) {
			this.id = id;
		}

		@Override
		public String getSerializedName() {
			return id;
		}
	}

	public record PlacementBudget(int maxChunksPerTick, int maxTickMs, int maxEntities, int maxBlockEntities) {
		public boolean valid() {
			return maxChunksPerTick > 0 && maxChunksPerTick <= 1024
					&& maxTickMs > 0 && maxTickMs <= 50
					&& maxEntities >= 0 && maxEntities <= 100_000
					&& maxBlockEntities >= 0 && maxBlockEntities <= 100_000;
		}
	}
}
