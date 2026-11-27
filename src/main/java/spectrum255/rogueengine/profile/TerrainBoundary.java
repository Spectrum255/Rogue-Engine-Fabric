package spectrum255.rogueengine.profile;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.ChunkPos;

public record TerrainBoundary(
		double centerX,
		double centerZ,
		double radiusX,
		double radiusZ,
		Shape shape,
		OutsideMode outsideMode) {
	public static final Codec<TerrainBoundary> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.DOUBLE.fieldOf("center_x").forGetter(TerrainBoundary::centerX),
			Codec.DOUBLE.fieldOf("center_z").forGetter(TerrainBoundary::centerZ),
			Codec.DOUBLE.fieldOf("radius_x").forGetter(TerrainBoundary::radiusX),
			Codec.DOUBLE.fieldOf("radius_z").forGetter(TerrainBoundary::radiusZ),
			Shape.CODEC.fieldOf("shape").forGetter(TerrainBoundary::shape),
			OutsideMode.CODEC.fieldOf("outside_mode").forGetter(TerrainBoundary::outsideMode))
			.apply(instance, TerrainBoundary::new));

	public TerrainBoundary {
		if (shape == null || outsideMode == null) {
			throw new IllegalArgumentException("Terrain boundary shape and outside mode are required");
		}
	}

	public boolean contains(double x, double z) {
		if (shape == Shape.CIRCLE) {
			double dx = (x - centerX) / radiusX;
			double dz = (z - centerZ) / radiusZ;
			return dx * dx + dz * dz <= 1.0D;
		}
		return Math.abs(x - centerX) <= radiusX && Math.abs(z - centerZ) <= radiusZ;
	}

	public boolean intersects(ChunkPos chunk) {
		int minX = chunk.getMinBlockX();
		int maxX = chunk.getMaxBlockX();
		int minZ = chunk.getMinBlockZ();
		int maxZ = chunk.getMaxBlockZ();
		if (maxX < centerX - radiusX || minX > centerX + radiusX
				|| maxZ < centerZ - radiusZ || minZ > centerZ + radiusZ) {
			return false;
		}
		if (shape == Shape.ELLIPSE) {
			return true;
		}
		double closestX = Math.max(minX, Math.min(maxX, centerX));
		double closestZ = Math.max(minZ, Math.min(maxZ, centerZ));
		double dx = (closestX - centerX) / radiusX;
		double dz = (closestZ - centerZ) / radiusZ;
		return dx * dx + dz * dz <= 1.0D;
	}

	public DataResult<TerrainBoundary> validate() {
		if (!Double.isFinite(centerX) || !Double.isFinite(centerZ)
				|| !Double.isFinite(radiusX) || !Double.isFinite(radiusZ)
				|| radiusX < 16.0D || radiusX > 1_000_000.0D
				|| radiusZ < 16.0D || radiusZ > 1_000_000.0D
				|| shape == Shape.CIRCLE && radiusX != radiusZ) {
			return DataResult.error(() -> "Invalid terrain boundary");
		}
		return DataResult.success(this);
	}

	public DataResult<TerrainBoundary> validateWithin(BoxProfile.Border hardBorder) {
		DataResult<TerrainBoundary> valid = validate();
		if (valid.result().isEmpty()) {
			return valid;
		}
		if (hardBorder == null || !Double.isFinite(hardBorder.initialSize())) {
			return DataResult.error(() -> "Invalid hard world border");
		}
		double half = hardBorder.initialSize() / 2.0D;
		if (Math.abs(centerX - hardBorder.centerX()) + radiusX > half
				|| Math.abs(centerZ - hardBorder.centerZ()) + radiusZ > half) {
			return DataResult.error(() -> "Terrain boundary exceeds the hard world border");
		}
		return DataResult.success(this);
	}

	public enum Shape implements StringRepresentable {
		CIRCLE("circle"),
		ELLIPSE("ellipse");

		public static final Codec<Shape> CODEC = StringRepresentable.fromEnum(Shape::values);
		private final String id;

		Shape(String id) {
			this.id = id;
		}

		@Override
		public String getSerializedName() {
			return id;
		}
	}

	public enum OutsideMode implements StringRepresentable {
		VOID("void"),
		BEDROCK_FLOOR("bedrock_floor");

		public static final Codec<OutsideMode> CODEC = StringRepresentable.fromEnum(OutsideMode::values);
		private final String id;

		OutsideMode(String id) {
			this.id = id;
		}

		@Override
		public String getSerializedName() {
			return id;
		}
	}
}
