package spectrum255.rogueengine.dimension;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.VillageSiege;
import net.minecraft.world.entity.npc.CatSpawner;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTraderSpawner;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.PatrolSpawner;
import net.minecraft.world.level.levelgen.PhantomSpawner;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.PrimaryLevelData;
import net.minecraft.world.level.storage.WorldData;

import spectrum255.rogueengine.instance.BoxDefinition;
import spectrum255.rogueengine.instance.BoxInstance;
import spectrum255.rogueengine.mixin.MinecraftServerAccessor;
import spectrum255.rogueengine.profile.BoxProfile;

public final class DimensionService {
	private final MinecraftServer server;
	private final Map<ResourceKey<Level>, ServerLevel> levels;

	public DimensionService(MinecraftServer server) {
		this.server = server;
		this.levels = ((MinecraftServerAccessor) server).getLevels();
	}

	public ServerLevel create(BoxDefinition definition) {
		return load(definition);
	}

	public ServerLevel load(BoxDefinition definition) {
		if (!server.isSameThread()) {
			throw new IllegalStateException("Dimension lifecycle operations must run on the server thread");
		}
		ResourceKey<Level> key = BoxInstance.dimensionKey(definition.instanceId());
		if (levels.containsKey(key)) {
			throw new IllegalStateException("Dimension is already loaded: " + key);
		}
		BoxProfile profile = definition.profile(server.registryAccess());
		LevelStem stem = LevelStem.CODEC.parse(
				RegistryOps.create(JsonOps.INSTANCE, server.registryAccess()),
				JsonParser.parseString(definition.levelStemJson())).getOrThrow();
		WorldData worldData = server.getWorldData();
		PrimaryLevelData levelData = new PrimaryLevelData(
				worldData.getLevelSettings(),
				PrimaryLevelData.SpecialWorldProperty.NONE,
				worldData.worldGenSettingsLifecycle());
		levelData.setInitialized(true);
		if (profile.spawn() instanceof BoxProfile.FixedSpawn fixed) {
			BlockPos spawn = new BlockPos((int) Math.floor(fixed.x()), (int) Math.floor(fixed.y()), (int) Math.floor(fixed.z()));
			levelData.setSpawn(new LevelData.RespawnData(new GlobalPos(key, spawn), fixed.yaw(), 0.0F));
		} else {
			levelData.setSpawn(new LevelData.RespawnData(new GlobalPos(key, BlockPos.ZERO), 0.0F, 0.0F));
		}
		List<net.minecraft.world.level.CustomSpawner> spawners = List.of(
				new PhantomSpawner(),
				new PatrolSpawner(),
				new CatSpawner(),
				new VillageSiege(),
				new WanderingTraderSpawner(server.getDataStorage()));
		DimensionSeeds.register(key, definition.seed());
		ServerLevel level;
		try {
			level = new ServerLevel(
					server,
					((MinecraftServerAccessor) server).getExecutor(),
					((MinecraftServerAccessor) server).getStorageSource(),
					levelData,
					key,
					stem,
					false,
					BiomeManager.obfuscateSeed(definition.seed()),
					spawners,
					false);
		} catch (RuntimeException exception) {
			DimensionSeeds.unregister(key);
			throw exception;
		}
		level.getWorldBorder().setCenter(profile.border().centerX(), profile.border().centerZ());
		level.getWorldBorder().setSize(profile.border().initialSize());
		level.getWorldBorder().setWarningBlocks((int) Math.round(profile.border().warningBlocks()));
		levels.put(key, level);
		try {
			ServerLevelEvents.LOAD.invoker().onLevelLoad(server, level);
		} catch (RuntimeException exception) {
			levels.remove(key);
			DimensionSeeds.unregister(key);
			try {
				level.close();
			} catch (IOException closeException) {
				exception.addSuppressed(closeException);
			}
			throw exception;
		}
		return level;
	}

	public void unload(ServerLevel level) throws IOException {
		if (!server.isSameThread()) {
			throw new IllegalStateException("Dimension lifecycle operations must run on the server thread");
		}
		level.save(null, true, false);
		levels.remove(level.dimension());
		ServerLevelEvents.UNLOAD.invoker().onLevelUnload(server, level);
		try {
			level.close();
		} finally {
			DimensionSeeds.unregister(level.dimension());
		}
	}

	public ServerLevel get(ResourceKey<Level> key) {
		return levels.get(key);
	}
}
