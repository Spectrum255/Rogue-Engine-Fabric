package spectrum255.rogueengine.map;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

class SavedWorldPathsTest {
	@Test
	void resolvesVanillaAndCustomDimensionPaths() {
		Path world = Path.of("world");
		assertEquals(Path.of("world/region"), SavedWorldPaths.dimensionFolder(world,
				ResourceKey.create(Registries.DIMENSION, Identifier.withDefaultNamespace("overworld"))));
		assertEquals(Path.of("world/DIM-1/region"), SavedWorldPaths.dimensionFolder(world,
				ResourceKey.create(Registries.DIMENSION, Identifier.withDefaultNamespace("the_nether"))));
		assertEquals(Path.of("world/DIM1/region"), SavedWorldPaths.dimensionFolder(world,
				ResourceKey.create(Registries.DIMENSION, Identifier.withDefaultNamespace("the_end"))));
		assertEquals(Path.of("world/dimensions/example/dungeon/region"), SavedWorldPaths.dimensionFolder(world,
				ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath("example", "dungeon"))));
	}
}
