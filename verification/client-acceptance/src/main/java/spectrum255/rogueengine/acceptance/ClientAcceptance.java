package spectrum255.rogueengine.acceptance;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.BlockPos;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

public final class ClientAcceptance implements ClientModInitializer {
	private static final String FULL_NAME = "Acceptance Full";
	private static final String SNAPSHOT_NAME = "Acceptance Snapshot";
	private final Path worldDir = Path.of(System.getProperty("rogue.acceptance.worldDir", "world"));
	private final Path resultFile = Path.of(System.getProperty("rogue.acceptance.result", "acceptance-result.txt"));
	private final Path debugFile = resultFile.resolveSibling("acceptance-debug.log");
	private int stage;
	private int stageTicks;
	private int totalTicks;
	private int debugTicks;
	private String snapshotId;
	private String fullId;
	private boolean finished;

	@Override
	public void onInitializeClient() {
		ClientTickEvents.END_CLIENT_TICK.register(this::tick);
		appendDebug("initialized");
	}

	private void tick(Minecraft client) {
		debugTicks++;
		if (debugTicks % 100 == 0) {
			appendDebug("screen=" + (client.gui.screen() == null ? "null" : client.gui.screen().getClass().getName())
					+ " server=" + client.getCurrentServer() + " player=" + (client.player != null)
					+ " level=" + (client.level != null) + " stage=" + stage);
		}
		if (finished) {
			return;
		}
		if (client.getCurrentServer() == null && client.gui.screen() instanceof TitleScreen) {
			String address = System.getProperty("rogue.acceptance.server", "127.0.0.1:25586");
			appendDebug("connecting to " + address);
			Screen screen = client.gui.screen();
			ConnectScreen.startConnecting(screen, client, ServerAddress.parseString(address),
					new ServerData("Acceptance", address, ServerData.Type.OTHER), false, null);
			return;
		}
		if (client.player == null || client.level == null || client.player.connection == null) {
			return;
		}
		totalTicks++;
		stageTicks++;
		if (totalTicks > 12_000) {
			fail(client, "Timed out at stage " + stage);
			return;
		}
		try {
			switch (stage) {
				case 0 -> {
					send(client, "gamemode creative @s");
					send(client, "tp @s 0 129 0");
					nextStage();
				}
				case 1 -> {
					if (stageTicks < 20) return;
					send(client, "fill 0 128 0 4 128 4 minecraft:stone");
					send(client, "setblock 2 128 2 minecraft:chest");
					send(client, "summon minecraft:armor_stand 2.5 129 2.5 {NoGravity:1b}");
					nextStage();
				}
				case 2 -> {
					if (stageTicks < 20) return;
					send(client, "rogueengine map snapshot capture example:captured 0 128 0 4 129 4");
					nextStage();
				}
				case 3 -> {
					if (!Files.isRegularFile(snapshotFile())) return;
					send(client, "rogueengine instance create rogueengine:acceptance_snapshot " + SNAPSHOT_NAME);
					nextStage();
				}
				case 4 -> {
					JsonObject instance = instance(SNAPSHOT_NAME);
					if (instance == null || !"ACTIVE".equals(instance.get("state").getAsString())) return;
					snapshotId = instance.get("uuid").getAsString();
					send(client, "rogueengine instance teleport " + snapshotId);
					nextStage();
				}
				case 5 -> {
					if (!insideInstance(client, snapshotId) || stageTicks < 20) return;
					if (snapshotReady(client)) {
						System.out.println("ACCEPTANCE_SNAPSHOT_OK");
						send(client, "rogueengine instance evacuate " + snapshotId);
						nextStage();
					} else if (stageTicks > 600) {
						BlockPos playerPos = client.player.blockPosition();
						fail(client, "Snapshot box did not restore the captured chest and item; dimension="
								+ client.level.dimension().identifier() + " player=" + playerPos
								+ " target=" + client.level.getBlockState(new BlockPos(2, 128, 2))
								+ " below=" + client.level.getBlockState(playerPos.below()));
					}
				}
				case 6 -> {
					if (insideInstance(client, snapshotId) || stageTicks < 10) return;
					send(client, "rogueengine instance archive " + snapshotId);
					nextStage();
				}
				case 7 -> {
					if (stageTicks < 20) return;
					send(client, "gamemode survival @s");
					send(client, "rogueengine instance create rogueengine:acceptance_full " + FULL_NAME);
					nextStage();
				}
				case 8 -> {
					JsonObject instance = instance(FULL_NAME);
					if (instance == null || !"ACTIVE".equals(instance.get("state").getAsString())) return;
					fullId = instance.get("uuid").getAsString();
					send(client, "rogueengine instance teleport " + fullId);
					nextStage();
				}
				case 9 -> {
					if (!insideInstance(client, fullId) || stageTicks < 40) return;
					send(client, "damage @s 4");
					send(client, "effect give @s minecraft:hunger 10 0");
					send(client, "effect give @s minecraft:speed 10 0");
					send(client, "xp set @s 5 levels");
					nextStage();
				}
				case 10 -> {
					boolean changed = client.player.getHealth() < 20.0F
							|| client.player.getFoodData().getFoodLevel() < 20
							|| client.player.hasEffect(MobEffects.HUNGER)
							|| client.player.hasEffect(MobEffects.SPEED);
					if (!changed && stageTicks < 200) return;
					if (!changed) {
						fail(client, "FULL test state did not change before reset");
						return;
					}
					send(client, "rogueengine instance evacuate " + fullId);
					nextStage();
				}
				case 11 -> {
					if (insideInstance(client, fullId) || stageTicks < 10) return;
					send(client, "rogueengine instance reset " + fullId);
					nextStage();
				}
				case 12 -> {
					JsonObject instance = instance(FULL_NAME);
					if (instance == null || !"ACTIVE".equals(instance.get("state").getAsString())) return;
					JsonObject definition = instance.getAsJsonObject("definition");
					JsonObject scene = definition.getAsJsonObject("scene_snapshot");
					if (scene == null || scene.get("reset_count").getAsInt() < 1) return;
					send(client, "rogueengine instance teleport " + fullId);
					nextStage();
				}
				case 13 -> {
					if (!insideInstance(client, fullId) || stageTicks < 40) return;
					verifyFull(client);
					pass(client);
				}
				default -> fail(client, "Unknown stage " + stage);
			}
		} catch (Throwable throwable) {
			fail(client, throwable.getClass().getSimpleName() + ": " + throwable.getMessage());
		}
	}

	private static boolean snapshotReady(Minecraft client) {
		if (!client.level.getBlockState(new BlockPos(2, 128, 2)).is(Blocks.CHEST)) {
			return false;
		}
		return client.level.getEntities(client.player, new AABB(1, 128, 1, 4, 131, 4))
				.stream().anyMatch(ArmorStand.class::isInstance);
	}

	private void verifyFull(Minecraft client) throws IOException {
		float health = client.player.getHealth();
		int food = client.player.getFoodData().getFoodLevel();
		int air = client.player.getAirSupply();
		int xp = client.player.experienceLevel;
		boolean hunger = client.player.hasEffect(MobEffects.HUNGER);
		boolean speed = client.player.hasEffect(MobEffects.SPEED);
		if (health < 20.0F || food < 20 || air < 300 || xp != 0 || hunger || speed) {
			throw new IOException("FULL state was not restored: health=" + health + " food=" + food
					+ " air=" + air + " xp=" + xp + " hunger=" + hunger + " speed=" + speed);
		}
		System.out.println("ACCEPTANCE_FULL_OK health=" + health + " food=" + food + " air=" + air + " xp=" + xp);
	}

	private JsonObject instance(String name) {
		try {
			Path catalog = worldDir.resolve("data/rogueengine/catalog-v1.json");
			if (!Files.isRegularFile(catalog)) return null;
			JsonElement root = JsonParser.parseString(Files.readString(catalog));
			for (JsonElement element : root.getAsJsonObject().getAsJsonArray("instances")) {
				JsonObject instance = element.getAsJsonObject();
				if (name.equals(instance.get("name").getAsString())) {
					return instance;
				}
			}
		} catch (IOException | JsonSyntaxException | IllegalStateException ignored) {
		}
		return null;
	}

	private Path snapshotFile() {
		return worldDir.resolve("data/rogueengine/map-snapshots/example/captured.nbt");
	}

	private static boolean insideInstance(Minecraft client, String id) {
		return id != null && client.level.dimension().identifier().toString()
				.endsWith("instance/" + id.replace("-", ""));
	}

	private static void send(Minecraft client, String command) {
		client.player.connection.sendCommand(command);
	}

	private void nextStage() {
		stage++;
		stageTicks = 0;
		appendDebug("stage=" + stage);
	}

	private void pass(Minecraft client) {
		writeResult("PASS snapshot=" + snapshotId + " full=" + fullId);
		finished = true;
		client.stop();
	}

	private void fail(Minecraft client, String message) {
		writeResult("FAIL stage=" + stage + " " + message);
		finished = true;
		client.stop();
	}

	private void writeResult(String result) {
		try {
			Files.createDirectories(resultFile.toAbsolutePath().getParent());
			Files.writeString(resultFile, result + System.lineSeparator());
		} catch (IOException exception) {
			exception.printStackTrace();
		}
	}

	private void appendDebug(String message) {
		try {
			Files.createDirectories(debugFile.toAbsolutePath().getParent());
			Files.writeString(debugFile, message + System.lineSeparator(),
					java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
		} catch (IOException ignored) {
		}
	}
}
