package spectrum255.rogueengine.probe;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.ai.village.VillageSiege;
import net.minecraft.world.entity.npc.CatSpawner;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTraderSpawner;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.PatrolSpawner;
import net.minecraft.world.level.levelgen.PhantomSpawner;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.PrimaryLevelData;
import net.minecraft.world.level.storage.WorldData;
import net.minecraft.world.phys.Vec3;
import spectrum255.rogueengine.probe.mixin.MinecraftServerAccessor;

public final class DimensionProbe implements ModInitializer {
	public static final String MOD_ID = "rogue-engine-probe";
	private static final Map<UUID, ProbeInstance> INSTANCES = new LinkedHashMap<>();
	private static final Map<ResourceKey<Level>, Long> SEEDS = new HashMap<>();
	private static final Map<UUID, PendingTeleport> PENDING_TELEPORTS = new HashMap<>();
	private static MinecraftServer server;

	@Override
	public void onInitialize() {
		ServerLifecycleEvents.SERVER_STARTING.register(startingServer -> {
			server = startingServer;
			loadInstances();
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(startingServer -> {
			PENDING_TELEPORTS.values().forEach(PendingTeleport::releaseTicket);
			PENDING_TELEPORTS.clear();
			saveInstances();
		});
		ServerTickEvents.END_SERVER_TICK.register(startingServer -> tickPendingTeleports());
		CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> registerCommands(dispatcher));
	}

	private static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("probe")
				.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_ADMIN))
				.then(Commands.literal("create")
						.executes(context -> create(context.getSource(), null))
						.then(Commands.argument("seed", LongArgumentType.longArg())
								.executes(context -> create(context.getSource(), LongArgumentType.getLong(context, "seed")))))
				.then(Commands.literal("list").executes(DimensionProbe::list))
				.then(Commands.literal("teleport")
						.then(Commands.argument("uuid", net.minecraft.commands.arguments.UuidArgument.uuid())
								.executes(DimensionProbe::teleport)))
				.then(Commands.literal("archive")
						.then(Commands.argument("uuid", net.minecraft.commands.arguments.UuidArgument.uuid())
								.executes(DimensionProbe::archive)))
				.then(Commands.literal("restore")
						.then(Commands.argument("uuid", net.minecraft.commands.arguments.UuidArgument.uuid())
								.executes(DimensionProbe::restore)))
												.then(Commands.literal("biome")
														.then(Commands.argument("uuid", net.minecraft.commands.arguments.UuidArgument.uuid())
															.then(Commands.argument("pos", BlockPosArgument.blockPos())
																.executes(DimensionProbe::biome)))));
	}

	private static int create(CommandSourceStack source, Long requestedSeed) {
		try {
			UUID id = UUID.randomUUID();
			long seed = requestedSeed == null ? UUID.randomUUID().getMostSignificantBits() : requestedSeed;
			ProbeInstance instance = new ProbeInstance(id, seed, false);
			INSTANCES.put(id, instance);
			loadLevel(instance);
			instance.active = true;
			saveInstances();
			source.sendSuccess(() -> Component.literal("Created " + id + " with seed " + seed), true);
			return 1;
		} catch (Exception exception) {
			source.sendFailure(Component.literal("Probe create failed: " + exception.getMessage()));
			return 0;
		}
	}

	private static int list(CommandContext<CommandSourceStack> context) {
		CommandSourceStack source = context.getSource();
		if (INSTANCES.isEmpty()) {
			source.sendSuccess(() -> Component.literal("No probe instances"), false);
		}
		for (ProbeInstance instance : INSTANCES.values()) {
			source.sendSuccess(() -> Component.literal(instance.id + " seed=" + instance.seed + " active=" + instance.active), false);
		}
		return INSTANCES.size();
	}

	private static int teleport(CommandContext<CommandSourceStack> context) {
		CommandSourceStack source = context.getSource();
		try {
			ServerPlayer player = source.getPlayerOrException();
			ProbeInstance instance = getInstance(context);
			ServerLevel level = server.getLevel(dimensionKey(instance.id));
			if (level == null) {
				source.sendFailure(Component.literal("Instance is not active"));
				return 0;
			}
			if (PENDING_TELEPORTS.containsKey(player.getUUID())) {
				source.sendFailure(Component.literal("A teleport is already being prepared"));
				return 0;
			}
			PendingTeleport pending = new PendingTeleport(player, level);
			pending.level.getChunkSource().addTicketWithRadius(TicketType.PLAYER_LOADING, pending.center, 1);
			PENDING_TELEPORTS.put(player.getUUID(), pending);
			source.sendSuccess(() -> Component.literal("Preparing spawn chunks for " + instance.id), true);
			return 1;
		} catch (CommandSyntaxException | IllegalArgumentException exception) {
			source.sendFailure(Component.literal(exception.getMessage()));
			return 0;
		}
	}

	private static void tickPendingTeleports() {
		for (PendingTeleport pending : List.copyOf(PENDING_TELEPORTS.values())) {
			try {
				if (pending.player.level().getServer() != server || pending.player.isRemoved()) {
					pending.releaseTicket();
					PENDING_TELEPORTS.remove(pending.player.getUUID());
					continue;
				}
				if (server.getLevel(pending.level.dimension()) != pending.level) {
					throw new IllegalStateException("Target dimension is no longer active");
				}
				int chunkX = pending.nextChunk % 3 - 1;
				int chunkZ = pending.nextChunk / 3 - 1;
				if (pending.currentFuture == null) {
					pending.currentFuture = pending.level.getChunkSource().getChunkFuture(chunkX, chunkZ, ChunkStatus.FULL, false);
				}
				if (pending.currentFuture.isDone()) {
					ChunkResult<ChunkAccess> result = pending.currentFuture.join();
					if (!result.isSuccess()) {
						throw new IllegalStateException("Chunk loading failed: " + result.getError());
					}
					pending.currentFuture = null;
					pending.nextChunk++;
				}
				pending.ticks++;
				if (pending.nextChunk >= 9) {
					double spawnY = pending.level.getChunkSource().getGenerator().getSpawnHeight(pending.level);
					pending.player.teleport(new TeleportTransition(
							pending.level,
							new Vec3(0.5, spawnY, 0.5),
							Vec3.ZERO,
							0.0F,
							0.0F,
							TeleportTransition.DO_NOTHING));
					pending.releaseTicket();
					PENDING_TELEPORTS.remove(pending.player.getUUID());
					continue;
				}
				if (pending.ticks >= 200) {
					throw new IllegalStateException("Spawn chunk preparation timed out");
				}
			} catch (Exception exception) {
				pending.releaseTicket();
				PENDING_TELEPORTS.remove(pending.player.getUUID());
				pending.player.sendSystemMessage(Component.literal("Teleport preparation failed: " + exception.getMessage()));
			}
		}
	}

	private static int archive(CommandContext<CommandSourceStack> context) {
		CommandSourceStack source = context.getSource();
		try {
			ProbeInstance instance = getInstance(context);
			ServerLevel level = server.getLevel(dimensionKey(instance.id));
			if (level == null || !instance.active) {
				source.sendFailure(Component.literal("Instance is not active"));
				return 0;
			}
			if (!level.players().isEmpty()) {
				source.sendFailure(Component.literal("Instance still has players"));
				return 0;
			}
			if (PENDING_TELEPORTS.values().stream().anyMatch(pending -> pending.level == level)) {
				source.sendFailure(Component.literal("A teleport is still being prepared"));
				return 0;
			}
			level.save(null, true, false);
			levels().remove(level.dimension());
			ServerLevelEvents.UNLOAD.invoker().onLevelUnload(server, level);
			level.close();
			SEEDS.remove(level.dimension());
			instance.active = false;
			saveInstances();
			source.sendSuccess(() -> Component.literal("Archived " + instance.id), true);
			return 1;
		} catch (Exception exception) {
			source.sendFailure(Component.literal("Probe archive failed: " + exception.getMessage()));
			return 0;
		}
	}

	private static int restore(CommandContext<CommandSourceStack> context) {
		CommandSourceStack source = context.getSource();
		try {
			ProbeInstance instance = getInstance(context);
			if (instance.active) {
				source.sendFailure(Component.literal("Instance is already active"));
				return 0;
			}
			loadLevel(instance);
			instance.active = true;
			saveInstances();
			source.sendSuccess(() -> Component.literal("Restored " + instance.id), true);
			return 1;
		} catch (Exception exception) {
			source.sendFailure(Component.literal("Probe restore failed: " + exception.getMessage()));
			return 0;
		}
	}

	private static int biome(CommandContext<CommandSourceStack> context) {
		CommandSourceStack source = context.getSource();
		try {
			ProbeInstance instance = getInstance(context);
			ServerLevel level = server.getLevel(dimensionKey(instance.id));
			if (level == null) {
				source.sendFailure(Component.literal("Instance is not active"));
				return 0;
			}
			BlockPos position = BlockPosArgument.getBlockPos(context, "pos");
			String biome = level.getNoiseBiome(position.getX(), position.getY(), position.getZ())
					.unwrapKey().orElseThrow().identifier().toString();
			source.sendSuccess(() -> Component.literal(instance.id + " @ " + position.toShortString() + " biome=" + biome), false);
			return 1;
		} catch (IllegalArgumentException exception) {
			source.sendFailure(Component.literal(exception.getMessage()));
			return 0;
		}
	}

	public static void restoreActiveDimensions(MinecraftServer startingServer) {
		if (server == null) {
			server = startingServer;
			loadInstances();
		}
		for (ProbeInstance instance : INSTANCES.values()) {
			if (instance.active) {
				loadLevel(instance);
			}
		}
	}

	@SuppressWarnings("deprecation")
	private static ServerLevel loadLevel(ProbeInstance instance) {
		ResourceKey<Level> key = dimensionKey(instance.id);
		var registries = server.registryAccess();
		Holder<DimensionType> dimensionType = registries.lookupOrThrow(net.minecraft.core.registries.Registries.DIMENSION_TYPE)
				.getOrThrow(ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION_TYPE, Identifier.withDefaultNamespace("overworld")));
		FlatLevelGeneratorSettings settings = FlatLevelGeneratorSettings.getDefault(
				registries.lookupOrThrow(net.minecraft.core.registries.Registries.BIOME),
				registries.lookupOrThrow(net.minecraft.core.registries.Registries.STRUCTURE_SET),
				registries.lookupOrThrow(net.minecraft.core.registries.Registries.PLACED_FEATURE));
		LevelStem stem = new LevelStem(dimensionType, new FlatLevelSource(settings));
		WorldData worldData = server.getWorldData();
		PrimaryLevelData levelData = new PrimaryLevelData(
				worldData.getLevelSettings(),
				PrimaryLevelData.SpecialWorldProperty.NONE,
				worldData.worldGenSettingsLifecycle());
		levelData.setInitialized(true);
		List<net.minecraft.world.level.CustomSpawner> spawners = List.of(
				new PhantomSpawner(),
				new PatrolSpawner(),
				new CatSpawner(),
				new VillageSiege(),
				new WanderingTraderSpawner(server.getDataStorage()));
		SEEDS.put(key, instance.seed);
		ServerLevel level;
		try {
			level = new ServerLevel(
					server,
					((MinecraftServerAccessor) server).getExecutor(),
					storage(),
					levelData,
					key,
					stem,
					false,
					BiomeManager.obfuscateSeed(instance.seed),
					spawners,
					false);
		} catch (RuntimeException exception) {
			SEEDS.remove(key);
			throw exception;
		}
		int spawnY = level.getChunkSource().getGenerator().getSpawnHeight(level);
		level.getWorldBorder().setCenter(0.0, 0.0);
		level.getWorldBorder().setSize(512.0);
		level.setRespawnData(new LevelData.RespawnData(new GlobalPos(key, new BlockPos(0, spawnY, 0)), 0.0F, 0.0F));
		levels().put(key, level);
		ServerLevelEvents.LOAD.invoker().onLevelLoad(server, level);
		return level;
	}

	private static ProbeInstance getInstance(CommandContext<CommandSourceStack> context) {
		UUID id = net.minecraft.commands.arguments.UuidArgument.getUuid(context, "uuid");
		ProbeInstance instance = INSTANCES.get(id);
		if (instance == null) {
			throw new IllegalArgumentException("Unknown probe instance " + id);
		}
		return instance;
	}

	private static Map<ResourceKey<Level>, ServerLevel> levels() {
		return ((MinecraftServerAccessor) server).getLevels();
	}

	private static LevelStorageSource.LevelStorageAccess storage() {
		return ((MinecraftServerAccessor) server).getStorageSource();
	}

	public static Long seedFor(ResourceKey<Level> key) {
		return SEEDS.get(key);
	}

	private static ResourceKey<Level> dimensionKey(UUID id) {
		return ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
				Identifier.fromNamespaceAndPath(MOD_ID, "probe/" + id.toString().replace("-", "")));
	}

	private static Path instanceFile() {
		return storage().getLevelPath(LevelResource.ROOT).resolve("data").resolve(MOD_ID).resolve("instances.dat");
	}

	private static void loadInstances() {
		INSTANCES.clear();
		Path file = instanceFile();
		if (!Files.exists(file)) {
			return;
		}
		try {
			CompoundTag root = NbtIo.readCompressed(file, NbtAccounter.uncompressedQuota());
			ListTag list = root.getListOrEmpty("instances");
			for (int index = 0; index < list.size(); index++) {
				CompoundTag tag = list.getCompoundOrEmpty(index);
				INSTANCES.put(UUID.fromString(tag.getStringOr("id", "")), new ProbeInstance(
						UUID.fromString(tag.getStringOr("id", "")),
						tag.getLongOr("seed", 0L),
						tag.getBooleanOr("active", false)));
			}
		} catch (IOException exception) {
			throw new UncheckedIOException("Failed to read probe instances", exception);
		}
	}

	private static void saveInstances() {
		CompoundTag root = new CompoundTag();
		root.putInt("version", 1);
		ListTag list = new ListTag();
		for (ProbeInstance instance : INSTANCES.values()) {
			CompoundTag tag = new CompoundTag();
			tag.putString("id", instance.id.toString());
			tag.putLong("seed", instance.seed);
			tag.putBoolean("active", instance.active);
			list.addAndUnwrap(tag);
		}
		root.put("instances", list);
		try {
			Path file = instanceFile();
			Files.createDirectories(file.getParent());
			NbtIo.writeCompressed(root, file);
		} catch (IOException exception) {
			throw new UncheckedIOException("Failed to write probe instances", exception);
		}
	}

	private static final class PendingTeleport {
		private final ServerPlayer player;
		private final ServerLevel level;
		private final ChunkPos center = new ChunkPos(0, 0);
		private CompletableFuture<ChunkResult<ChunkAccess>> currentFuture;
		private int nextChunk;
		private int ticks;

		private PendingTeleport(ServerPlayer player, ServerLevel level) {
			this.player = player;
			this.level = level;
		}

		private void releaseTicket() {
			level.getChunkSource().removeTicketWithRadius(TicketType.PLAYER_LOADING, center, 1);
		}
	}

	private static final class ProbeInstance {
		private final UUID id;
		private final long seed;
		private boolean active;

		private ProbeInstance(UUID id, long seed, boolean active) {
			this.id = id;
			this.seed = seed;
			this.active = active;
		}
	}
}
