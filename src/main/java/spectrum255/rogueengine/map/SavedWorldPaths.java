package spectrum255.rogueengine.map;

import java.nio.file.Path;
import java.util.Objects;

import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public final class SavedWorldPaths {
	private SavedWorldPaths() {
	}

	public static Path dimensionFolder(Path worldRoot, ResourceKey<Level> dimension) {
		Objects.requireNonNull(worldRoot, "worldRoot");
		Objects.requireNonNull(dimension, "dimension");
		Identifier id = dimension.identifier();
		Path root = worldRoot.normalize();
		if (id.equals(Identifier.withDefaultNamespace("overworld"))) {
			return root.resolve("region");
		}
		if (id.equals(Identifier.withDefaultNamespace("the_nether"))) {
			return root.resolve("DIM-1").resolve("region");
		}
		if (id.equals(Identifier.withDefaultNamespace("the_end"))) {
			return root.resolve("DIM1").resolve("region");
		}
		return root.resolve("dimensions").resolve(id.getNamespace()).resolve(id.getPath()).resolve("region");
	}
}
