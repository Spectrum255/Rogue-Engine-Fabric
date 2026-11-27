package spectrum255.rogueengine.runtime;

import java.util.concurrent.atomic.AtomicLong;

public final class PerformanceMetrics {
	private final AtomicLong created = new AtomicLong();
	private final AtomicLong archived = new AtomicLong();
	private final AtomicLong restored = new AtomicLong();
	private final AtomicLong destroyed = new AtomicLong();
	private final AtomicLong preparationFailures = new AtomicLong();
	private final AtomicLong preparationNanos = new AtomicLong();

	public void recordCreate() {
		created.incrementAndGet();
	}

	public void recordArchive() {
		archived.incrementAndGet();
	}

	public void recordRestore() {
		restored.incrementAndGet();
	}

	public void recordDestroy() {
		destroyed.incrementAndGet();
	}

	public void recordPreparation(long elapsedNanos, boolean success) {
		preparationNanos.addAndGet(elapsedNanos);
		if (!success) {
			preparationFailures.incrementAndGet();
		}
	}

	public String snapshot() {
		return "created=" + created + ", archived=" + archived + ", restored=" + restored
				+ ", destroyed=" + destroyed + ", preparationFailures=" + preparationFailures
				+ ", preparationTotalMs=" + preparationNanos.get() / 1_000_000;
	}
}
