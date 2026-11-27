package spectrum255.rogueengine.map;

import java.util.List;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

public final class MapSceneCodec {
	private MapSceneCodec() {
	}

	static final Codec<MapScene.SceneBounds> BOUNDS_CODEC = RecordCodecBuilder.create(instance -> instance.group(
			BlockPos.CODEC.fieldOf("min").forGetter(MapScene.SceneBounds::min),
			BlockPos.CODEC.fieldOf("max").forGetter(MapScene.SceneBounds::max))
			.apply(instance, MapScene.SceneBounds::new));

	static final Codec<MapScene.RefreshPolicy> REFRESH_CODEC = RecordCodecBuilder.create(instance -> instance.group(
			MapScene.RefreshTrigger.CODEC.listOf().fieldOf("triggers").forGetter(MapScene.RefreshPolicy::triggers),
			MapScene.PlayerResetPolicy.CODEC.fieldOf("player_reset").forGetter(MapScene.RefreshPolicy::playerReset))
			.apply(instance, MapScene.RefreshPolicy::new));

	private static final Codec<MapScene.Source> SOURCE_CODEC = RecordCodecBuilder.create(instance -> instance.group(
			MapScene.SourceKind.CODEC.fieldOf("kind").forGetter(MapScene.Source::kind),
			Identifier.CODEC.fieldOf("id").forGetter(MapScene.Source::id))
			.apply(instance, MapScene.Source::new));

	private static final MapCodec<SceneFields> FIELDS_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			BOUNDS_CODEC.fieldOf("bounds").forGetter(SceneFields::bounds),
			BlockPos.CODEC.fieldOf("anchor").forGetter(SceneFields::anchor),
			BlockPos.CODEC.fieldOf("spawn").forGetter(SceneFields::spawn),
			Rotation.CODEC.fieldOf("rotation").forGetter(SceneFields::rotation),
			Mirror.CODEC.fieldOf("mirror").forGetter(SceneFields::mirror),
			Identifier.CODEC.listOf().optionalFieldOf("processors", List.of()).forGetter(SceneFields::processors),
			REFRESH_CODEC.fieldOf("refresh").forGetter(SceneFields::refresh))
			.apply(instance, SceneFields::new));

	private static final Codec<LegacyScene> LEGACY_CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.intRange(1, 1).fieldOf("schema_version").forGetter(LegacyScene::schemaVersion),
			Identifier.CODEC.fieldOf("structure_id").forGetter(LegacyScene::structureId),
			FIELDS_CODEC.forGetter(LegacyScene::fields))
			.apply(instance, LegacyScene::new));

	private static final Codec<SourcedScene> SOURCED_CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.intRange(2, 2).fieldOf("schema_version").forGetter(SourcedScene::schemaVersion),
			SOURCE_CODEC.fieldOf("source").forGetter(SourcedScene::source),
			FIELDS_CODEC.forGetter(SourcedScene::fields))
			.apply(instance, SourcedScene::new));

	public static final Codec<MapScene> CODEC = Codec.either(LEGACY_CODEC, SOURCED_CODEC)
			.xmap(either -> either.map(MapSceneCodec::fromLegacy, MapSceneCodec::fromSourced),
					scene -> scene.schemaVersion() == 1 ? Either.left(legacy(scene)) : Either.right(sourced(scene)))
			.validate(MapScene::validate);

	private static MapScene fromLegacy(LegacyScene legacy) {
		SceneFields fields = legacy.fields();
		return new MapScene(legacy.schemaVersion(), legacy.structureId(), fields.bounds(), fields.anchor(),
				fields.spawn(), fields.rotation(), fields.mirror(), fields.processors(), fields.refresh());
	}

	private static MapScene fromSourced(SourcedScene sourced) {
		SceneFields fields = sourced.fields();
		return new MapScene(sourced.schemaVersion(), sourced.source(), fields.bounds(), fields.anchor(),
				fields.spawn(), fields.rotation(), fields.mirror(), fields.processors(), fields.refresh());
	}

	private static LegacyScene legacy(MapScene scene) {
		return new LegacyScene(scene.schemaVersion(), scene.structureId(), fields(scene));
	}

	private static SourcedScene sourced(MapScene scene) {
		return new SourcedScene(scene.schemaVersion(), scene.source(), fields(scene));
	}

	private static SceneFields fields(MapScene scene) {
		return new SceneFields(scene.bounds(), scene.anchor(), scene.spawn(), scene.rotation(), scene.mirror(),
				scene.processorIds(), scene.refresh());
	}

	private record SceneFields(MapScene.SceneBounds bounds, BlockPos anchor, BlockPos spawn,
			Rotation rotation, Mirror mirror, List<Identifier> processors, MapScene.RefreshPolicy refresh) {
	}

	private record LegacyScene(int schemaVersion, Identifier structureId, SceneFields fields) {
	}

	private record SourcedScene(int schemaVersion, MapScene.Source source, SceneFields fields) {
	}
}
