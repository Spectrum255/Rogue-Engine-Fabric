package spectrum255.rogueengine.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import spectrum255.rogueengine.instance.BoxInstance;
import spectrum255.rogueengine.instance.BoxState;

class InstanceRecoveryServiceTest {
	@TempDir
	Path tempDirectory;

	@Test
	void interruptedCreationRequiresManualRecovery() throws IOException {
		InstanceJournal journal = journalWithPending("COMPLETE_CREATE", BoxState.CREATING, BoxState.ACTIVE);

		List<InstanceRecoveryService.Directive> directives = new InstanceRecoveryService(journal).plan();

		assertEquals(1, directives.size());
		assertEquals(InstanceRecoveryService.Type.MANUAL, directives.getFirst().type());
	}

	@Test
	void interruptedArchiveCanBeRetried() throws IOException {
		InstanceJournal journal = journalWithPending("COMPLETE_ARCHIVE", BoxState.ACTIVE, BoxState.ARCHIVING);

		List<InstanceRecoveryService.Directive> directives = new InstanceRecoveryService(journal).plan();

		assertEquals(InstanceRecoveryService.Type.RETRY_ARCHIVE, directives.getFirst().type());
	}

	@Test
	void interruptedRestoreCanBeRetried() throws IOException {
		InstanceJournal journal = journalWithPending("COMPLETE_RESTORE", BoxState.ARCHIVED, BoxState.RESTORING);

		List<InstanceRecoveryService.Directive> directives = new InstanceRecoveryService(journal).plan();

		assertEquals(InstanceRecoveryService.Type.RETRY_RESTORE, directives.getFirst().type());
	}

	@Test
	void interruptedResetCanBeRetried() throws IOException {
		InstanceJournal journal = journalWithPending("COMPLETE_RESET", BoxState.RESETTING, BoxState.ACTIVE);

		List<InstanceRecoveryService.Directive> directives = new InstanceRecoveryService(journal).plan();

		assertEquals(InstanceRecoveryService.Type.RETRY_RESET, directives.getFirst().type());
	}

	@Test
	void interruptedDestroyOnlyReconcilesQuarantine() throws IOException {
		InstanceJournal journal = journalWithPending("COMPLETE_DESTROY", BoxState.ARCHIVED, BoxState.DESTROY_PENDING);

		List<InstanceRecoveryService.Directive> directives = new InstanceRecoveryService(journal).plan();

		assertEquals(InstanceRecoveryService.Type.RECONCILE_QUARANTINE, directives.getFirst().type());
	}

	@Test
	void unknownActionFailsClosed() throws IOException {
		InstanceJournal journal = journalWithPending("UNKNOWN_ACTION", BoxState.CREATING, BoxState.ACTIVE);

		List<InstanceRecoveryService.Directive> directives = new InstanceRecoveryService(journal).plan();

		assertEquals(InstanceRecoveryService.Type.MANUAL, directives.getFirst().type());
	}

	@Test
	void cleanJournalHasNoDirectives() throws IOException {
		InstanceJournal journal = new InstanceJournal(tempDirectory);

		assertTrue(new InstanceRecoveryService(journal).plan().isEmpty());
	}

	private InstanceJournal journalWithPending(String action, BoxState from, BoxState to) throws IOException {
		InstanceJournal journal = new InstanceJournal(tempDirectory);
		UUID id = UUID.randomUUID();
		BoxInstance instance = BoxInstance.create(id, "Recovery Box");
		journal.begin(instance);
		if (from == BoxState.ACTIVE || from == BoxState.ARCHIVED || from == BoxState.RESETTING) {
			instance = instance.withState(BoxState.ACTIVE, 2);
			journal.commit(instance);
		}
		if (from == BoxState.RESETTING) {
			instance = instance.withState(BoxState.RESETTING, 3);
			journal.commit(instance);
		}
		if (from == BoxState.ARCHIVED) {
			instance = instance.withState(BoxState.ARCHIVING, 3);
			journal.commit(instance);
			instance = instance.withState(BoxState.ARCHIVED, 4);
			journal.commit(instance);
		}
		long expectedVersion = instance.stateVersion();
		journal.appendIntent(new InstanceJournal.WalRecord(
				UUID.randomUUID(), id, from, to, expectedVersion, expectedVersion + 1, action, 1L));
		return journal;
	}
}
