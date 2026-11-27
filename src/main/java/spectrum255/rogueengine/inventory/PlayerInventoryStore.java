package spectrum255.rogueengine.inventory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import spectrum255.rogueengine.persistence.AtomicFiles;

public final class PlayerInventoryStore {
	private final Path root;

	public PlayerInventoryStore(Path root) {
		this.root = root.toAbsolutePath().normalize();
	}

	public Path root() {
		return root;
	}

	public Path path(UUID playerId, UUID instanceId) {
		try {
			AtomicFiles.verifyManagedPath(root, root.resolve(playerId.toString()).resolve(instanceId + ".dat"));
			return root.resolve(playerId.toString()).resolve(instanceId + ".dat");
		} catch (IOException exception) {
			throw new IllegalArgumentException("Invalid inventory path", exception);
		}
	}

	public Path playerDirectory(UUID playerId) {
		try {
			AtomicFiles.verifyManagedPath(root, root.resolve(playerId.toString()));
			return root.resolve(playerId.toString());
		} catch (IOException exception) {
			throw new IllegalArgumentException("Invalid inventory path", exception);
		}
	}

	public void write(PlayerInventoryRecord record) throws IOException {
		Path target = path(record.playerId(), record.instanceId());
		if (Files.exists(target)) {
			AtomicFiles.writeAtomic(target.resolveSibling(target.getFileName() + ".bak"), Files.readAllBytes(target));
		}
		CompoundTag tag = new CompoundTag();
		tag.putInt("schema_version", record.schemaVersion());
		tag.putString("player_id", record.playerId().toString());
		tag.putString("instance_id", record.instanceId().toString());
		tag.putString("phase", record.phase().name());
		tag.putBoolean("has_outside", record.hasOutside());
		tag.put("outside", record.outside().data());
		tag.put("inside", record.inside().data());
		if (record.source() != null) {
			tag.putString("source", record.source().identifier().toString());
		}
		if (record.destination() != null) {
			tag.putString("destination", record.destination().identifier().toString());
		}
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		NbtIo.writeCompressed(tag, output);
		AtomicFiles.writeAtomic(target, output.toByteArray());
	}

	public List<PlayerInventoryRecord> list(UUID playerId) throws IOException {
		Path directory = playerDirectory(playerId);
		if (!Files.isDirectory(directory)) {
			return List.of();
		}
		try (var files = Files.list(directory)) {
			List<PlayerInventoryRecord> records = new java.util.ArrayList<>();
			for (Path path : files.filter(path -> Files.isRegularFile(path)
					&& path.getFileName().toString().endsWith(".dat")).sorted().toList()) {
				try {
					UUID instanceId = UUID.fromString(path.getFileName().toString().replaceFirst("\\.dat$", ""));
					records.add(read(playerId, instanceId).orElseThrow());
				} catch (IllegalArgumentException exception) {
					throw new IOException("Invalid inventory record path: " + path, exception);
				}
			}
			return List.copyOf(records);
		}
	}

	public Optional<PlayerInventoryRecord> read(UUID playerId, UUID instanceId) throws IOException {
		Path target = path(playerId, instanceId);
		if (!Files.exists(target)) {
			return Optional.empty();
		}
		CompoundTag tag;
		try {
			tag = NbtIo.readCompressed(target, NbtAccounter.uncompressedQuota());
		} catch (IOException exception) {
			throw new IOException("Corrupt player inventory record: " + target, exception);
		}
		if (tag.getIntOr("schema_version", -1) != 1) {
			throw new IOException("Unsupported player inventory schema: " + target);
		}
		try {
			UUID storedPlayer = UUID.fromString(tag.getStringOr("player_id", ""));
			UUID storedInstance = UUID.fromString(tag.getStringOr("instance_id", ""));
			if (!playerId.equals(storedPlayer) || !instanceId.equals(storedInstance)) {
				throw new IOException("Player inventory record identity mismatch: " + target);
			}
			PlayerInventoryRecord.Phase phase = PlayerInventoryRecord.Phase.valueOf(tag.getStringOr("phase", ""));
			boolean hasOutside = tag.getBooleanOr("has_outside", false);
			PlayerInventorySnapshot outside = new PlayerInventorySnapshot(tag.getCompoundOrEmpty("outside"));
			PlayerInventorySnapshot inside = new PlayerInventorySnapshot(tag.getCompoundOrEmpty("inside"));
			ResourceKey<Level> source = tag.contains("source")
					? PlayerInventoryRecord.dimension(tag.getStringOr("source", "")) : null;
			ResourceKey<Level> destination = tag.contains("destination")
					? PlayerInventoryRecord.dimension(tag.getStringOr("destination", "")) : null;
			return Optional.of(new PlayerInventoryRecord(1, storedPlayer, storedInstance, phase, hasOutside, outside, inside,
					source, destination));
		} catch (IllegalArgumentException exception) {
			throw new IOException("Invalid player inventory record: " + target, exception);
		}
	}
}
