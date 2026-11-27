package spectrum255.rogueengine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.resources.Identifier;

class ServerOnlyMetadataTest {
	@Test
	void modIdentityUsesOneNamespace() {
		assertEquals("rogue-engine-fabric", RogueEngineFabric.MOD_ID);
		assertEquals(Identifier.fromNamespaceAndPath("rogue-engine-fabric", "profile"), RogueEngineFabric.id("profile"));
	}

	@Test
	void productionMetadataSupportsOptionalClientTools() throws IOException {
		JsonObject metadata = readMetadata();
		assertEquals("*", metadata.get("environment").getAsString());
		assertTrue(metadata.getAsJsonObject("entrypoints").has("modmenu"));
		assertFalse(metadata.getAsJsonObject("entrypoints").has("client"));
		assertFalse(metadata.getAsJsonObject("entrypoints").has("fabric-datagen"));
	}

	private JsonObject readMetadata() throws IOException {
		try (InputStream input = ServerOnlyMetadataTest.class.getResourceAsStream("/fabric.mod.json")) {
			if (input == null) {
				throw new IOException("fabric.mod.json is missing from the test classpath");
			}
			return JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
		}
	}
}
