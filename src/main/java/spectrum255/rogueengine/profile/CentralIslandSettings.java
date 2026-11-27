package spectrum255.rogueengine.profile;

import java.util.Objects;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.state.BlockState;

public record CentralIslandSettings(
		SurfaceMode surfaceMode,
		int centerY,
		int maxThickness,
		double bottomTaper,
		double surfaceRoughness,
		double edgeNoise,
		Optional<CustomLayers> customLayers) {
	public static final Codec<CentralIslandSettings> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			SurfaceMode.CODEC.fieldOf("surface_mode").forGetter(CentralIslandSettings::surfaceMode),
			Codec.INT.fieldOf("center_y").forGetter(CentralIslandSettings::centerY),
			Codec.intRange(16, 256).fieldOf("max_thickness").forGetter(CentralIslandSettings::maxThickness),
			Codec.DOUBLE.fieldOf("bottom_taper").forGetter(CentralIslandSettings::bottomTaper),
			Codec.DOUBLE.fieldOf("surface_roughness").forGetter(CentralIslandSettings::surfaceRoughness),
			Codec.DOUBLE.fieldOf("edge_noise").forGetter(CentralIslandSettings::edgeNoise),
			CustomLayers.CODEC.optionalFieldOf("custom_layers").forGetter(CentralIslandSettings::customLayers))
			.apply(instance, CentralIslandSettings::new));

	public CentralIslandSettings {
		Objects.requireNonNull(surfaceMode, "surfaceMode");
		Objects.requireNonNull(customLayers, "customLayers");
	}

	public DataResult<CentralIslandSettings> validate() {
		if (centerY < -64 || centerY > 319 || maxThickness < 16 || maxThickness > 256
				|| !Double.isFinite(bottomTaper) || bottomTaper < 0.25 || bottomTaper > 4.0
				|| !Double.isFinite(surfaceRoughness) || surfaceRoughness < 0.0 || surfaceRoughness > 32.0
				|| !Double.isFinite(edgeNoise) || edgeNoise < 0.0 || edgeNoise > 1.0) {
			return DataResult.error(() -> "Invalid central island parameters");
		}
		if (surfaceMode == SurfaceMode.CUSTOM_LAYERS) {
			if (customLayers.isEmpty()) {
				return DataResult.error(() -> "custom_layers is required for custom layer islands");
			}
			return customLayers.get().validate().map(ignored -> this);
		}
		if (customLayers.isPresent()) {
			return DataResult.error(() -> "custom_layers is only valid for custom layer islands");
		}
		return DataResult.success(this);
	}

	public int spawnY() {
		return centerY + 1;
	}

	public enum SurfaceMode implements StringRepresentable {
		VANILLA_SURFACE("vanilla_surface"),
		CUSTOM_LAYERS("custom_layers");

		public static final Codec<SurfaceMode> CODEC = StringRepresentable.fromEnum(SurfaceMode::values);
		private final String id;

		SurfaceMode(String id) {
			this.id = id;
		}

		@Override
		public String getSerializedName() {
			return id;
		}
	}

	public record CustomLayers(
			BlockState topBlock,
			BlockState soilBlock,
			BlockState rockBlock,
			int topDepth,
			int soilDepth,
			Optional<BlockState> oreBlock,
			double oreChance,
			int oreMinDepth,
			int oreMaxDepth) {
		public static final Codec<CustomLayers> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				BlockState.FULL_CODEC.fieldOf("top_block").forGetter(CustomLayers::topBlock),
				BlockState.FULL_CODEC.fieldOf("soil_block").forGetter(CustomLayers::soilBlock),
				BlockState.FULL_CODEC.fieldOf("rock_block").forGetter(CustomLayers::rockBlock),
				Codec.intRange(1, 16).fieldOf("top_depth").forGetter(CustomLayers::topDepth),
				Codec.intRange(0, 64).fieldOf("soil_depth").forGetter(CustomLayers::soilDepth),
				BlockState.FULL_CODEC.optionalFieldOf("ore_block").forGetter(CustomLayers::oreBlock),
				Codec.DOUBLE.fieldOf("ore_chance").forGetter(CustomLayers::oreChance),
				Codec.INT.fieldOf("ore_min_depth").forGetter(CustomLayers::oreMinDepth),
				Codec.INT.fieldOf("ore_max_depth").forGetter(CustomLayers::oreMaxDepth))
				.apply(instance, CustomLayers::new));

		public CustomLayers {
			Objects.requireNonNull(topBlock, "topBlock");
			Objects.requireNonNull(soilBlock, "soilBlock");
			Objects.requireNonNull(rockBlock, "rockBlock");
			Objects.requireNonNull(oreBlock, "oreBlock");
		}

		public DataResult<CustomLayers> validate() {
			if (topBlock.isAir() || soilBlock.isAir() || rockBlock.isAir()
					|| topDepth < 1 || topDepth > 16 || soilDepth < 0 || soilDepth > 64
					|| !Double.isFinite(oreChance) || oreChance < 0.0 || oreChance > 1.0
					|| oreMinDepth < 0 || oreMaxDepth < oreMinDepth
					|| oreBlock.isPresent() && oreBlock.get().isAir()) {
				return DataResult.error(() -> "Invalid custom island layers");
			}
			return DataResult.success(this);
		}
	}
}
