package spectrum255.rogueengine.persistence;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public final class PlayerReferenceScanner {
	private PlayerReferenceScanner() {
	}

	public static boolean isReferenced(Path playerDataDirectory, ResourceKey<Level> dimension) throws IOException {
		return isReferenced(playerDataDirectory, dimension, Set.of());
	}

	public static boolean isReferenced(Path playerDataDirectory, ResourceKey<Level> dimension, Set<UUID> onlinePlayers)
			throws IOException {
		if (!Files.isDirectory(playerDataDirectory)) {
			return false;
		}
		Set<String> onlineFiles = onlinePlayers.stream().map(id -> id.toString() + ".dat").collect(Collectors.toSet());
		try (Stream<Path> files = Files.list(playerDataDirectory)) {
			for (Path file : files.filter(path -> Files.isRegularFile(path) && path.getFileName().toString().endsWith(".dat"))
					.filter(path -> !onlineFiles.contains(path.getFileName().toString())).toList()) {
				String savedDimension = NbtIo.readCompressed(file, NbtAccounter.uncompressedQuota())
						.getStringOr("Dimension", "");
				if (dimension.identifier().toString().equals(savedDimension)) {
					return true;
				}
			}
		}
		return false;
	}
}
