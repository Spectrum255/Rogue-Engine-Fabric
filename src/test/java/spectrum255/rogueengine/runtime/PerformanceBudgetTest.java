package spectrum255.rogueengine.runtime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

class PerformanceBudgetTest {
	@Test
	void stopsWhenTickBudgetIsExhausted() {
		AtomicLong now = new AtomicLong();
		PerformanceBudget budget = new PerformanceBudget(5, now::get);

		assertTrue(budget.allowsMore());
		now.set(6_000_000L);
		assertFalse(budget.allowsMore());
	}
}
