package spectrum255.rogueengine.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

class PlayerSceneStateServiceTest {
	@TempDir
	Path temp;
	private PlayerSceneStateStore store;
	private final UUID instanceId = UUID.randomUUID();

	@BeforeAll
	static void bootstrap() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	void capturesOnceAndRestoresOnlyAfterReset() throws IOException {
		PlayerSceneStateService service = service();
		FakeAccess access = new FakeAccess(UUID.randomUUID(), 20.0F, 0L);
		SceneSelectionSnapshot snapshot = snapshot(access.playerId(), 0L);
		service.prepareEntry(access, snapshot);
		service.commitEntry(access);
		assertEquals(20.0F, access.current.health());

		access.setHealth(7.0F);
		service.prepareEntry(access, snapshot(access.playerId(), 1L));
		assertEquals(20.0F, access.current.health());
		service.rollbackEntry(access);
		assertEquals(7.0F, access.current.health());
		assertEquals(0L, store.read(snapshot.instanceId(), access.playerId()).orElseThrow().appliedResetCount());

		service.prepareEntry(access, snapshot(access.playerId(), 1L));
		service.commitEntry(access);
		assertEquals(1L, store.read(snapshot.instanceId(), access.playerId()).orElseThrow().appliedResetCount());
	}

	@Test
	void failedFirstEntryDoesNotLeaveBaseline() throws IOException {
		PlayerSceneStateService service = service();
		FakeAccess access = new FakeAccess(UUID.randomUUID(), 20.0F, 0L);
		SceneSelectionSnapshot snapshot = snapshot(access.playerId(), 0L);

		service.prepareEntry(access, snapshot);
		service.rollbackEntry(access);

		assertEquals(Optional.empty(), store.read(snapshot.instanceId(), access.playerId()));
	}

	@Test
	void corruptStateRejectsEntry() throws IOException {
		PlayerSceneStateService service = service();
		FakeAccess access = new FakeAccess(UUID.randomUUID(), 20.0F, 0L);
		SceneSelectionSnapshot snapshot = snapshot(access.playerId(), 0L);
		service.prepareEntry(access, snapshot);
		service.commitEntry(access);
		Files.writeString(store.pathFor(snapshot.instanceId(), access.playerId()), "broken");

		assertThrows(IOException.class, () -> service.prepareEntry(access, snapshot(access.playerId(), 1L)));
	}

	private PlayerSceneStateService service() {
		store = new PlayerSceneStateStore(temp, RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
		return new PlayerSceneStateService(store);
	}

	private SceneSelectionSnapshot snapshot(UUID playerId, long resetCount) {
		return new SceneSelectionSnapshot(instanceId, Identifier.withDefaultNamespace("pool"),
				Identifier.withDefaultNamespace("scene"), new SceneSelectionSnapshot.Hashes("a".repeat(64), "b".repeat(64)),
				42L, 0L, BlockPos.ZERO, BlockPos.ZERO, Rotation.NONE, Mirror.NONE,
				new MapScene.SceneBounds(BlockPos.ZERO, BlockPos.ZERO), List.of(),
				"scenes/" + instanceId + "/source.nbt",
				new MapScene.RefreshPolicy(List.of(MapScene.RefreshTrigger.MANUAL), MapScene.PlayerResetPolicy.FULL),
				resetCount, Optional.of("scenes/" + instanceId + "/players"));
	}

	private static final class FakeAccess implements PlayerSceneStateAccess {
		private final UUID playerId;
		private PlayerSceneState current;

		private FakeAccess(UUID playerId, float health, long watermark) {
			this.playerId = playerId;
			this.current = new PlayerSceneState(playerId, UUID.randomUUID(), health,
					new PlayerFoodState(20, 5.0F, 0.0F, 0), 300, 0, 0.0F, 0, List.of(), watermark);
		}

		@Override
		public UUID playerId() {
			return playerId;
		}

		@Override
		public PlayerSceneState capture(UUID instanceId, long watermark) {
			current = new PlayerSceneState(playerId, instanceId, current.health(), current.food(), current.airSupply(),
					current.experienceLevel(), current.experienceProgress(), current.experienceTotal(),
					current.effects(), watermark);
			return current;
		}

		private void setHealth(float health) {
			current = new PlayerSceneState(playerId, current.instanceId(), health, current.food(), current.airSupply(),
					current.experienceLevel(), current.experienceProgress(), current.experienceTotal(), current.effects(),
					current.appliedResetCount());
		}

		@Override
		public void apply(PlayerSceneState state) {
			current = state;
		}
	}
}
