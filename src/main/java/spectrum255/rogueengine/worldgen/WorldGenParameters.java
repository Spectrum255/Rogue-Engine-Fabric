package spectrum255.rogueengine.worldgen;

import java.util.Objects;
import java.util.Optional;

import net.minecraft.resources.Identifier;

import spectrum255.rogueengine.profile.WorldGenTemplate.NoiseParameters;

public record WorldGenParameters(
		Identifier templateId,
		String generatorKind,
		String templateHash,
		NoiseParameters noise,
		Optional<String> presetName) {
	public WorldGenParameters {
		Objects.requireNonNull(templateId, "templateId");
		Objects.requireNonNull(generatorKind, "generatorKind");
		Objects.requireNonNull(templateHash, "templateHash");
		Objects.requireNonNull(noise, "noise");
		Objects.requireNonNull(presetName, "presetName");
	}
}
