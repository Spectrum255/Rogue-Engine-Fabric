package spectrum255.rogueengine.map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.Identifier;

public final class MapPoolCodec {
	private MapPoolCodec() {
	}

	private static final Codec<MapPool.WeightedScene> WEIGHTED_SCENE_CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Identifier.CODEC.fieldOf("scene").forGetter(MapPool.WeightedScene::sceneId),
			Codec.intRange(1, Integer.MAX_VALUE).fieldOf("weight").forGetter(MapPool.WeightedScene::weight))
			.apply(instance, MapPool.WeightedScene::new));

	public static final Codec<MapPool.SelectionRange> SELECTION_RANGE_CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Identifier.CODEC.listOf().optionalFieldOf("scenes", java.util.List.of()).forGetter(MapPool.SelectionRange::sceneIds),
			Identifier.CODEC.listOf().optionalFieldOf("tags", java.util.List.of()).forGetter(MapPool.SelectionRange::tags))
			.apply(instance, MapPool.SelectionRange::new));

	private static final Codec<MapPool.PlacementBudget> PLACEMENT_BUDGET_CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.intRange(1, 1024).fieldOf("max_chunks_per_tick").forGetter(MapPool.PlacementBudget::maxChunksPerTick),
			Codec.intRange(1, 50).fieldOf("max_tick_ms").forGetter(MapPool.PlacementBudget::maxTickMs),
			Codec.intRange(0, 100_000).fieldOf("max_entities").forGetter(MapPool.PlacementBudget::maxEntities),
			Codec.intRange(0, 100_000).fieldOf("max_block_entities").forGetter(MapPool.PlacementBudget::maxBlockEntities))
			.apply(instance, MapPool.PlacementBudget::new));

	public static final Codec<MapPool> CODEC = RecordCodecBuilder.<MapPool>create(instance -> instance.group(
			Codec.intRange(1, 1).fieldOf("schema_version").forGetter(MapPool::schemaVersion),
			WEIGHTED_SCENE_CODEC.listOf().fieldOf("entries").forGetter(MapPool::entries),
			SELECTION_RANGE_CODEC.optionalFieldOf("default_range", new MapPool.SelectionRange(java.util.List.of(), java.util.List.of()))
					.forGetter(MapPool::defaultRange),
			MapPool.RepeatPolicy.CODEC.optionalFieldOf("repeat_policy", MapPool.RepeatPolicy.ALLOW).forGetter(MapPool::repeatPolicy),
			MapSceneCodec.REFRESH_CODEC.optionalFieldOf("refresh", new MapScene.RefreshPolicy(
					java.util.List.of(MapScene.RefreshTrigger.MANUAL, MapScene.RefreshTrigger.ON_EMPTY), MapScene.PlayerResetPolicy.NONE))
					.forGetter(MapPool::refresh),
			PLACEMENT_BUDGET_CODEC.fieldOf("budget").forGetter(MapPool::budget))
			.apply(instance, MapPool::new))
			.validate(MapPool::validate);
}
