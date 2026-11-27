package spectrum255.rogueengine.map;

import java.util.UUID;

public interface PlayerSceneStateAccess {
	UUID playerId();
	PlayerSceneState capture(UUID instanceId, long appliedResetCount);
	void apply(PlayerSceneState state);
}
