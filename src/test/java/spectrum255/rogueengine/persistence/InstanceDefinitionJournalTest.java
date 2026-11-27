package spectrum255.rogueengine.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

import spectrum255.rogueengine.map.MapScene;
import spectrum255.rogueengine.map.SceneSelectionSnapshot;
import spectrum255.rogueengine.instance.BoxDefinition;
import spectrum255.rogueengine.instance.BoxInstance;
import spectrum255.rogueengine.instance.BoxState;

class InstanceDefinitionJournalTest {
	@TempDir
	Path tempDirectory;

	@Test
	void definitionWithSceneSnapshotSurvivesReopen() throws IOException {
		InstanceJournal journal = new InstanceJournal(tempDirectory);
		UUID id = UUID.randomUUID();
		SceneSelectionSnapshot scene = new SceneSelectionSnapshot(
				id,
				Identifier.withDefaultNamespace("pool"),
				Identifier.withDefaultNamespace("scene"),
				new SceneSelectionSnapshot.Hashes("a".repeat(64), "b".repeat(64)),
				42L,
				0L,
				BlockPos.ZERO,
				BlockPos.ZERO,
				Rotation.NONE,
				Mirror.NONE,
				new MapScene.SceneBounds(BlockPos.ZERO, new BlockPos(15, 79, 15)),
				List.of(),
				"scenes/" + id + "/source.nbt",
				new MapScene.RefreshPolicy(List.of(MapScene.RefreshTrigger.MANUAL), MapScene.PlayerResetPolicy.NONE),
				0L,
				Optional.empty());
		BoxDefinition definition = new BoxDefinition(id, Identifier.withDefaultNamespace("test"),
				"{}", "profile-hash", "dependency-hash", "{}", 42L, scene);
		journal.begin(BoxInstance.create(id, "Map Box"), definition);
		journal.commit(BoxInstance.create(id, "Map Box").withState(BoxState.ACTIVE, 2));

		InstanceJournal reopened = new InstanceJournal(tempDirectory);

		assertEquals(definition, reopened.definition(id).orElseThrow());
	}

	@Test
	void definitionCanBeUpdated() throws IOException {
		InstanceJournal journal = new InstanceJournal(tempDirectory);
		UUID id = UUID.randomUUID();
		BoxDefinition original = new BoxDefinition(id, Identifier.withDefaultNamespace("test"),
				"{}", "profile-hash", "dependency-hash", "{}", 42L);
		journal.begin(BoxInstance.create(id, "Defined Box"), original);
		journal.commit(BoxInstance.create(id, "Defined Box").withState(BoxState.ACTIVE, 2));
		BoxDefinition updated = new BoxDefinition(id, Identifier.withDefaultNamespace("test"),
				"{\"updated\":true}", "profile-hash-2", "dependency-hash", "{}", 42L);

		journal.updateDefinition(updated);

		assertEquals(updated, new InstanceJournal(tempDirectory).definition(id).orElseThrow());
	}

	@Test
	void definitionSurvivesReopen() throws IOException {
		InstanceJournal journal = new InstanceJournal(tempDirectory);
		UUID id = UUID.randomUUID();
		BoxDefinition definition = new BoxDefinition(id, Identifier.withDefaultNamespace("test"),
				"{}", "profile-hash", "dependency-hash", "{}", 42L);
		journal.begin(BoxInstance.create(id, "Defined Box"), definition);
		journal.commit(BoxInstance.create(id, "Defined Box").withState(BoxState.ACTIVE, 2));

		InstanceJournal reopened = new InstanceJournal(tempDirectory);

		assertEquals(definition, reopened.definition(id).orElseThrow());
	}
}
