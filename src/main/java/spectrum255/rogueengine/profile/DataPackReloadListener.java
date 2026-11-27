package spectrum255.rogueengine.profile;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.mojang.serialization.JsonOps;

import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import spectrum255.rogueengine.RogueEngineFabric;

public final class DataPackReloadListener extends SimplePreparableReloadListener<EngineSettings> {
	private static final Identifier SETTINGS_ID = RogueEngineFabric.id("engine.json");
	private final AtomicReference<EngineSettings> current = new AtomicReference<>(EngineSettings.DEFAULT);

	public static DataPackReloadListener register() {
		DataPackReloadListener listener = new DataPackReloadListener();
		ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(RogueEngineFabric.id("engine_settings"), listener);
		return listener;
	}

	@Override
	protected EngineSettings prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
		if (resourceManager.getResource(SETTINGS_ID).isEmpty()) {
			return EngineSettings.DEFAULT;
		}
		try {
			String json = resourceManager.getResourceOrThrow(SETTINGS_ID).readAllAsString();
			JsonElement element = JsonParser.parseString(json);
			return EngineSettings.CODEC.parse(JsonOps.INSTANCE, element).getOrThrow();
		} catch (Exception exception) {
			throw new IllegalStateException("Invalid Rogue Engine settings: " + SETTINGS_ID, exception);
		}
	}

	@Override
	protected void apply(EngineSettings prepared, ResourceManager resourceManager, ProfilerFiller profiler) {
		current.set(prepared);
	}

	public EngineSettings current() {
		return current.get();
	}

	@Override
	public String getName() {
		return "Rogue Engine settings";
	}
}
