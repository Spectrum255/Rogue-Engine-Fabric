package spectrum255.rogueengine.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;

class PlayerSceneStateStoreTest {
	@TempDir
	Path temp;

	@BeforeAll
	static void bootstrap() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	void writesReadsAndDeletesState() throws IOException {
		PlayerSceneStateStore store = store();
		PlayerSceneState state = state();

		store.write(state);
		assertEquals(state, store.read(state.instanceId(), state.playerId()).orElseThrow());
		store.delete(state.instanceId(), state.playerId());
		assertTrue(store.read(state.instanceId(), state.playerId()).isEmpty());
	}

	@Test
	void rejectsOversizedFile() throws IOException {
		PlayerSceneStateStore store = store();
		PlayerSceneState state = state();
		Files.createDirectories(store.pathFor(state.instanceId(), state.playerId()).getParent());
		Files.write(store.pathFor(state.instanceId(), state.playerId()), new byte[65 * 1024]);

		assertThrows(IOException.class, () -> store.read(state.instanceId(), state.playerId()));
	}

	private PlayerSceneStateStore store() {
		return new PlayerSceneStateStore(temp, RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
	}

	private PlayerSceneState state() {
		return new PlayerSceneState(UUID.randomUUID(), UUID.randomUUID(), 20.0F,
				new PlayerFoodState(20, 5.0F, 0.0F, 0), 300, 0, 0.0F, 0, List.of(), 0L);
	}
}
