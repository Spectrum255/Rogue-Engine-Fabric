package spectrum255.rogueengine.instance;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import net.minecraft.resources.Identifier;

class BoxDefinitionTest {
	@Test
	void rejectsMissingSnapshot() {
		UUID id = UUID.randomUUID();
		assertThrows(IllegalArgumentException.class, () -> new BoxDefinition(
				id, Identifier.withDefaultNamespace("test"), "{}", "", "hash", "{}", 1L));
	}
}
