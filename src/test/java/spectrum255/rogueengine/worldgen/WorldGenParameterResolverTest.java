package spectrum255.rogueengine.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import net.minecraft.resources.Identifier;

import spectrum255.rogueengine.profile.BoxProfile;
import spectrum255.rogueengine.profile.WorldGenTemplate;
import spectrum255.rogueengine.profile.WorldGenTemplate.NoiseParameters;
import spectrum255.rogueengine.profile.WorldGenTemplate.NoiseRanges;

class WorldGenParameterResolverTest {
	@Test
	void sameInputsProduceSameParameters() {
		WorldGenTemplate template = template("noise_chaos", List.of());
		BoxProfile.TemplateGenerator generator = generator(Optional.empty(), Map.of());

		assertEquals(
				WorldGenParameterResolver.resolve(template, "template-hash", generator, 42L),
				WorldGenParameterResolver.resolve(template, "template-hash", generator, 42L));
	}

	@Test
	void differentSeedsProduceDifferentParameters() {
		WorldGenTemplate template = template("noise_chaos", List.of());
		BoxProfile.TemplateGenerator generator = generator(Optional.empty(), Map.of());

		assertNotEquals(
				WorldGenParameterResolver.resolve(template, "template-hash", generator, 1L),
				WorldGenParameterResolver.resolve(template, "template-hash", generator, 2L));
	}

	@Test
	void overridesReplaceRandomValues() {
		WorldGenTemplate template = template("noise_chaos", List.of());
		BoxProfile.TemplateGenerator generator = generator(Optional.empty(), Map.of("density_offset", 0.05));

		WorldGenParameters parameters = WorldGenParameterResolver.resolve(template, "template-hash", generator, 42L);

		assertEquals(0.05, parameters.noise().densityOffset());
	}

	@Test
	void rejectsOutOfRangeOverride() {
		WorldGenTemplate template = template("noise_chaos", List.of());
		BoxProfile.TemplateGenerator generator = generator(Optional.empty(), Map.of("density_offset", 5.0));

		assertThrows(IllegalArgumentException.class,
				() -> WorldGenParameterResolver.resolve(template, "template-hash", generator, 42L));
	}

	@Test
	void rejectsUnknownOverride() {
		WorldGenTemplate template = template("noise_chaos", List.of());
		BoxProfile.TemplateGenerator generator = generator(Optional.empty(), Map.of("unknown", 1.0));

		assertThrows(IllegalArgumentException.class,
				() -> WorldGenParameterResolver.resolve(template, "template-hash", generator, 42L));
	}

	@Test
	void namedPresetIsSelected() {
		WorldGenTemplate.Preset wild = preset("wild", 0.1);
		WorldGenTemplate.Preset calm = preset("calm", -0.1);
		WorldGenTemplate template = template("preset_chaos", List.of(wild, calm));
		BoxProfile.TemplateGenerator generator = generator(Optional.of("wild"), Map.of());

		WorldGenParameters parameters = WorldGenParameterResolver.resolve(template, "template-hash", generator, 42L);

		assertEquals("wild", parameters.presetName().orElseThrow());
		assertEquals(wild.parameters(), parameters.noise());
	}

	@Test
	void unknownPresetIsRejected() {
		WorldGenTemplate template = template("preset_chaos", List.of(preset("wild", 0.1)));
		BoxProfile.TemplateGenerator generator = generator(Optional.of("missing"), Map.of());

		assertThrows(IllegalArgumentException.class,
				() -> WorldGenParameterResolver.resolve(template, "template-hash", generator, 42L));
	}

	private WorldGenTemplate template(String kind, List<WorldGenTemplate.Preset> presets) {
		NoiseRanges ranges = new NoiseRanges(
				new WorldGenTemplate.DoubleRange(-0.2, 0.2),
				new WorldGenTemplate.DoubleRange(0.8, 1.2),
				new WorldGenTemplate.DoubleRange(0.8, 1.2),
				new WorldGenTemplate.DoubleRange(0.8, 1.2),
				new WorldGenTemplate.DoubleRange(0.8, 1.2),
				new WorldGenTemplate.DoubleRange(-0.1, 0.1),
				new WorldGenTemplate.DoubleRange(-0.1, 0.1),
				new WorldGenTemplate.IntRange(-8, 8));
		return new WorldGenTemplate(1, kind, Identifier.withDefaultNamespace("overworld"), ranges, presets);
	}

	private WorldGenTemplate.Preset preset(String name, double densityOffset) {
		return new WorldGenTemplate.Preset(name, new NoiseParameters(
				densityOffset, 1.0, 1.0, 1.0, 1.0, 0.0, 0.0, 0));
	}

	private BoxProfile.TemplateGenerator generator(Optional<String> preset, Map<String, Double> overrides) {
		return new BoxProfile.TemplateGenerator("template", Identifier.withDefaultNamespace("test"), overrides, preset);
	}
}
