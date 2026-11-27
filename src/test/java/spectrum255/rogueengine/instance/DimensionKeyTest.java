package spectrum255.rogueengine.instance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

class DimensionKeyTest {
	@Test
	void dimensionKeyUsesStableLowerCaseUuidPath() {
		UUID id = UUID.fromString("ABCDEFAB-1234-5678-90AB-CDEFABCDEFAB");
		ResourceKey<Level> key = BoxInstance.dimensionKey(id);

		assertEquals("rogue-engine-fabric", key.identifier().getNamespace());
		assertEquals("instance/abcdefab1234567890abcdefabcdefab", key.identifier().getPath());
	}

	@Test
	void differentInstancesNeverShareADimensionKey() {
		assertNotEquals(
				BoxInstance.dimensionKey(UUID.randomUUID()),
				BoxInstance.dimensionKey(UUID.randomUUID()));
	}

	@Test
	void nullUuidIsRejected() {
		assertThrows(NullPointerException.class, () -> BoxInstance.dimensionKey(null));
	}

	@Test
	void createdInstanceUsesTheEngineNamespace() {
		BoxInstance instance = BoxInstance.create(UUID.randomUUID(), "Example");
		assertEquals(Identifier.fromNamespaceAndPath("rogue-engine-fabric", "instance/"
				+ instance.id().toString().replace("-", "")), instance.dimensionKey().identifier());
	}
}
