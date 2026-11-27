package spectrum255.rogueengine.profile;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

import net.fabricmc.fabric.api.event.registry.DynamicRegistries;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

import spectrum255.rogueengine.RogueEngineFabric;
import spectrum255.rogueengine.tool.DatapackAuthoringService;

public final class WorldGenTemplateCatalog {
	public static final ResourceKey<Registry<WorldGenTemplate>> REGISTRY_KEY =
			ResourceKey.createRegistryKey(RogueEngineFabric.id("worldgen_template"));

	private final HolderLookup.RegistryLookup<WorldGenTemplate> registry;
	private final DatapackAuthoringService local;

	public WorldGenTemplateCatalog(HolderLookup.RegistryLookup<WorldGenTemplate> registry) {
		this(registry, null);
	}

	WorldGenTemplateCatalog(HolderLookup.RegistryLookup<WorldGenTemplate> registry,
			DatapackAuthoringService local) {
		this.registry = registry;
		this.local = local;
	}

	public static void register() {
		DynamicRegistries.registerReloadable(REGISTRY_KEY, WorldGenTemplateCodec.CODEC);
	}

	public static WorldGenTemplateCatalog from(HolderLookup.Provider registries) {
		return new WorldGenTemplateCatalog(registries.lookupOrThrow(REGISTRY_KEY),
				new DatapackAuthoringService(DatapackAuthoringService.configRoot()));
	}

	public Optional<WorldGenTemplate> get(Identifier id) {
		Optional<WorldGenTemplate> registered = registry.get(ResourceKey.create(REGISTRY_KEY, id)).map(Holder::value);
		if (registered.isPresent() || local == null) {
			return registered;
		}
		return Optional.ofNullable(local.templates().get(id));
	}

	public Set<Identifier> ids() {
		Set<Identifier> ids = new LinkedHashSet<>();
		registry.listElementIds().forEach(key -> ids.add(key.identifier()));
		if (local != null) {
			ids.addAll(local.templates().keySet());
		}
		return Set.copyOf(ids);
	}
}
