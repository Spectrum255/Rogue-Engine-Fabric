package spectrum255.rogueengine.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.Lifecycle;

import net.minecraft.SharedConstants;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;

import spectrum255.rogueengine.RogueEngineFabric;

class ProfileCatalogTest {
	@BeforeAll
	static void bootstrapMinecraft() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	void registryKeyUsesEngineNamespace() {
		assertEquals("rogue-engine-fabric", ProfileCatalog.REGISTRY_KEY.identifier().getNamespace());
		assertEquals("profile", ProfileCatalog.REGISTRY_KEY.identifier().getPath());
	}

	@Test
	void testProfileResourceUsesEngineNamespace() throws IOException {
		String path = "/data/" + RogueEngineFabric.MOD_ID + "/" + RogueEngineFabric.MOD_ID
				+ "/profile/flat_test.json";
		try (InputStream input = ProfileCatalogTest.class.getResourceAsStream(path)) {
			assertNotNull(input, "Missing profile resource: " + path);
		}
	}

	@Test
	void catalogReadsProfilesFromRegistry() {
		Identifier id = Identifier.fromNamespaceAndPath("test", "example");
		BoxProfile profile = BoxProfileCodec.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("""
				{
				  "schema_version": 1,
				  "dimension_type": "minecraft:overworld",
				  "generator": {"kind": "stem", "id": "minecraft:overworld"},
				  "seed": {"mode": "random"},
				  "spawn": {"mode": "search", "radius": 32},
				  "border": {"center_x": 0.0, "center_z": 0.0, "initial_size": 512.0, "warning_blocks": 16.0}
				}
				""")).result().orElseThrow();
		MappedRegistry<BoxProfile> registry = new MappedRegistry<>(ProfileCatalog.REGISTRY_KEY, Lifecycle.stable());
		registry.register(ResourceKey.create(ProfileCatalog.REGISTRY_KEY, id), profile, RegistrationInfo.BUILT_IN);
		ProfileCatalog catalog = new ProfileCatalog(registry.freeze());

		assertTrue(catalog.get(id).isPresent());
		assertEquals(profile, catalog.get(id).orElseThrow());
		assertTrue(catalog.ids().contains(id));
		assertFalse(catalog.get(Identifier.withDefaultNamespace("missing")).isPresent());
	}
}
