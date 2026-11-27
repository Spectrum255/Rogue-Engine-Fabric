package spectrum255.rogueengine.inventory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;

import spectrum255.rogueengine.RogueEngineFabric;
import spectrum255.rogueengine.dimension.DimensionSeeds;
import spectrum255.rogueengine.instance.BoxDefinition;
import spectrum255.rogueengine.instance.BoxInstance;
import spectrum255.rogueengine.map.VanillaPlayerSceneStateAccess;
import spectrum255.rogueengine.profile.BoxProfile;
import spectrum255.rogueengine.runtime.ServerRuntime;

public final class PlayerInventoryService {
	private final ServerRuntime runtime;
	private final PlayerInventoryStore store;
	private final Set<UUID> guarded = ConcurrentHashMap.newKeySet();
	private final Set<UUID> managedDeaths = ConcurrentHashMap.newKeySet();
	private final java.util.Map<UUID, PlayerInventorySnapshot> deathInventories = new ConcurrentHashMap<>();

	public PlayerInventoryService(ServerRuntime runtime, PlayerInventoryStore store) {
		this.runtime = runtime;
		this.store = store;
	}

	public void enter(ServerPlayer player, BoxDefinition definition, BlockPos destinationPosition) throws IOException {
		assertServerThread();
		ServerLevel destination = runtime.dimensions().get(BoxInstance.dimensionKey(definition.instanceId()));
		if (destination == null) {
			throw new IllegalStateException("Box dimension is not active");
		}
		if (isDynamic(player.level().dimension()) && !player.level().dimension().equals(destination.dimension())) {
			leaveCurrent(player, destination, destinationPosition);
		}
		PlayerInventoryRecord existing = store.read(player.getUUID(), definition.instanceId()).orElse(null);
		if (existing != null && isPending(existing.phase())) {
			throw new IllegalStateException("A previous inventory transition is still pending");
		}
		BoxProfile.InventoryPolicy policy = definition.profile(runtime.server().registryAccess()).inventory();
		InventoryTransferPolicy.Plan plan = InventoryTransferPolicy.enter(policy, existing != null);
		PlayerInventorySnapshot outside = PlayerInventorySnapshot.capture(player);
		PlayerInventorySnapshot inside = existing == null ? PlayerInventorySnapshot.empty() : existing.inside();
		ResourceKey<Level> source = player.level().dimension();
		store.write(new PlayerInventoryRecord(1, player.getUUID(), definition.instanceId(), PlayerInventoryRecord.Phase.ENTERING,
				plan.saveOutside(), outside, inside, source, destination.dimension()));
		try {
			if (plan.applyInside()) {
				inside.apply(player);
			} else if (plan.mergeInside()) {
				inside.mergeInto(player, destination, destinationPosition);
			}
			store.write(new PlayerInventoryRecord(1, player.getUUID(), definition.instanceId(), PlayerInventoryRecord.Phase.INSIDE,
					plan.saveOutside(), outside, inside, source, destination.dimension()));
			runtime.playerStates().prepareEntry(
					new VanillaPlayerSceneStateAccess(player), definition.sceneSnapshot());
		} catch (Exception exception) {
			runtime.playerStates().rollbackEntry(new VanillaPlayerSceneStateAccess(player));
			outside.apply(player);
			store.write(new PlayerInventoryRecord(1, player.getUUID(), definition.instanceId(), PlayerInventoryRecord.Phase.IDLE,
					plan.saveOutside(), outside, inside, null, null));
			throw exception;
		}
	}

	public void leaveCurrent(ServerPlayer player, ServerLevel destination, BlockPos destinationPosition) throws IOException {
		BoxDefinition definition = definitionFor(player.level().dimension());
		if (definition == null) {
			return;
		}
		leave(player, definition, destination, destinationPosition);
	}

	public void leave(ServerPlayer player, BoxDefinition definition, ServerLevel destination, BlockPos destinationPosition)
			throws IOException {
		assertServerThread();
		PlayerInventoryRecord existing = store.read(player.getUUID(), definition.instanceId())
				.orElseThrow(() -> new IllegalStateException("No inventory session exists for this box"));
		if (existing.phase() != PlayerInventoryRecord.Phase.INSIDE) {
			throw new IllegalStateException("Player is not in a stable box inventory session");
		}
		BoxProfile.InventoryPolicy policy = definition.profile(runtime.server().registryAccess()).inventory();
		InventoryTransferPolicy.Plan plan = InventoryTransferPolicy.leave(policy, existing.hasOutside());
		PlayerInventorySnapshot current = PlayerInventorySnapshot.capture(player);
		PlayerInventorySnapshot insideAfter = plan.saveInside() ? current : PlayerInventorySnapshot.empty();
		store.write(new PlayerInventoryRecord(1, player.getUUID(), definition.instanceId(), PlayerInventoryRecord.Phase.EXITING,
				existing.hasOutside(), existing.outside(), current, player.level().dimension(), destination.dimension()));
		try {
			if (plan.restoreOutside()) {
				existing.outside().apply(player);
			} else if (plan.clearIfNoOutside()) {
				PlayerInventorySnapshot.empty().apply(player);
			}
			if (plan.mergeInside()) {
				current.mergeInto(player, destination, destinationPosition);
			}
			store.write(new PlayerInventoryRecord(1, player.getUUID(), definition.instanceId(), PlayerInventoryRecord.Phase.IDLE,
					existing.hasOutside(), existing.outside(), insideAfter, null, null));
		} catch (Exception exception) {
			current.apply(player);
			store.write(new PlayerInventoryRecord(1, player.getUUID(), definition.instanceId(), PlayerInventoryRecord.Phase.INSIDE,
					existing.hasOutside(), existing.outside(), current, player.level().dimension(), destination.dimension()));
			throw exception;
		}
	}

	public boolean beforeTeleport(ServerPlayer player, TeleportTransition transition) {
		if (guarded.contains(player.getUUID())) {
			return true;
		}
		try {
			ResourceKey<Level> source = player.level().dimension();
			ResourceKey<Level> destination = transition.newLevel().dimension();
			BlockPos position = BlockPos.containing(transition.position());
			if (!source.equals(destination) && isDynamic(source)) {
				leaveCurrent(player, transition.newLevel(), position);
			}
			if (!source.equals(destination) && isDynamic(destination)) {
				BoxDefinition definition = definitionFor(destination);
				if (definition != null) {
					enter(player, definition, position);
				}
			}
			return true;
		} catch (Exception exception) {
			RogueEngineFabric.LOGGER.error("Inventory transition cancelled teleport for {}", player.getUUID(), exception);
			return false;
		}
	}

	public <T> T runGuarded(ServerPlayer player, Supplier<T> action) {
		guarded.add(player.getUUID());
		try {
			return action.get();
		} finally {
			guarded.remove(player.getUUID());
		}
	}

	public void rollback(ServerPlayer player) {
		try {
			runtime.playerStates().rollbackEntry(new VanillaPlayerSceneStateAccess(player));
			for (PlayerInventoryRecord record : store.list(player.getUUID())) {
				if (!isPending(record.phase())) {
					continue;
				}
				boolean insideBox = record.phase() == PlayerInventoryRecord.Phase.ENTERING
						? player.level().dimension().equals(record.destination())
						: player.level().dimension().equals(record.source());
				InventoryRecoveryPolicy.Action action = InventoryRecoveryPolicy.recover(record.phase(), insideBox);
				switch (action) {
					case RESTORE_OUTSIDE -> record.outside().apply(player);
					case APPLY_INSIDE -> record.inside().apply(player);
					case FINISH_IDLE -> { }
					case NONE -> { }
				}
				PlayerInventoryRecord.Phase phase = action == InventoryRecoveryPolicy.Action.APPLY_INSIDE
						? PlayerInventoryRecord.Phase.INSIDE : PlayerInventoryRecord.Phase.IDLE;
				store.write(new PlayerInventoryRecord(1, player.getUUID(), record.instanceId(), phase, record.hasOutside(),
						record.outside(), record.inside(), phase == PlayerInventoryRecord.Phase.INSIDE ? record.source() : null,
						phase == PlayerInventoryRecord.Phase.INSIDE ? record.destination() : null));
			}
		} catch (Exception exception) {
			RogueEngineFabric.LOGGER.error("Failed to roll back inventory transition for {}", player.getUUID(), exception);
		}
	}

	public void recoverOnJoin(ServerPlayer player) {
		rollback(player);
	}

	public boolean beforeDeath(ServerPlayer player) {
		if (!isDynamic(player.level().dimension())) {
			return false;
		}
		try {
			ServerLevel fallback = runtime.server().getLevel(ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
					runtime.settings().fallbackDimension()));
			if (fallback == null) {
				return false;
			}
			leaveCurrent(player, fallback, fallback.getRespawnData().pos());
			deathInventories.put(player.getUUID(), PlayerInventorySnapshot.capture(player));
			managedDeaths.add(player.getUUID());
			return true;
		} catch (Exception exception) {
			RogueEngineFabric.LOGGER.error("Managed box death inventory transition failed for {}", player.getUUID(), exception);
			return false;
		}
	}

	public void afterRespawn(ServerPlayer player, boolean alive) {
		managedDeaths.remove(player.getUUID());
		if (alive) {
			return;
		}
		PlayerInventorySnapshot snapshot = deathInventories.remove(player.getUUID());
		if (snapshot != null) {
			snapshot.apply(player);
		}
	}

	public boolean shouldSuppressDeathLoot(ServerPlayer player) {
		return managedDeaths.contains(player.getUUID());
	}

	public Path storeRoot() {
		return store.root();
	}

	public BoxDefinition definitionFor(ResourceKey<Level> dimension) {
		for (BoxInstance instance : runtime.boxes().all()) {
			if (instance.dimensionKey().equals(dimension)) {
				return runtime.journal().definition(instance.id()).orElse(null);
			}
		}
		return null;
	}

	private static boolean isDynamic(ResourceKey<Level> dimension) {
		return DimensionSeeds.seedFor(dimension).isPresent();
	}

	private static boolean isPending(PlayerInventoryRecord.Phase phase) {
		return phase == PlayerInventoryRecord.Phase.ENTERING || phase == PlayerInventoryRecord.Phase.EXITING;
	}

	private void assertServerThread() {
		if (!runtime.server().isSameThread()) {
			throw new IllegalStateException("Inventory transitions must run on the server thread");
		}
	}
}
