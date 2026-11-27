package spectrum255.rogueengine.persistence;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import spectrum255.rogueengine.instance.BoxState;

/**
 * Classifies interrupted journal operations without touching live dimensions or deleting files.
 */
public final class InstanceRecoveryService {
	private final InstanceJournal journal;

	public InstanceRecoveryService(InstanceJournal journal) {
		this.journal = journal;
	}

	public List<Directive> plan() throws IOException {
		return journal.load().pendingOperations().stream()
				.map(operation -> new Directive(operation, classify(operation)))
				.toList();
	}

	private Type classify(InstanceJournal.WalRecord operation) {
		if (operation.toState() == BoxState.RECOVERY_REQUIRED) {
			return Type.MANUAL;
		}
		return switch (operation.recoveryAction().toUpperCase(Locale.ROOT)) {
			case "COMPLETE_ARCHIVE" -> Type.RETRY_ARCHIVE;
			case "COMPLETE_RESTORE" -> Type.RETRY_RESTORE;
			case "COMPLETE_RESET" -> Type.RETRY_RESET;
			case "COMPLETE_DESTROY" -> Type.RECONCILE_QUARANTINE;
			case "COMPLETE_CREATE" -> Type.MANUAL;
			default -> Type.MANUAL;
		};
	}

	public enum Type {
		MANUAL,
		RETRY_ARCHIVE,
		RETRY_RESTORE,
		RETRY_RESET,
		RECONCILE_QUARANTINE
	}

	public record Directive(InstanceJournal.WalRecord operation, Type type) {
	}
}
