package spectrum255.rogueengine.map;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import spectrum255.rogueengine.RogueEngineFabric;

public final class PlayerSceneStateService {
	private final PlayerSceneStateStore store;
	private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();

	public PlayerSceneStateService(PlayerSceneStateStore store) {
		this.store = store;
	}

	public void prepareEntry(PlayerSceneStateAccess access, SceneSelectionSnapshot snapshot) throws IOException {
		if (snapshot == null || snapshot.refresh().playerReset() != MapScene.PlayerResetPolicy.FULL) {
			return;
		}
		var existing = store.read(snapshot.instanceId(), access.playerId());
		var action = PlayerSceneStatePolicy.decide(existing.isPresent(),
				existing.map(PlayerSceneState::appliedResetCount).orElse(0L), snapshot.resetCount());
		switch (action) {
			case CAPTURE -> {
				PlayerSceneState baseline = access.capture(snapshot.instanceId(), snapshot.resetCount());
				store.write(baseline);
				pending.put(access.playerId(), new Pending(baseline, snapshot.resetCount(), null, true));
			}
			case KEEP -> pending.remove(access.playerId());
			case RESTORE -> {
				PlayerSceneState baseline = existing.orElseThrow();
				PlayerSceneState rollback = access.capture(snapshot.instanceId(), baseline.appliedResetCount());
				try {
					access.apply(baseline);
				} catch (RuntimeException exception) {
					access.apply(rollback);
					throw exception;
				}
				pending.put(access.playerId(), new Pending(baseline, snapshot.resetCount(), rollback, false));
			}
		}
	}

	public void commitEntry(PlayerSceneStateAccess access) {
		Pending transition = pending.remove(access.playerId());
		if (transition == null || transition.createdBaseline()) {
			return;
		}
		try {
			store.write(transition.baseline().withAppliedResetCount(transition.targetResetCount()));
		} catch (IOException exception) {
			RogueEngineFabric.LOGGER.error("Could not commit player scene state for {}", access.playerId(), exception);
		}
	}

	public void rollbackEntry(PlayerSceneStateAccess access) {
		Pending transition = pending.remove(access.playerId());
		if (transition == null) {
			return;
		}
		try {
			if (transition.createdBaseline()) {
				store.delete(transition.baseline().instanceId(), access.playerId());
			} else if (transition.rollback() != null) {
				access.apply(transition.rollback());
			}
		} catch (Exception exception) {
			RogueEngineFabric.LOGGER.error("Could not roll back player scene state for {}", access.playerId(), exception);
		}
	}

	public void recoverOnJoin(PlayerSceneStateAccess access, SceneSelectionSnapshot snapshot) throws IOException {
		if (snapshot == null || snapshot.refresh().playerReset() != MapScene.PlayerResetPolicy.FULL) {
			return;
		}
		var existing = store.read(snapshot.instanceId(), access.playerId());
		var action = PlayerSceneStatePolicy.decide(existing.isPresent(),
				existing.map(PlayerSceneState::appliedResetCount).orElse(0L), snapshot.resetCount());
		if (action == PlayerSceneStatePolicy.Action.CAPTURE) {
			store.write(access.capture(snapshot.instanceId(), snapshot.resetCount()));
		} else if (action == PlayerSceneStatePolicy.Action.RESTORE) {
			PlayerSceneState baseline = existing.orElseThrow();
			access.apply(baseline);
			store.write(baseline.withAppliedResetCount(snapshot.resetCount()));
		}
	}

	private record Pending(PlayerSceneState baseline, long targetResetCount,
			PlayerSceneState rollback, boolean createdBaseline) {
	}
}
