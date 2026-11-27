package spectrum255.rogueengine.instance;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Server-thread-only in-memory state boundary. Persistence is added by the journal layer.
 */
public final class BoxManager {
	private final Map<UUID, BoxInstance> instances = new LinkedHashMap<>();

	public BoxManager() {
	}

	public BoxManager(Iterable<BoxInstance> restored) {
		for (BoxInstance instance : restored) {
			restore(instance);
		}
	}

	public void restore(BoxInstance instance) {
		if (instances.putIfAbsent(instance.id(), instance) != null) {
			throw new IllegalArgumentException("Box UUID already exists: " + instance.id());
		}
	}

	public CreateResult create(UUID id, String name) {
		if (id == null) {
			return new Rejected("Box UUID is required");
		}
		if (instances.containsKey(id)) {
			return new Rejected("Box UUID already exists: " + id);
		}
		try {
			BoxInstance instance = BoxInstance.create(id, name);
			instances.put(id, instance);
			return new Created(instance);
		} catch (IllegalArgumentException exception) {
			return new Rejected(exception.getMessage());
		}
	}

	public TransitionResult transition(UUID id, BoxState target, long expectedVersion) {
		BoxInstance current = instances.get(id);
		if (current == null) {
			return new Rejected("Unknown box: " + id);
		}
		if (current.stateVersion() != expectedVersion) {
			return new Rejected("Box state version conflict for " + id);
		}
		if (!current.state().canTransitionTo(target)) {
			return new Rejected("Illegal box state transition: " + current.state() + " -> " + target);
		}
		BoxInstance updated = current.withState(target, expectedVersion + 1);
		instances.put(id, updated);
		return new Updated(updated);
	}

	public Optional<BoxInstance> get(UUID id) {
		return Optional.ofNullable(instances.get(id));
	}

	public List<BoxInstance> all() {
		return List.copyOf(instances.values());
	}

	public sealed interface CreateResult permits Created, Rejected {
	}

	public record Created(BoxInstance instance) implements CreateResult {
	}

	public sealed interface TransitionResult permits Updated, Rejected {
	}

	public record Updated(BoxInstance instance) implements TransitionResult {
	}

	public record Rejected(String reason) implements CreateResult, TransitionResult {
	}
}
