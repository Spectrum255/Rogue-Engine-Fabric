package spectrum255.rogueengine.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import spectrum255.rogueengine.instance.BoxInstance;
import spectrum255.rogueengine.instance.BoxState;

class InstanceJournalTest {
	@TempDir
	Path tempDirectory;

	@Test
	void committedSnapshotSurvivesReopen() throws IOException {
		InstanceJournal journal = new InstanceJournal(tempDirectory);
		UUID id = UUID.randomUUID();
		BoxInstance creating = BoxInstance.create(id, "Persistent Box");
		journal.begin(creating);
		journal.appendIntent(new InstanceJournal.WalRecord(
				UUID.randomUUID(), id, BoxState.CREATING, BoxState.ACTIVE, 1, 2, "COMPLETE_CREATE", 1L));
		journal.commit(creating.withState(BoxState.ACTIVE, 2));

		InstanceJournal.LoadResult loaded = new InstanceJournal(tempDirectory).load();

		assertEquals(BoxState.ACTIVE, loaded.instances().get(id).state());
		assertEquals(2, loaded.instances().get(id).stateVersion());
		assertTrue(loaded.pendingOperations().isEmpty());
	}

	@Test
	void intentWithoutCommitIsReportedForRecovery() throws IOException {
		InstanceJournal journal = new InstanceJournal(tempDirectory);
		UUID id = UUID.randomUUID();
		BoxInstance creating = BoxInstance.create(id, "Interrupted Box");
		journal.begin(creating);
		journal.appendIntent(new InstanceJournal.WalRecord(
				UUID.randomUUID(), id, BoxState.CREATING, BoxState.ACTIVE, 1, 2, "COMPLETE_CREATE", 1L));

		InstanceJournal.LoadResult loaded = new InstanceJournal(tempDirectory).load();

		assertEquals(BoxState.CREATING, loaded.instances().get(id).state());
		assertEquals(1, loaded.pendingOperations().size());
		assertEquals("COMPLETE_CREATE", loaded.pendingOperations().getFirst().recoveryAction());
	}

	@Test
	void incompleteWalTailIsTruncatedAndIgnored() throws IOException {
		InstanceJournal journal = new InstanceJournal(tempDirectory);
		UUID id = UUID.randomUUID();
		journal.begin(BoxInstance.create(id, "Interrupted Box"));
		journal.appendIntent(new InstanceJournal.WalRecord(
				UUID.randomUUID(), id, BoxState.CREATING, BoxState.ACTIVE, 1, 2, "COMPLETE_CREATE", 1L));
		Files.writeString(tempDirectory.resolve("operations-v1.wal"), "{\"operationId\":",
				StandardOpenOption.APPEND);

		InstanceJournal.LoadResult loaded = new InstanceJournal(tempDirectory).load();

		assertEquals(1, loaded.pendingOperations().size());
		String wal = Files.readString(tempDirectory.resolve("operations-v1.wal"));
		assertTrue(wal.endsWith("\n"));
		assertFalse(wal.endsWith("{\"operationId\":"));
	}

	@Test
	void unknownSchemaFailsClosed() throws IOException {
		Files.writeString(tempDirectory.resolve("catalog-v1.json"), "{\"schema_version\":99,\"instances\":[]}");

		assertThrows(IOException.class, () -> new InstanceJournal(tempDirectory).load());
	}

	@Test
	void corruptWalFailsClosed() throws IOException {
		InstanceJournal journal = new InstanceJournal(tempDirectory);
		journal.begin(BoxInstance.create(UUID.randomUUID(), "Box"));
		Files.writeString(tempDirectory.resolve("operations-v1.wal"), "not-json\n");

		assertThrows(IOException.class, () -> new InstanceJournal(tempDirectory).load());
	}

	@Test
	void walRejectsTransitionToOlderVersion() throws IOException {
		InstanceJournal journal = new InstanceJournal(tempDirectory);
		UUID id = UUID.randomUUID();
		journal.begin(BoxInstance.create(id, "Box"));

		assertThrows(IOException.class, () -> journal.appendIntent(new InstanceJournal.WalRecord(
				UUID.randomUUID(), id, BoxState.CREATING, BoxState.ACTIVE, 1, 1, "COMPLETE_CREATE", 1L)));
	}

	@Test
	void emptyJournalLoadsEmptySnapshot() throws IOException {
		InstanceJournal.LoadResult loaded = new InstanceJournal(tempDirectory).load();

		assertTrue(loaded.instances().isEmpty());
		assertTrue(loaded.pendingOperations().isEmpty());
	}
}
