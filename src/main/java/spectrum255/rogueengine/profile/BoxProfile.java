package spectrum255.rogueengine.profile;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.mojang.serialization.DataResult;

import net.minecraft.resources.Identifier;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.ChunkGenerator;

public record BoxProfile(
		int schemaVersion,
		Identifier dimensionType,
		Generator generator,
		Seed seed,
		Spawn spawn,
		Border border,
		InventoryPolicy inventory) {
	public BoxProfile {
		Objects.requireNonNull(dimensionType, "dimensionType");
		Objects.requireNonNull(generator, "generator");
		Objects.requireNonNull(seed, "seed");
		Objects.requireNonNull(spawn, "spawn");
		Objects.requireNonNull(border, "border");
		Objects.requireNonNull(inventory, "inventory");
	}

	public DataResult<BoxProfile> validate() {
		if (schemaVersion != 1 && schemaVersion != 2) {
			return DataResult.error(() -> "Unsupported profile schema_version: " + schemaVersion);
		}
		if (generator instanceof TemplateGenerator && schemaVersion != 2) {
			return DataResult.error(() -> "Template generators require profile schema_version 2");
		}
		if (!seed.mode().equals("random") && !seed.mode().equals("fixed")) {
			return DataResult.error(() -> "seed.mode must be random or fixed");
		}
		if (seed.mode().equals("fixed") != seed.value().isPresent()) {
			return DataResult.error(() -> "fixed seed requires value and random seed forbids it");
		}
		if (spawn instanceof SearchSpawn search && (search.radius() < 1 || search.radius() > 64)) {
			return DataResult.error(() -> "spawn.radius must be between 1 and 64");
		}
		if (!Double.isFinite(border.centerX()) || !Double.isFinite(border.centerZ())
				|| Math.abs(border.centerX()) > WorldBorder.MAX_CENTER_COORDINATE
				|| Math.abs(border.centerZ()) > WorldBorder.MAX_CENTER_COORDINATE
				|| !Double.isFinite(border.initialSize()) || border.initialSize() < 16.0
				|| border.initialSize() > WorldBorder.MAX_SIZE
				|| !Double.isFinite(border.warningBlocks()) || border.warningBlocks() < 0.0
				|| border.warningBlocks() > border.initialSize() / 2.0) {
			return DataResult.error(() -> "Invalid world border settings");
		}
		if (spawn instanceof FixedSpawn fixed) {
			double halfSize = border.initialSize() / 2.0;
			if (!Double.isFinite(fixed.x()) || !Double.isFinite(fixed.y()) || !Double.isFinite(fixed.z())
					|| Math.abs(fixed.x() - border.centerX()) > halfSize
					|| Math.abs(fixed.z() - border.centerZ()) > halfSize) {
				return DataResult.error(() -> "Fixed spawn must be finite and inside the world border");
			}
		}
		return DataResult.success(this);
	}

	public sealed interface Generator permits StemGenerator, InlineGenerator, TemplateGenerator {
	}

	public record StemGenerator(String kind, Identifier id) implements Generator {
		public StemGenerator {
			if (!"stem".equals(kind)) {
				throw new IllegalArgumentException("generator.kind must be stem");
			}
			Objects.requireNonNull(id, "id");
		}
	}

	public record InlineGenerator(String kind, ChunkGenerator value) implements Generator {
		public InlineGenerator {
			if (!"inline".equals(kind)) {
				throw new IllegalArgumentException("generator.kind must be inline");
			}
			Objects.requireNonNull(value, "value");
		}
	}

	public record TemplateGenerator(String kind, Identifier id, Map<String, Double> overrides, Optional<String> preset)
			implements Generator {
		public TemplateGenerator {
			if (!"template".equals(kind)) {
				throw new IllegalArgumentException("generator.kind must be template");
			}
			Objects.requireNonNull(id, "id");
			Objects.requireNonNull(preset, "preset");
			Objects.requireNonNull(overrides, "overrides");
			overrides = Map.copyOf(overrides);
			for (var entry : overrides.entrySet()) {
				if (entry.getKey().isBlank() || entry.getValue() == null || !Double.isFinite(entry.getValue())) {
					throw new IllegalArgumentException("Template overrides must have non-blank keys and finite values");
				}
			}
		}
	}

	public record Seed(String mode, Optional<Long> value) {
	}

	public sealed interface Spawn permits FixedSpawn, SearchSpawn {
	}

	public record FixedSpawn(String kind, double x, double y, double z, float yaw) implements Spawn {
		public FixedSpawn {
			if (!"fixed".equals(kind)) {
				throw new IllegalArgumentException("spawn.mode must be fixed");
			}
		}
	}

	public record SearchSpawn(String kind, int radius) implements Spawn {
		public SearchSpawn {
			if (!"search".equals(kind)) {
				throw new IllegalArgumentException("spawn.mode must be search");
			}
		}
	}

	public record Border(double centerX, double centerZ, double initialSize, double warningBlocks) {
	}

	public record InventoryPolicy(boolean bringOnEnter, boolean takeOnExit) {
		public static final InventoryPolicy DEFAULT = new InventoryPolicy(true, true);
	}
}
