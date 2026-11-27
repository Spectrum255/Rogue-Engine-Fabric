package spectrum255.rogueengine.runtime;

import java.util.concurrent.atomic.AtomicBoolean;

public final class MutationGate {
	private final AtomicBoolean busy = new AtomicBoolean();

	public boolean tryBegin() {
		return busy.compareAndSet(false, true);
	}

	public void end() {
		busy.set(false);
	}

	public boolean isBusy() {
		return busy.get();
	}
}
