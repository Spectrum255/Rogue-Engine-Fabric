package spectrum255.rogueengine.map;

import java.util.List;
import java.util.Objects;

import com.mojang.serialization.DataResult;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

public record MapScene(
		int schemaVersion,
		Source source,
		SceneBounds bounds,
		BlockPos anchor,
		BlockPos spawn,
		Rotation rotation,
		Mirror mirror,
		List<Identifier> processorIds,
		RefreshPolicy refresh) {
	private static final int MAX_SCENE_SIZE = 512;

	public MapScene {
		Objects.requireNonNull(source, "source");
		Objects.requireNonNull(bounds, "bounds");
		Objects.requireNonNull(anchor, "anchor");
		Objects.requireNonNull(spawn, "spawn");
		Objects.requireNonNull(rotation, "rotation");
		Objects.requireNonNull(mirror, "mirror");
		processorIds = List.copyOf(processorIds);
		Objects.requireNonNull(refresh, "refresh");
	}

	public MapScene(int schemaVersion, Identifier structureId, SceneBounds bounds, BlockPos anchor,
			BlockPos spawn, Rotation rotation, Mirror mirror, List<Identifier> processorIds, RefreshPolicy refresh) {
		this(schemaVersion, new Source(SourceKind.STRUCTURE, structureId), bounds, anchor, spawn,
				rotation, mirror, processorIds, refresh);
	}

	public Identifier structureId() {
		return source.kind() == SourceKind.STRUCTURE ? source.id() : null;
	}

	public Identifier snapshotId() {
		return source.kind() == SourceKind.SNAPSHOT ? source.id() : null;
	}

	public DataResult<MapScene> validate() {
		if (schemaVersion != 1 && schemaVersion != 2) {
			return DataResult.error(() -> "Unsupported map scene schema_version: " + schemaVersion);
		}
		if (schemaVersion == 1 && source.kind() != SourceKind.STRUCTURE) {
			return DataResult.error(() -> "Map scene schema v1 only supports structure sources");
		}
		if (!bounds.valid(MAX_SCENE_SIZE)) {
			return DataResult.error(() -> "Map scene bounds are invalid or exceed " + MAX_SCENE_SIZE + " blocks per axis");
		}
		if (!bounds.contains(spawn)) {
			return DataResult.error(() -> "Map scene spawn must be inside its bounds");
		}
		if (refresh.triggers().isEmpty()) {
			return DataResult.error(() -> "Map scene refresh requires at least one trigger");
		}
		return DataResult.success(this);
	}

	public enum SourceKind implements StringRepresentable {
		STRUCTURE("structure"),
		SNAPSHOT("snapshot");

		public static final com.mojang.serialization.Codec<SourceKind> CODEC =
				StringRepresentable.fromEnum(SourceKind::values);
		private final String id;

		SourceKind(String id) {
			this.id = id;
		}

		@Override
		public String getSerializedName() {
			return id;
		}
	}

	public record Source(SourceKind kind, Identifier id) {
		public Source {
			Objects.requireNonNull(kind, "kind");
			Objects.requireNonNull(id, "id");
		}
	}

	public record SceneBounds(BlockPos min, BlockPos max) {
		public SceneBounds {
			Objects.requireNonNull(min, "min");
			Objects.requireNonNull(max, "max");
		}

		public boolean valid(int maxSize) {
			int sizeX = max.getX() - min.getX() + 1;
			int sizeY = max.getY() - min.getY() + 1;
			int sizeZ = max.getZ() - min.getZ() + 1;
			return sizeX > 0 && sizeY > 0 && sizeZ > 0
					&& sizeX <= maxSize && sizeY <= maxSize && sizeZ <= maxSize;
		}

		public boolean contains(BlockPos pos) {
			return pos.getX() >= min.getX() && pos.getX() <= max.getX()
					&& pos.getY() >= min.getY() && pos.getY() <= max.getY()
					&& pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
		}
	}

	public record RefreshPolicy(List<RefreshTrigger> triggers, PlayerResetPolicy playerReset) {
		public RefreshPolicy {
			triggers = List.copyOf(triggers);
			Objects.requireNonNull(playerReset, "playerReset");
		}
	}

	public enum RefreshTrigger implements StringRepresentable {
		MANUAL("manual"),
		ON_EMPTY("on_empty"),
		ON_DEATH("on_death"),
		ON_COMPLETED("on_completed");

		public static final com.mojang.serialization.Codec<RefreshTrigger> CODEC =
				StringRepresentable.fromEnum(RefreshTrigger::values);
		private final String id;

		RefreshTrigger(String id) {
			this.id = id;
		}

		@Override
		public String getSerializedName() {
			return id;
		}
	}

	public enum PlayerResetPolicy implements StringRepresentable {
		NONE("none"),
		FULL("full");

		public static final com.mojang.serialization.Codec<PlayerResetPolicy> CODEC =
				StringRepresentable.fromEnum(PlayerResetPolicy::values);
		private final String id;

		PlayerResetPolicy(String id) {
			this.id = id;
		}

		@Override
		public String getSerializedName() {
			return id;
		}
	}
}
