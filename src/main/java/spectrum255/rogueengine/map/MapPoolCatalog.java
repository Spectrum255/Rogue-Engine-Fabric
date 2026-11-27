package spectrum255.rogueengine.map;

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

public final class MapPoolCatalog {
	public static final ResourceKey<Registry<MapPool>> REGISTRY_KEY =
			ResourceKey.createRegistryKey(RogueEngineFabric.id("map_pool"));

	private final HolderLookup.RegistryLookup<MapPool> registry;

	public MapPoolCatalog(HolderLookup.RegistryLookup<MapPool> registry) {
		this.registry = registry;
	}

	public static void register() {
		DynamicRegistries.registerReloadable(REGISTRY_KEY, MapPoolCodec.CODEC);
	}

	public static MapPoolCatalog from(HolderLookup.Provider registries) {
		return new MapPoolCatalog(registries.lookupOrThrow(REGISTRY_KEY));
	}

	public Optional<MapPool> get(Identifier id) {
		return registry.get(ResourceKey.create(REGISTRY_KEY, id)).map(Holder::value);
	}

	public Set<Identifier> ids() {
		Set<Identifier> ids = new LinkedHashSet<>();
		registry.listElementIds().forEach(key -> ids.add(key.identifier()));
		return Set.copyOf(ids);
	}
}
