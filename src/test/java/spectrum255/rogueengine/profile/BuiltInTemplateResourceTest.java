package spectrum255.rogueengine.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

class BuiltInTemplateResourceTest {
	@BeforeAll
	static void bootstrapMinecraft() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	void builtInTemplatesUseCorrectNamespaceAndParse() throws IOException {
		assertEquals("noise_chaos", readTemplate("noise_chaos").generatorKind());
		assertEquals("full_chaos", readTemplate("full_chaos").generatorKind());
		assertFalse(readTemplate("preset_chaos").presets().isEmpty());
		assertEquals(TerrainBoundary.Shape.CIRCLE, readTemplate("bounded_noise").terrainBoundary().shape());
		assertEquals(TerrainBoundary.OutsideMode.VOID,
				readTemplate("bounded_noise").terrainBoundary().outsideMode());
		assertEquals(CentralIslandSettings.SurfaceMode.VANILLA_SURFACE,
				readTemplate("central_island").island().surfaceMode());
		assertEquals(CentralIslandSettings.SurfaceMode.CUSTOM_LAYERS,
				readTemplate("central_island_layers").island().surfaceMode());
	}

	@Test
	void builtInProfilesReferenceTheirTemplates() throws IOException {
		for (String name : java.util.List.of("noise_chaos", "full_chaos", "preset_chaos", "bounded_noise",
				"central_island", "central_island_layers")) {
			String path = "/data/rogueengine/rogueengine/profile/" + name + ".json";
			try (InputStream input = BuiltInTemplateResourceTest.class.getResourceAsStream(path)) {
				assertNotNull(input, "Missing built-in profile: " + path);
				BoxProfile profile = BoxProfileCodec.CODEC.parse(JsonOps.INSTANCE,
						JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8))).getOrThrow();
				BoxProfile.TemplateGenerator generator = assertInstanceOf(BoxProfile.TemplateGenerator.class, profile.generator());
				assertEquals("rogueengine:" + name, generator.id().toString());
			}
		}
	}

	private WorldGenTemplate readTemplate(String name) throws IOException {
		String path = "/data/rogueengine/rogueengine/worldgen_template/" + name + ".json";
		try (InputStream input = BuiltInTemplateResourceTest.class.getResourceAsStream(path)) {
			assertNotNull(input, "Missing built-in worldgen template: " + path);
			return WorldGenTemplateCodec.CODEC.parse(JsonOps.INSTANCE,
					JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8))).getOrThrow();
		}
	}
}
