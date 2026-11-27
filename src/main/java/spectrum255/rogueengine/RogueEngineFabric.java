package spectrum255.rogueengine;

import net.fabricmc.api.ModInitializer;

import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import spectrum255.rogueengine.command.RogueEngineCommands;
import spectrum255.rogueengine.lifecycle.ServerLifecycleHooks;
import spectrum255.rogueengine.map.MapPoolCatalog;
import spectrum255.rogueengine.map.MapSceneCatalog;
import spectrum255.rogueengine.profile.DataPackReloadListener;
import spectrum255.rogueengine.profile.ProfileCatalog;
import spectrum255.rogueengine.profile.WorldGenTemplateCatalog;

public class RogueEngineFabric implements ModInitializer {
	public static final String MOD_ID = "rogue-engine-fabric";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ProfileCatalog.register();
		WorldGenTemplateCatalog.register();
		MapSceneCatalog.register();
		MapPoolCatalog.register();
		DataPackReloadListener settings = DataPackReloadListener.register();
		ServerLifecycleHooks.register(settings);
		RogueEngineCommands.register();
		LOGGER.info("Rogue Engine server initializing");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
