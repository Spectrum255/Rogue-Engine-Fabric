package spectrum255.rogueengine.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

class BoxProfileV2CompatibilityTest {
	@BeforeAll
	static void bootstrapMinecraft() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	void schemaV1StemProfileStillParses() {
		BoxProfile profile = parse("""
				{
				  "schema_version": 1,
				  "dimension_type": "minecraft:overworld",
				  "generator": {"kind": "stem", "id": "minecraft:overworld"},
				  "seed": {"mode": "random"},
				  "spawn": {"mode": "search", "radius": 32},
				  "border": {"center_x": 0.0, "center_z": 0.0, "initial_size": 512.0, "warning_blocks": 16.0}
				}
				""").result().orElseThrow();

		assertEquals(1, profile.schemaVersion());
		assertInstanceOf(BoxProfile.StemGenerator.class, profile.generator());
	}

	@Test
	void schemaV2TemplateProfileParses() {
		BoxProfile profile = parse("""
				{
				  "schema_version": 2,
				  "dimension_type": "minecraft:overworld",
				  "generator": {
				    "kind": "template",
				    "id": "rogueengine:noise_chaos",
				    "overrides": {"density_offset": 0.1},
				    "preset": "wild"
				  },
				  "seed": {"mode": "random"},
				  "spawn": {"mode": "search", "radius": 32},
				  "border": {"center_x": 0.0, "center_z": 0.0, "initial_size": 512.0, "warning_blocks": 16.0}
				}
				""").result().orElseThrow();

		assertEquals(2, profile.schemaVersion());
		BoxProfile.TemplateGenerator generator = assertInstanceOf(BoxProfile.TemplateGenerator.class, profile.generator());
		assertEquals("rogueengine:noise_chaos", generator.id().toString());
		assertEquals(0.1, generator.overrides().get("density_offset"));
		assertEquals("wild", generator.preset().orElseThrow());
	}

	@Test
	void schemaV3IsRejected() {
		assertFalse(parse("""
				{
				  "schema_version": 3,
				  "dimension_type": "minecraft:overworld",
				  "generator": {"kind": "stem", "id": "minecraft:overworld"},
				  "seed": {"mode": "random"},
				  "spawn": {"mode": "search", "radius": 32},
				  "border": {"center_x": 0.0, "center_z": 0.0, "initial_size": 512.0, "warning_blocks": 16.0}
				}
				""").result().isPresent());
	}

	private DataResult<BoxProfile> parse(String json) {
		JsonElement element = JsonParser.parseString(json);
		return BoxProfileCodec.CODEC.parse(JsonOps.INSTANCE, element);
	}
}
