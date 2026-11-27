package spectrum255.rogueengine.command;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

public final class DestroyConfirmationService {
	private static final SecureRandom RANDOM = new SecureRandom();
	private final LongSupplier clock;
	private final long ttlMillis;
	private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();

	public DestroyConfirmationService(LongSupplier clock, long ttlMillis) {
		this.clock = clock;
		this.ttlMillis = ttlMillis;
	}

	public String prepare(Object owner, UUID instanceId, long stateVersion, String contentHash) {
		byte[] token = new byte[32];
		RANDOM.nextBytes(token);
		String value = HexFormat.of().formatHex(token);
		pending.put(instanceId, new Pending(owner, instanceId, stateVersion, contentHash, value, clock.getAsLong() + ttlMillis));
		return value;
	}

	public boolean confirm(Object owner, UUID instanceId, long stateVersion, String contentHash, String token) {
		Pending request = pending.get(instanceId);
		if (request == null) {
			return false;
		}
		if (request.expiresAt() < clock.getAsLong()) {
			pending.remove(instanceId, request);
			return false;
		}
		if (request.owner() != owner || request.stateVersion() != stateVersion
				|| !request.contentHash().equals(contentHash) || !request.token().equals(token)) {
			return false;
		}
		pending.remove(instanceId, request);
		return true;
	}

	public void cancel(Object owner, UUID instanceId) {
		Pending request = pending.get(instanceId);
		if (request != null && request.owner() == owner) {
			pending.remove(instanceId, request);
		}
	}

	public int pendingCount() {
		long now = clock.getAsLong();
		pending.values().removeIf(request -> request.expiresAt() < now);
		return pending.size();
	}

	private record Pending(Object owner, UUID instanceId, long stateVersion, String contentHash, String token, long expiresAt) {
	}
}
