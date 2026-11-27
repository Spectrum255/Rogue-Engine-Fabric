package spectrum255.rogueengine.map;

import java.nio.file.Path;
import java.util.Objects;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.DataResult;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

public record SceneSelectionSnapshot(
		UUID instanceId,
		Identifier poolId,
		Identifier sceneId,
		Hashes hashes,
		long drawSeed,
		long drawCycle,
		BlockPos anchor,
		BlockPos spawn,
		Rotation rotation,
		Mirror mirror,
		MapScene.SceneBounds bounds,
		List<Identifier> processorIds,
		String cachePath,
		MapScene.RefreshPolicy refresh,
		long resetCount,
		Optional<String> playerStatePath) {
	public SceneSelectionSnapshot {
		Objects.requireNonNull(instanceId, "instanceId");
		Objects.requireNonNull(poolId, "poolId");
		Objects.requireNonNull(sceneId, "sceneId");
		Objects.requireNonNull(hashes, "hashes");
		Objects.requireNonNull(anchor, "anchor");
		Objects.requireNonNull(spawn, "spawn");
		Objects.requireNonNull(rotation, "rotation");
		Objects.requireNonNull(mirror, "mirror");
		Objects.requireNonNull(bounds, "bounds");
		processorIds = List.copyOf(processorIds);
		Objects.requireNonNull(cachePath, "cachePath");
		Objects.requireNonNull(refresh, "refresh");
		Objects.requireNonNull(playerStatePath, "playerStatePath");
	}

	public SceneSelectionSnapshot withResetCount(long resetCount) {
		return new SceneSelectionSnapshot(instanceId, poolId, sceneId, hashes, drawSeed, drawCycle, anchor, spawn,
				rotation, mirror, bounds, processorIds, cachePath, refresh, resetCount, playerStatePath);
	}

	public String poolHash() {
		return hashes.poolHash();
	}

	public String sceneHash() {
		return hashes.sceneHash();
	}

	public MapScene.SourceKind sourceKind() {
		return hashes.sourceKind();
	}

	public Identifier sourceId() {
		return hashes.sourceId();
	}

	public record Hashes(String poolHash, String sceneHash, MapScene.SourceKind sourceKind, Identifier sourceId) {
		public Hashes(String poolHash, String sceneHash) {
			this(poolHash, sceneHash, MapScene.SourceKind.STRUCTURE, Identifier.withDefaultNamespace("legacy"));
		}

		public Hashes {
			Objects.requireNonNull(poolHash, "poolHash");
			Objects.requireNonNull(sceneHash, "sceneHash");
			Objects.requireNonNull(sourceKind, "sourceKind");
			Objects.requireNonNull(sourceId, "sourceId");
		}
	}

	public static SceneSelectionSnapshot capture(UUID instanceId, MapSelection selection, MapScene scene,
			SceneCacheService.CachedScene cached) {
		return new SceneSelectionSnapshot(
				instanceId,
				selection.poolId(),
				selection.sceneId(),
				new Hashes(selection.poolHash(), cached.hash(), scene.source().kind(), scene.source().id()),
				selection.seed(),
				selection.cycle(),
				scene.anchor(),
				scene.spawn(),
				scene.rotation(),
				scene.mirror(),
				scene.bounds(),
				scene.processorIds(),
				cached.relativePath(),
				scene.refresh(),
				0L,
				scene.refresh().playerReset() == MapScene.PlayerResetPolicy.FULL
						? Optional.of("scenes/" + instanceId + "/players")
						: Optional.empty());
	}

	public DataResult<SceneSelectionSnapshot> validate() {
		if (!isHash(hashes.poolHash()) || !isHash(hashes.sceneHash())) {
			return DataResult.error(() -> "Scene selection hashes must be 64 lowercase hex characters");
		}
		if (!bounds.valid(512)) {
			return DataResult.error(() -> "Scene selection bounds are invalid or too large");
		}
		if (resetCount < 0) {
			return DataResult.error(() -> "Scene reset count cannot be negative");
		}
		Path path = Path.of(cachePath).normalize();
		String normalized = path.toString().replace('\\', '/');
		String prefix = "scenes/" + instanceId + "/";
		if (path.isAbsolute() || !normalized.equals(cachePath) || !normalized.startsWith(prefix)
				|| normalized.length() == prefix.length()) {
			return DataResult.error(() -> "Scene cache path must stay inside the instance scene directory");
		}
		if (playerStatePath.isPresent() && !validPlayerStatePath(playerStatePath.get())) {
			return DataResult.error(() -> "Player state path must stay inside the instance scene directory");
		}
		return DataResult.success(this);
	}

	private boolean validPlayerStatePath(String value) {
		Path path = Path.of(value).normalize();
		String normalized = path.toString().replace('\\', '/');
		return !path.isAbsolute() && normalized.equals(value)
				&& normalized.startsWith("scenes/" + instanceId + "/");
	}

	private static boolean isHash(String value) {
		return value != null && value.matches("[0-9a-f]{64}");
	}
}
