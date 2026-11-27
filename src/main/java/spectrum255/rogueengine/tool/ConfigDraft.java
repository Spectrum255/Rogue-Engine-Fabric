package spectrum255.rogueengine.tool;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.mojang.serialization.DataResult;

import net.minecraft.resources.Identifier;

import spectrum255.rogueengine.profile.BoxProfile;
import spectrum255.rogueengine.profile.WorldGenTemplate;

public record ConfigDraft(
		String packName,
		String namespace,
		Identifier templateId,
		Identifier profileId,
		WorldGenTemplate template,
		BoxProfile profile) {
	public ConfigDraft {
		Objects.requireNonNull(packName, "packName");
		Objects.requireNonNull(namespace, "namespace");
		Objects.requireNonNull(templateId, "templateId");
		Objects.requireNonNull(profileId, "profileId");
		Objects.requireNonNull(template, "template");
		Objects.requireNonNull(profile, "profile");
	}

	public DataResult<ConfigDraft> validate() {
		if (!packName.matches("[a-z0-9_-]+") || namespace.isBlank()) {
			return DataResult.error(() -> "Invalid pack name or namespace");
		}
		if (!namespace.equals(templateId.getNamespace()) || !namespace.equals(profileId.getNamespace())
				|| !(profile.generator() instanceof BoxProfile.TemplateGenerator generator)
				|| !generator.id().equals(templateId)) {
			return DataResult.error(() -> "Profile must reference the drafted template in the same namespace");
		}
		return template.validate().flatMap(ignored -> profile.validate().map(ignored2 -> this));
	}

	public static ConfigDraft defaultDraft() {
		Identifier id = Identifier.fromNamespaceAndPath("example", "my_world");
		WorldGenTemplate template = new WorldGenTemplate(1, "noise_chaos",
				Identifier.withDefaultNamespace("overworld"), WorldGenTemplate.neutralRanges(), List.of());
		BoxProfile profile = new BoxProfile(2, Identifier.withDefaultNamespace("overworld"),
				new BoxProfile.TemplateGenerator("template", id, Map.of(), Optional.empty()),
				new BoxProfile.Seed("random", Optional.empty()), new BoxProfile.SearchSpawn("search", 32),
				new BoxProfile.Border(0, 0, 30_000_000, 16), BoxProfile.InventoryPolicy.DEFAULT);
		return new ConfigDraft("my_pack", "example", id, id, template, profile);
	}
}
