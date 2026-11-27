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

public final class ProfileCatalog {
	public static final ResourceKey<Registry<BoxProfile>> REGISTRY_KEY =
			ResourceKey.createRegistryKey(RogueEngineFabric.id("profile"));

	private final HolderLookup.RegistryLookup<BoxProfile> registry;
	private final DatapackAuthoringService local;

	public ProfileCatalog(HolderLookup.RegistryLookup<BoxProfile> registry) {
		this(registry, null);
	}

	ProfileCatalog(HolderLookup.RegistryLookup<BoxProfile> registry, DatapackAuthoringService local) {
		this.registry = registry;
		this.local = local;
	}

	public static void register() {
		DynamicRegistries.registerReloadable(REGISTRY_KEY, BoxProfileCodec.CODEC);
	}

	public static ProfileCatalog from(HolderLookup.Provider registries) {
		return new ProfileCatalog(registries.lookupOrThrow(REGISTRY_KEY),
				new DatapackAuthoringService(DatapackAuthoringService.configRoot()));
	}

	public Optional<BoxProfile> get(Identifier id) {
		Optional<BoxProfile> registered = registry.get(ResourceKey.create(REGISTRY_KEY, id)).map(Holder::value);
		if (registered.isPresent() || local == null) {
			return registered;
		}
		return Optional.ofNullable(local.profiles().get(id));
	}

	public Set<Identifier> ids() {
		Set<Identifier> ids = new LinkedHashSet<>();
		registry.listElementIds().forEach(key -> ids.add(key.identifier()));
		if (local != null) {
			ids.addAll(local.profiles().keySet());
		}
		return Set.copyOf(ids);
	}
}
