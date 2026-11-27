package spectrum255.rogueengine.map;

import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

public final class SceneSelectionSnapshotCodec {
	private SceneSelectionSnapshotCodec() {
	}

	private static final Codec<UUID> UUID_CODEC = Codec.STRING.xmap(UUID::fromString, UUID::toString);

	private static final Codec<SceneSelectionSnapshot.Hashes> HASHES_CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.STRING.fieldOf("pool_hash").forGetter(SceneSelectionSnapshot.Hashes::poolHash),
			Codec.STRING.fieldOf("scene_hash").forGetter(SceneSelectionSnapshot.Hashes::sceneHash),
			MapScene.SourceKind.CODEC.optionalFieldOf("source_kind", MapScene.SourceKind.STRUCTURE)
					.forGetter(SceneSelectionSnapshot.Hashes::sourceKind),
			Identifier.CODEC.optionalFieldOf("source_id", Identifier.withDefaultNamespace("legacy"))
					.forGetter(SceneSelectionSnapshot.Hashes::sourceId))
			.apply(instance, SceneSelectionSnapshot.Hashes::new));

	public static final Codec<SceneSelectionSnapshot> CODEC = RecordCodecBuilder.<SceneSelectionSnapshot>create(instance -> instance.group(
			UUID_CODEC.fieldOf("instance_id").forGetter(SceneSelectionSnapshot::instanceId),
			Identifier.CODEC.fieldOf("pool_id").forGetter(SceneSelectionSnapshot::poolId),
			Identifier.CODEC.fieldOf("scene_id").forGetter(SceneSelectionSnapshot::sceneId),
			HASHES_CODEC.fieldOf("hashes").forGetter(SceneSelectionSnapshot::hashes),
			Codec.LONG.fieldOf("draw_seed").forGetter(SceneSelectionSnapshot::drawSeed),
			Codec.LONG.fieldOf("draw_cycle").forGetter(SceneSelectionSnapshot::drawCycle),
			BlockPos.CODEC.fieldOf("anchor").forGetter(SceneSelectionSnapshot::anchor),
			BlockPos.CODEC.fieldOf("spawn").forGetter(SceneSelectionSnapshot::spawn),
			Rotation.CODEC.fieldOf("rotation").forGetter(SceneSelectionSnapshot::rotation),
			Mirror.CODEC.fieldOf("mirror").forGetter(SceneSelectionSnapshot::mirror),
			MapSceneCodec.BOUNDS_CODEC.fieldOf("bounds").forGetter(SceneSelectionSnapshot::bounds),
			Identifier.CODEC.listOf().optionalFieldOf("processors", java.util.List.of())
					.forGetter(SceneSelectionSnapshot::processorIds),
			Codec.STRING.fieldOf("cache_path").forGetter(SceneSelectionSnapshot::cachePath),
			MapSceneCodec.REFRESH_CODEC.fieldOf("refresh").forGetter(SceneSelectionSnapshot::refresh),
			Codec.LONG.fieldOf("reset_count").forGetter(SceneSelectionSnapshot::resetCount),
			Codec.STRING.optionalFieldOf("player_state_path").forGetter(SceneSelectionSnapshot::playerStatePath))
			.apply(instance, SceneSelectionSnapshot::new))
			.validate(SceneSelectionSnapshot::validate);
}
