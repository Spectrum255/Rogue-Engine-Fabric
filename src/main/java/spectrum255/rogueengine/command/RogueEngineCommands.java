package spectrum255.rogueengine.command;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;

import spectrum255.rogueengine.RogueEngineFabric;
import spectrum255.rogueengine.dimension.PlayerTransferService;
import spectrum255.rogueengine.instance.BoxCreationService;
import spectrum255.rogueengine.instance.BoxDestroyService;
import spectrum255.rogueengine.instance.BoxInstance;
import spectrum255.rogueengine.instance.BoxLifecycleService;
import spectrum255.rogueengine.lifecycle.ServerLifecycleHooks;
import spectrum255.rogueengine.map.MapScene;
import spectrum255.rogueengine.map.SceneResetService;
import spectrum255.rogueengine.map.SelectionRequest;
import spectrum255.rogueengine.map.SaveImportService;
import spectrum255.rogueengine.map.SnapshotCaptureService;
import spectrum255.rogueengine.mixin.MinecraftServerAccessor;
import spectrum255.rogueengine.profile.ProfileCatalog;
import spectrum255.rogueengine.runtime.ServerRuntime;
import spectrum255.rogueengine.tool.DatapackAuthoringService;

public final class RogueEngineCommands {
	public static final String ROOT = "rogueengine";

	private RogueEngineCommands() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> register(dispatcher));
	}

	private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		registerAdmin(dispatcher);
		dispatcher.register(Commands.literal(ROOT)
				.then(Commands.literal("profile")
						.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_MODERATOR))
						.then(Commands.literal("list").executes(RogueEngineCommands::listProfiles)))
				.then(Commands.literal("map")
						.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_ADMIN))
						.then(Commands.literal("snapshot")
								.then(Commands.literal("capture")
										.then(Commands.argument("id", IdentifierArgument.id())
											.then(Commands.argument("from", BlockPosArgument.blockPos())
												.then(Commands.argument("to", BlockPosArgument.blockPos())
													.executes(RogueEngineCommands::captureSnapshot)))))))
				.then(Commands.literal("instance")
						.then(Commands.literal("create")
								.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_ADMIN))
								.then(Commands.argument("profile", IdentifierArgument.id())
										.suggests((context, builder) -> {
											ProfileCatalog.from(context.getSource().getServer().reloadableRegistries().lookup()).ids()
													.forEach(id -> builder.suggest(id.toString()));
											return builder.buildFuture();
										})
										.executes(context -> create(context, "New Box"))
										.then(Commands.literal("create-map")
												.then(Commands.argument("scenes", StringArgumentType.greedyString())
														.executes(context -> createWithMaps(context,
															StringArgumentType.getString(context, "scenes")))))
										.then(Commands.argument("name", StringArgumentType.greedyString())
													.executes(context -> create(context, StringArgumentType.getString(context, "name"))))))
						.then(Commands.literal("list")
								.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_MODERATOR))
								.executes(RogueEngineCommands::listInstances))
						.then(Commands.literal("info")
								.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_MODERATOR))
								.then(Commands.argument("uuid", UuidArgument.uuid())
										.suggests((context, builder) -> {
											runtime(context).boxes().all().forEach(instance -> builder.suggest(instance.id().toString()));
											return builder.buildFuture();
										})
										.executes(RogueEngineCommands::info)))
						.then(Commands.literal("teleport")
								.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_ADMIN))
								.then(Commands.argument("uuid", UuidArgument.uuid())
										.suggests((context, builder) -> {
											runtime(context).boxes().all().forEach(instance -> builder.suggest(instance.id().toString()));
											return builder.buildFuture();
										})
										.executes(RogueEngineCommands::teleport)))
						.then(Commands.literal("evacuate")
								.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_ADMIN))
								.then(Commands.argument("uuid", UuidArgument.uuid())
										.executes(RogueEngineCommands::evacuate)))
						.then(Commands.literal("archive")
								.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_ADMIN))
								.then(Commands.argument("uuid", UuidArgument.uuid())
										.executes(RogueEngineCommands::archive)))
						.then(Commands.literal("restore")
								.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_ADMIN))
								.then(Commands.argument("uuid", UuidArgument.uuid())
										.executes(RogueEngineCommands::restore)))
						.then(Commands.literal("reset")
							.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_ADMIN))
							.then(Commands.argument("uuid", UuidArgument.uuid())
									.executes(RogueEngineCommands::reset)))));
		registerImportCommand(dispatcher);
	}

	private static void registerImportCommand(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal(ROOT)
				.then(Commands.literal("map")
					.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_ADMIN))
					.then(Commands.literal("snapshot")
						.requires(source -> source.getEntity() == null)
						.then(Commands.literal("import")
							.then(Commands.argument("id", IdentifierArgument.id())
								.then(Commands.argument("source", StringArgumentType.string())
									.then(Commands.argument("dimension", IdentifierArgument.id())
										.then(Commands.argument("from", BlockPosArgument.blockPos())
											.then(Commands.argument("to", BlockPosArgument.blockPos())
												.then(Commands.argument("entities", StringArgumentType.word())
														.suggests((context, builder) -> builder.suggest("true").suggest("false").buildFuture())
													.then(Commands.argument("block_entities", StringArgumentType.word())
														.suggests((context, builder) -> builder.suggest("true").suggest("false").buildFuture())
														.executes(RogueEngineCommands::importSnapshot))))))))))));
	}

	private static boolean boolArgument(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
		String value = StringArgumentType.getString(context, name);
		if (!value.equals("true") && !value.equals("false")) {
			throw new CommandSyntaxException(
					new SimpleCommandExceptionType(Component.literal("Expected true or false")),
					Component.literal(value));
		}
		return Boolean.parseBoolean(value);
	}

	private static int importSnapshot(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerRuntime runtime = runtime(context);
		Identifier id = IdentifierArgument.getId(context, "id");
		Path source = Path.of(StringArgumentType.getString(context, "source"));
		if (!source.isAbsolute()) {
			source = context.getSource().getServer().getServerDirectory().resolve(source);
		}
		ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION,
				IdentifierArgument.getId(context, "dimension"));
		Path importSource = source;
		var from = BlockPosArgument.getBlockPos(context, "from");
		var to = BlockPosArgument.getBlockPos(context, "to");
		boolean entities = boolArgument(context, "entities");
		boolean blockEntities = boolArgument(context, "block_entities");
		Path worldRoot = ((MinecraftServerAccessor) context.getSource().getServer()).getStorageSource()
				.getLevelPath(LevelResource.ROOT);
		try {
			SaveImportService.Imported imported = runtime.mutate(
					() -> {
						try {
							return new SaveImportService(worldRoot, context.getSource().getServer().registryAccess())
									.importSnapshot(id, importSource, dimension, from, to,
										new SaveImportService.ImportOptions(entities, blockEntities));
						} catch (java.io.IOException exception) {
							throw new java.io.UncheckedIOException(exception);
						}
					},
					() -> null);
			if (imported == null) {
				return fail(context, "Another box operation is in progress");
			}
			context.getSource().sendSuccess(() -> Component.literal(
					"Imported snapshot " + id + " to " + imported.file() + " sha256=" + imported.hash()
							+ " blocks=" + imported.blocks() + " block_entities=" + imported.blockEntities()
							+ " entities=" + imported.entities()), false);
			return 1;
		} catch (Exception exception) {
			return fail(context, "Snapshot import failed: " + exception.getMessage());
		}
	}

	private static void registerAdmin(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal(ROOT)
				.then(Commands.literal("admin")
						.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_OWNER))
						.then(Commands.literal("destroy")
								.then(Commands.literal("prepare")
										.then(Commands.argument("uuid", UuidArgument.uuid())
											.executes(RogueEngineCommands::prepareDestroy)))
								.then(Commands.literal("confirm")
										.then(Commands.argument("uuid", UuidArgument.uuid())
											.then(Commands.argument("token", StringArgumentType.word())
												.executes(RogueEngineCommands::confirmDestroy))))
								.then(Commands.literal("cancel")
											.then(Commands.argument("uuid", UuidArgument.uuid())
													.executes(RogueEngineCommands::cancelDestroy))))));
		dispatcher.register(Commands.literal(ROOT)
					.then(Commands.literal("admin")
						.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_OWNER)
								&& source.getEntity() == null)
						.then(Commands.literal("datapack")
							.then(Commands.literal("import")
								.then(Commands.argument("pack_name", StringArgumentType.word())
									.then(Commands.argument("source", StringArgumentType.string())
										.executes(RogueEngineCommands::importDatapack)))))));
		dispatcher.register(Commands.literal(ROOT)
					.then(Commands.literal("admin")
							.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_OWNER))
							.then(Commands.literal("stats").executes(RogueEngineCommands::stats))
							.then(Commands.literal("recover").executes(RogueEngineCommands::recover))));
	}

	private static int importDatapack(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		String packName = StringArgumentType.getString(context, "pack_name");
		Path source = Path.of(StringArgumentType.getString(context, "source"));
		if (!source.isAbsolute()) {
			source = context.getSource().getServer().getServerDirectory().resolve(source);
		}
		Path importSource = source;
		ServerRuntime runtime = runtime(context);
		try {
			Boolean imported = runtime.mutate(() -> {
				try {
					new DatapackAuthoringService(DatapackAuthoringService.configRoot())
							.importZip(importSource, packName);
					return true;
				} catch (java.io.IOException exception) {
					throw new java.io.UncheckedIOException(exception);
				}
			}, () -> false);
			if (!imported) {
				return fail(context, "Another box operation is in progress");
			}
			context.getSource().sendSuccess(() -> Component.literal("Imported local datapack " + packName), false);
			return 1;
		} catch (Exception exception) {
			return fail(context, "Datapack import failed: " + exception.getMessage());
		}
	}

	private static int captureSnapshot(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		Identifier id = IdentifierArgument.getId(context, "id");
		var from = BlockPosArgument.getLoadedBlockPos(context, "from");
		var to = BlockPosArgument.getLoadedBlockPos(context, "to");
		var storage = ((MinecraftServerAccessor) context.getSource().getServer()).getStorageSource();
		Path worldRoot = storage.getLevelPath(LevelResource.ROOT);
		try {
			SnapshotCaptureService.Captured captured = new SnapshotCaptureService(worldRoot,
					context.getSource().getServer().registryAccess()).capture(player.level(), from, to, id);
			context.getSource().sendSuccess(() -> Component.literal(
					"Captured snapshot " + id + " to " + captured.file() + " sha256=" + captured.hash()
							+ " blocks=" + captured.blocks() + " block_entities=" + captured.blockEntities()
							+ " entities=" + captured.entities()), false);
			return 1;
		} catch (Exception exception) {
			return fail(context, "Snapshot capture failed: " + exception.getMessage());
		}
	}

	private static int stats(CommandContext<CommandSourceStack> context) {
		context.getSource().sendSuccess(() -> Component.literal(runtime(context).metrics().snapshot()), false);
		return 1;
	}

	private static int recover(CommandContext<CommandSourceStack> context) {
		try {
			ServerLifecycleHooks.recoverNow(context.getSource().getServer());
			context.getSource().sendSuccess(() -> Component.literal("Recovery scan completed"), true);
			return 1;
		} catch (Exception exception) {
			return fail(context, "Recovery failed: " + exception.getMessage());
		}
	}

	private static int prepareDestroy(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		UUID id = UuidArgument.getUuid(context, "uuid");
		ServerRuntime runtime = runtime(context);
		ServerPlayer owner = context.getSource().getPlayerOrException();
		BoxDestroyService.PrepareResult result = runtime.mutate(
				() -> new BoxDestroyService(runtime).prepare(owner, id),
				() -> new BoxDestroyService.Rejected("Another box operation is in progress"));
		if (result instanceof BoxDestroyService.Prepared prepared) {
			context.getSource().sendSuccess(() -> Component.literal(
					"Destroy token: " + prepared.token() + "\nContent hash: " + prepared.contentHash()), false);
			return 1;
		}
		return fail(context, ((BoxDestroyService.Rejected) result).reason());
	}

	private static int confirmDestroy(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		UUID id = UuidArgument.getUuid(context, "uuid");
		String token = StringArgumentType.getString(context, "token");
		ServerRuntime runtime = runtime(context);
		ServerPlayer owner = context.getSource().getPlayerOrException();
		BoxDestroyService.LifecycleResult result = runtime.mutate(
				() -> new BoxDestroyService(runtime).confirm(owner, id, token),
				() -> new BoxDestroyService.Rejected("Another box operation is in progress"));
		if (result instanceof BoxDestroyService.Destroyed) {
			context.getSource().sendSuccess(() -> Component.literal("Destroyed " + id + " (quarantined)"), true);
			return 1;
		}
		return fail(context, ((BoxDestroyService.Rejected) result).reason());
	}

	private static int cancelDestroy(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		UUID id = UuidArgument.getUuid(context, "uuid");
		new BoxDestroyService(runtime(context)).cancel(context.getSource().getPlayerOrException(), id);
		context.getSource().sendSuccess(() -> Component.literal("Destroy request cancelled"), true);
		return 1;
	}

	private static int listProfiles(CommandContext<CommandSourceStack> context) {
		try {
			ProfileCatalog.from(context.getSource().getServer().reloadableRegistries().lookup()).ids()
					.forEach(id -> context.getSource().sendSuccess(() -> Component.literal(id.toString()), false));
			return 1;
		} catch (Exception exception) {
			RogueEngineFabric.LOGGER.error("Failed to list Rogue Engine profiles", exception);
			return fail(context, "Profile catalog failed: " + exception.getClass().getSimpleName() + ": " + exception.getMessage());
		}
	}

	private static int create(CommandContext<CommandSourceStack> context, String defaultName) {
		return create(context, defaultName, SelectionRequest.all());
	}

	private static int createWithMaps(CommandContext<CommandSourceStack> context, String scenes) {
		List<Identifier> sceneIds = Arrays.stream(scenes.split(","))
				.map(String::trim)
				.filter(value -> !value.isEmpty())
				.map(Identifier::parse)
				.toList();
		return create(context, "New Box", new SelectionRequest(sceneIds, List.of()));
	}

	private static int create(CommandContext<CommandSourceStack> context, String defaultName, SelectionRequest selectionRequest) {
		ServerRuntime runtime = runtime(context);
		if (runtime.isReloading() || runtime.isStopping()) {
			return fail(context, "The engine is reloading or stopping");
		}
		Identifier profile = IdentifierArgument.getId(context, "profile");
		String name = defaultName;
		try {
			name = StringArgumentType.getString(context, "name");
		} catch (IllegalArgumentException ignored) {
			// The optional display name was omitted.
		}
		final Identifier selectedProfile = profile;
		final String boxName = name;
		BoxCreationService.CreateResult result = runtime.mutate(
				() -> runtime.creation().create(selectedProfile, boxName, selectionRequest),
				() -> new BoxCreationService.Rejected("Another box operation is in progress"));
		if (result instanceof BoxCreationService.Created created) {
			context.getSource().sendSuccess(() -> Component.literal("Created box " + created.id()), true);
			return 1;
		}
		if (result instanceof BoxCreationService.Pending pending) {
			context.getSource().sendSuccess(() -> Component.literal("Preparing box " + pending.id()), true);
			return 1;
		}
		return fail(context, ((BoxCreationService.Rejected) result).reason());
	}

	private static int listInstances(CommandContext<CommandSourceStack> context) {
		for (BoxInstance instance : runtime(context).boxes().all()) {
			context.getSource().sendSuccess(() -> Component.literal(instance.id() + " " + instance.state() + " " + instance.name()), false);
		}
		return 1;
	}

	private static int info(CommandContext<CommandSourceStack> context) {
		UUID id = UuidArgument.getUuid(context, "uuid");
		BoxInstance instance = runtime(context).boxes().get(id).orElse(null);
		if (instance == null) {
			return fail(context, "Unknown box: " + id);
		}
		var definition = runtime(context).journal().definition(id);
		context.getSource().sendSuccess(() -> Component.literal(
				instance.id() + " state=" + instance.state() + " version=" + instance.stateVersion()
						+ " profile=" + definition.map(value -> value.profileId().toString()).orElse("<missing>")
						+ " seed=" + definition.map(value -> Long.toString(value.seed())).orElse("<missing>")
						+ definition.map(value -> value.sceneSnapshot() == null ? ""
							: " map=" + value.sceneSnapshot().sceneId() + " resets=" + value.sceneSnapshot().resetCount())
							.orElse("")), false);
		return 1;
	}

	private static int teleport(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerRuntime runtime = runtime(context);
		if (runtime.isReloading() || runtime.isStopping()) {
			return fail(context, "The engine is reloading or stopping");
		}
		UUID id = UuidArgument.getUuid(context, "uuid");
		BoxInstance instance = runtime.boxes().get(id).orElse(null);
		if (instance == null || instance.state() != spectrum255.rogueengine.instance.BoxState.ACTIVE) {
			return fail(context, "Box is not active: " + id);
		}
		var definition = runtime.journal().definition(id)
				.orElseThrow(() -> new IllegalStateException("Box definition is missing"));
		new PlayerTransferService(runtime).teleport(context.getSource().getPlayerOrException(), definition);
		context.getSource().sendSuccess(() -> Component.literal("Preparing teleport to " + id), true);
		return 1;
	}

	private static int evacuate(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerRuntime runtime = runtime(context);
		UUID id = UuidArgument.getUuid(context, "uuid");
		BoxInstance instance = runtime.boxes().get(id).orElse(null);
		if (instance == null || instance.state() != spectrum255.rogueengine.instance.BoxState.ACTIVE) {
			return fail(context, "Box is not active: " + id);
		}
		ServerLevel level = runtime.dimensions().get(instance.dimensionKey());
		if (level == null) {
			return fail(context, "Box dimension is not loaded");
		}
		PlayerTransferService transfer = new PlayerTransferService(runtime);
		try {
			for (ServerPlayer player : List.copyOf(level.players())) {
				transfer.evacuate(player);
			}
		} catch (RuntimeException exception) {
			RogueEngineFabric.LOGGER.error("Evacuate command failed for {}", id, exception);
			return fail(context, exception.getClass().getSimpleName() + ": " + exception.getMessage());
		}
		context.getSource().sendSuccess(() -> Component.literal("Evacuated players from " + id), true);
		return 1;
	}

	private static int archive(CommandContext<CommandSourceStack> context) {
		UUID id = UuidArgument.getUuid(context, "uuid");
		ServerRuntime runtime = runtime(context);
		BoxLifecycleService.LifecycleResult result = runtime.mutate(
				() -> new BoxLifecycleService(runtime).archive(id),
				() -> new BoxLifecycleService.Rejected("Another box operation is in progress"));
		if (result instanceof BoxLifecycleService.Success) {
			context.getSource().sendSuccess(() -> Component.literal("Archived " + id), true);
			return 1;
		}
		return fail(context, ((BoxLifecycleService.Rejected) result).reason());
	}

	private static int restore(CommandContext<CommandSourceStack> context) {
		UUID id = UuidArgument.getUuid(context, "uuid");
		ServerRuntime runtime = runtime(context);
		BoxLifecycleService.LifecycleResult result = runtime.mutate(
				() -> new BoxLifecycleService(runtime).restore(id),
				() -> new BoxLifecycleService.Rejected("Another box operation is in progress"));
		if (result instanceof BoxLifecycleService.Success) {
			context.getSource().sendSuccess(() -> Component.literal("Restored " + id), true);
			return 1;
		}
		return fail(context, ((BoxLifecycleService.Rejected) result).reason());
	}

	private static int reset(CommandContext<CommandSourceStack> context) {
		UUID id = UuidArgument.getUuid(context, "uuid");
		ServerRuntime runtime = runtime(context);
		SceneResetService.ResetResult result = runtime.mutate(
				() -> new SceneResetService(runtime).reset(id, MapScene.RefreshTrigger.MANUAL),
				() -> new SceneResetService.Rejected("Another box operation is in progress"));
		if (result instanceof SceneResetService.Started started) {
			context.getSource().sendSuccess(() -> Component.literal("Resetting box " + started.id()), true);
			return 1;
		}
		if (result instanceof SceneResetService.Pending pending) {
			context.getSource().sendSuccess(() -> Component.literal("Reset pending for box " + pending.id()), true);
			return 1;
		}
		return fail(context, ((SceneResetService.Rejected) result).reason());
	}

	private static ServerRuntime runtime(CommandContext<CommandSourceStack> context) {
		return ServerLifecycleHooks.current(context.getSource().getServer());
	}

	private static int fail(CommandContext<CommandSourceStack> context, String message) {
		context.getSource().sendFailure(Component.literal(message));
		return 0;
	}
}
