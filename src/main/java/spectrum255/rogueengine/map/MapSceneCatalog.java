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
import net.minecraft.tags.TagKey;

import spectrum255.rogueengine.RogueEngineFabric;

public final class MapSceneCatalog {
	public static final ResourceKey<Registry<MapScene>> REGISTRY_KEY =
			ResourceKey.createRegistryKey(RogueEngineFabric.id("map_scene"));

	private final HolderLookup.RegistryLookup<MapScene> registry;

	public MapSceneCatalog(HolderLookup.RegistryLookup<MapScene> registry) {
		this.registry = registry;
	}

	public static void register() {
		DynamicRegistries.registerReloadable(REGISTRY_KEY, MapSceneCodec.CODEC);
	}

	public static MapSceneCatalog from(HolderLookup.Provider registries) {
		return new MapSceneCatalog(registries.lookupOrThrow(REGISTRY_KEY));
	}

	public Optional<MapScene> get(Identifier id) {
		return registry.get(ResourceKey.create(REGISTRY_KEY, id)).map(Holder::value);
	}

	public Set<Identifier> ids() {
		Set<Identifier> ids = new LinkedHashSet<>();
		registry.listElementIds().forEach(key -> ids.add(key.identifier()));
		return Set.copyOf(ids);
	}

	public Set<Identifier> tagged(Identifier tagId) {
		return registry.get(TagKey.create(REGISTRY_KEY, tagId))
				.map(tag -> tag.stream().map(holder -> holder.unwrapKey().orElseThrow().identifier())
						.collect(java.util.stream.Collectors.toUnmodifiableSet()))
				.orElseGet(Set::of);
	}
}
