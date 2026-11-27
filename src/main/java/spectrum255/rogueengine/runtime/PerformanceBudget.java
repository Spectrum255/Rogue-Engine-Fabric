package spectrum255.rogueengine.runtime;

import java.util.Objects;
import java.util.function.LongSupplier;

public final class PerformanceBudget {
	private final int budgetMillis;
	private final LongSupplier nanoTime;
	private final long startedAt;

	public PerformanceBudget(int budgetMillis, LongSupplier nanoTime) {
		if (budgetMillis < 1) {
			throw new IllegalArgumentException("budgetMillis must be positive");
		}
		this.budgetMillis = budgetMillis;
		this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
		this.startedAt = nanoTime.getAsLong();
	}

	public boolean allowsMore() {
		return nanoTime.getAsLong() - startedAt < budgetMillis * 1_000_000L;
	}
}
