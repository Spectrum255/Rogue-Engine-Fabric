package spectrum255.rogueengine.dimension;

import java.io.IOException;
import java.io.UncheckedIOException;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

import spectrum255.rogueengine.instance.BoxDefinition;
import spectrum255.rogueengine.lifecycle.ServerLifecycleHooks;
import spectrum255.rogueengine.profile.BoxProfile;
import spectrum255.rogueengine.runtime.ServerRuntime;
import spectrum255.rogueengine.worldgen.BoundedNoiseGenerator;
import spectrum255.rogueengine.worldgen.CentralIslandGenerator;

public final class PlayerTransferService {
	private final ServerRuntime runtime;

	public PlayerTransferService(ServerRuntime runtime) {
		this.runtime = runtime;
	}

	public void teleport(ServerPlayer player, BoxDefinition definition) {
		ServerLevel level = runtime.dimensions().get(spectrum255.rogueengine.instance.BoxInstance.dimensionKey(definition.instanceId()));
		if (level == null) {
			throw new IllegalStateException("Box dimension is not active");
		}
		BoxProfile profile = definition.profile(level.getServer().registryAccess());
		BlockPos target;
		float yaw = player.getYRot();
		if (profile.spawn() instanceof BoxProfile.FixedSpawn fixed) {
			target = new BlockPos((int) Math.floor(fixed.x()), (int) Math.floor(fixed.y()), (int) Math.floor(fixed.z()));
			yaw = fixed.yaw();
		} else {
			double centerX = profile.border().centerX();
			double centerZ = profile.border().centerZ();
			int targetY = 64;
			if (level.getChunkSource().getGenerator() instanceof BoundedNoiseGenerator bounded) {
				centerX = bounded.boundary().centerX();
				centerZ = bounded.boundary().centerZ();
			}
			if (level.getChunkSource().getGenerator() instanceof CentralIslandGenerator island) {
				centerX = island.boundary().centerX();
				centerZ = island.boundary().centerZ();
				targetY = island.spawnY();
			}
			target = new BlockPos((int) centerX, targetY, (int) centerZ);
		}
		final float teleportYaw = yaw;
		runtime.preparation().prepare(player, level, target, runtime.settings().preloadRadius(),
				SpawnPreparationPolicy.shouldSearch(profile.spawn()), spawn -> {
				try {
					runtime.inventories().enter(player, definition, spawn);
				} catch (IOException exception) {
					throw new UncheckedIOException(exception);
				}
				ServerPlayer teleported = runtime.inventories().runGuarded(player, () -> player.teleport(new TeleportTransition(
						level, new Vec3(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5),
						Vec3.ZERO, teleportYaw, 0.0F, TeleportTransition.DO_NOTHING)));
				if (teleported == null) {
					runtime.inventories().rollback(player);
					throw new IllegalStateException("Teleport was rejected");
				}
		});
	}

	public void evacuate(ServerPlayer player) {
		TeleportTransition transition = fallbackRespawnTransition(player);
		try {
			runtime.inventories().leaveCurrent(player, transition.newLevel(), BlockPos.containing(transition.position()));
		} catch (IOException exception) {
			throw new UncheckedIOException(exception);
		}
		ServerPlayer teleported = runtime.inventories().runGuarded(player, () -> player.teleport(transition));
		if (teleported == null) {
			runtime.inventories().rollback(player);
			throw new IllegalStateException("Evacuation teleport was rejected");
		}
	}

	public static TeleportTransition fallbackRespawnTransition(ServerPlayer player) {
		ResourceKey<Level> fallbackKey = ResourceKey.create(Registries.DIMENSION,
				ServerLifecycleHooks.current(player.level().getServer()).settings().fallbackDimension());
		ServerLevel fallback = player.level().getServer().getLevel(fallbackKey);
		if (fallback == null) {
			throw new IllegalStateException("Fallback dimension is not loaded: " + fallbackKey);
		}
		var spawn = fallback.getRespawnData();
		return new TeleportTransition(fallback, Vec3.atBottomCenterOf(spawn.pos()), Vec3.ZERO,
				spawn.yaw(), spawn.pitch(), TeleportTransition.DO_NOTHING);
	}
}
