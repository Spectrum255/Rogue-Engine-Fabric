package spectrum255.rogueengine.profile;

import java.util.Map;
import java.util.Optional;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.Identifier;
import net.minecraft.world.level.chunk.ChunkGenerator;

public final class BoxProfileCodec {
	private BoxProfileCodec() {
	}

	private record GeneratorShape(
			String kind,
			Optional<Identifier> id,
			Optional<ChunkGenerator> value,
			Map<String, Double> overrides,
			Optional<String> preset) {
	}

	private static final Codec<GeneratorShape> GENERATOR_SHAPE_CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.STRING.fieldOf("kind").forGetter(GeneratorShape::kind),
			Identifier.CODEC.optionalFieldOf("id").forGetter(GeneratorShape::id),
			ChunkGenerator.CODEC.optionalFieldOf("value").forGetter(GeneratorShape::value),
			Codec.unboundedMap(Codec.STRING, Codec.DOUBLE).optionalFieldOf("overrides", Map.of())
					.forGetter(GeneratorShape::overrides),
			Codec.STRING.optionalFieldOf("preset").forGetter(GeneratorShape::preset))
			.apply(instance, GeneratorShape::new));

	private static final Codec<BoxProfile.Generator> GENERATOR_CODEC = GENERATOR_SHAPE_CODEC.flatXmap(
			shape -> switch (shape.kind()) {
				case "stem" -> shape.id()
						.map(id -> DataResult.success(new BoxProfile.StemGenerator("stem", id)))
						.orElseGet(() -> DataResult.error(() -> "stem generator requires id"));
				case "inline" -> shape.value()
						.map(value -> DataResult.success(new BoxProfile.InlineGenerator("inline", value)))
						.orElseGet(() -> DataResult.error(() -> "inline generator requires value"));
				case "template" -> {
					try {
						yield shape.id()
								.map(id -> DataResult.success(new BoxProfile.TemplateGenerator(
									"template", id, shape.overrides(), shape.preset())))
								.orElseGet(() -> DataResult.error(() -> "template generator requires id"));
					} catch (IllegalArgumentException exception) {
						yield DataResult.error(exception::getMessage);
					}
				}
				default -> DataResult.error(() -> "Unknown generator.kind: " + shape.kind());
			},
			generator -> DataResult.success(switch (generator) {
				case BoxProfile.StemGenerator stem -> new GeneratorShape(
						stem.kind(), Optional.of(stem.id()), Optional.empty(), Map.of(), Optional.empty());
				case BoxProfile.InlineGenerator inline -> new GeneratorShape(
						inline.kind(), Optional.empty(), Optional.of(inline.value()), Map.of(), Optional.empty());
				case BoxProfile.TemplateGenerator template -> new GeneratorShape(
						template.kind(), Optional.of(template.id()), Optional.empty(), template.overrides(), template.preset());
			}));

	private static final Codec<BoxProfile.Seed> SEED_CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.STRING.fieldOf("mode").forGetter(BoxProfile.Seed::mode),
			Codec.LONG.optionalFieldOf("value").forGetter(BoxProfile.Seed::value))
			.apply(instance, BoxProfile.Seed::new));

	private static final Codec<BoxProfile.FixedSpawn> FIXED_SPAWN_CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.STRING.fieldOf("mode").forGetter(BoxProfile.FixedSpawn::kind),
			Codec.DOUBLE.fieldOf("x").forGetter(BoxProfile.FixedSpawn::x),
			Codec.DOUBLE.fieldOf("y").forGetter(BoxProfile.FixedSpawn::y),
			Codec.DOUBLE.fieldOf("z").forGetter(BoxProfile.FixedSpawn::z),
			Codec.FLOAT.optionalFieldOf("yaw", 0.0F).forGetter(BoxProfile.FixedSpawn::yaw))
			.apply(instance, BoxProfile.FixedSpawn::new));

	private static final Codec<BoxProfile.SearchSpawn> SEARCH_SPAWN_CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.STRING.fieldOf("mode").forGetter(BoxProfile.SearchSpawn::kind),
			Codec.intRange(1, 64).fieldOf("radius").forGetter(BoxProfile.SearchSpawn::radius))
			.apply(instance, BoxProfile.SearchSpawn::new));

	private static final Codec<BoxProfile.Spawn> SPAWN_CODEC = Codec
			.either(FIXED_SPAWN_CODEC, SEARCH_SPAWN_CODEC)
			.xmap(either -> either.map(left -> left, right -> right),
					spawn -> spawn instanceof BoxProfile.FixedSpawn fixed
							? Either.left(fixed)
							: Either.right((BoxProfile.SearchSpawn) spawn));

	private static final Codec<BoxProfile.Border> BORDER_CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.DOUBLE.fieldOf("center_x").forGetter(BoxProfile.Border::centerX),
			Codec.DOUBLE.fieldOf("center_z").forGetter(BoxProfile.Border::centerZ),
			Codec.DOUBLE.fieldOf("initial_size").forGetter(BoxProfile.Border::initialSize),
			Codec.DOUBLE.fieldOf("warning_blocks").forGetter(BoxProfile.Border::warningBlocks))
			.apply(instance, BoxProfile.Border::new));

	private static final Codec<BoxProfile.InventoryPolicy> INVENTORY_CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.BOOL.optionalFieldOf("bring_on_enter", true).forGetter(BoxProfile.InventoryPolicy::bringOnEnter),
			Codec.BOOL.optionalFieldOf("take_on_exit", true).forGetter(BoxProfile.InventoryPolicy::takeOnExit))
			.apply(instance, BoxProfile.InventoryPolicy::new));

	public static final Codec<BoxProfile> CODEC = RecordCodecBuilder.<BoxProfile>create(instance -> instance.group(
			Codec.intRange(1, 2).fieldOf("schema_version").forGetter(BoxProfile::schemaVersion),
			Identifier.CODEC.fieldOf("dimension_type").forGetter(BoxProfile::dimensionType),
			GENERATOR_CODEC.fieldOf("generator").forGetter(BoxProfile::generator),
			SEED_CODEC.fieldOf("seed").forGetter(BoxProfile::seed),
			SPAWN_CODEC.fieldOf("spawn").forGetter(BoxProfile::spawn),
			BORDER_CODEC.fieldOf("border").forGetter(BoxProfile::border),
			INVENTORY_CODEC.optionalFieldOf("inventory", BoxProfile.InventoryPolicy.DEFAULT).forGetter(BoxProfile::inventory))
			.apply(instance, BoxProfile::new))
			.validate(BoxProfile::validate);
}
