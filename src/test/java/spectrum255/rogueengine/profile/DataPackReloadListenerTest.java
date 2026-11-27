package spectrum255.rogueengine.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.Profiler;

import spectrum255.rogueengine.RogueEngineFabric;

class DataPackReloadListenerTest {
	@Test
	void missingEngineFileUsesDefaults() throws IOException {
		DataPackReloadListener listener = new DataPackReloadListener();
		EngineSettings prepared = listener.prepare(resourceManager(Map.of()), Profiler.get());
		listener.apply(prepared, resourceManager(Map.of()), Profiler.get());

		assertEquals(EngineSettings.DEFAULT, listener.current());
	}

	@Test
	void validEngineFileReplacesSettings() throws IOException {
		DataPackReloadListener listener = new DataPackReloadListener();
		ResourceManager manager = resourceManager(Map.of(RogueEngineFabric.id("engine.json"), resource("""
				{
				  "schema_version": 1,
				  "max_active_instances": 4,
				  "max_pending_operations": 8,
				  "preload_radius": 2,
				  "preload_chunks_per_tick": 2,
				  "preload_tick_budget_ms": 5,
				  "preload_timeout_seconds": 60,
				  "archive_min_tick_budget_ms": 20,
				  "destroy_confirmation_ttl_seconds": 30,
				  "quarantine_retention_days": 7,
				  "fallback_dimension": "minecraft:overworld"
				}
				""")));

		EngineSettings prepared = listener.prepare(manager, Profiler.get());
		listener.apply(prepared, manager, Profiler.get());

		assertEquals(4, listener.current().maxActiveInstances());
	}

	@Test
	void invalidEngineFileDoesNotReplaceSettings() throws IOException {
		DataPackReloadListener listener = new DataPackReloadListener();
		ResourceManager manager = resourceManager(Map.of(RogueEngineFabric.id("engine.json"), resource("not-json")));

		assertThrows(IllegalStateException.class, () -> listener.prepare(manager, Profiler.get()));
		assertEquals(EngineSettings.DEFAULT, listener.current());
	}

	private Resource resource(String json) {
		return new Resource(null, () -> new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
	}

	private ResourceManager resourceManager(Map<Identifier, Resource> resources) {
		return new ResourceManager() {
			@Override
			public Set<String> getNamespaces() {
				return resources.keySet().stream().map(Identifier::getNamespace).collect(java.util.stream.Collectors.toSet());
			}

			@Override
			public Optional<Resource> getResource(Identifier id) {
				return Optional.ofNullable(resources.get(id));
			}

			@Override
			public java.util.List<Resource> getResourceStack(Identifier id) {
				return resources.containsKey(id) ? List.of(resources.get(id)) : List.of();
			}

			@Override
			public Map<Identifier, List<Resource>> listResourceStacks(String path, Selector selector) {
				return resources.entrySet().stream()
						.filter(entry -> entry.getKey().getPath().equals(path) && selector.isIncluded(entry.getKey()))
						.collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, entry -> List.of(entry.getValue())));
			}

			@Override
			public Map<Identifier, Resource> listResources(String path, Selector selector) {
				return resources.entrySet().stream()
						.filter(entry -> entry.getKey().getPath().equals(path) && selector.isIncluded(entry.getKey()))
						.collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
			}

			@Override
			public java.util.stream.Stream<net.minecraft.server.packs.PackResources> listPacks() {
				return java.util.stream.Stream.empty();
			}
		};
	}
}
