package spectrum255.rogueengine.tool;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;

import spectrum255.rogueengine.map.SafeZipExtractor;
import spectrum255.rogueengine.persistence.AtomicFiles;
import spectrum255.rogueengine.profile.BoxProfile;
import spectrum255.rogueengine.profile.BoxProfileCodec;
import spectrum255.rogueengine.profile.WorldGenTemplate;
import spectrum255.rogueengine.profile.WorldGenTemplateCodec;

public final class DatapackAuthoringService {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final int PACK_FORMAT = 121;
	private final Path datapacksRoot;

	public DatapackAuthoringService(Path datapacksRoot) {
		this.datapacksRoot = Objects.requireNonNull(datapacksRoot, "datapacksRoot").toAbsolutePath().normalize();
	}

	public static Path configRoot() {
		return FabricLoader.getInstance().getConfigDir().resolve("rogueengine/datapacks").normalize();
	}

	public Path save(ConfigDraft draft) throws IOException {
		Objects.requireNonNull(draft, "draft");
		draft.validate().getOrThrow();
		Path pack = packRoot(draft.packName());
		AtomicFiles.verifyManagedPath(datapacksRoot, pack);
		Files.createDirectories(pack);
		JsonObject metadata = new JsonObject();
		JsonObject packMetadata = new JsonObject();
		packMetadata.addProperty("pack_format", PACK_FORMAT);
		packMetadata.addProperty("min_format", PACK_FORMAT);
		packMetadata.addProperty("max_format", PACK_FORMAT);
		packMetadata.addProperty("description", "Rogue Engine local datapack");
		metadata.add("pack", packMetadata);
		AtomicFiles.writeAtomic(pack.resolve("pack.mcmeta"),
				(GSON.toJson(metadata) + "\n").getBytes(StandardCharsets.UTF_8));

		JsonElement templateJson = WorldGenTemplateCodec.CODEC.encodeStart(JsonOps.INSTANCE, draft.template())
				.getOrThrow();
		JsonElement profileJson = BoxProfileCodec.CODEC.encodeStart(JsonOps.INSTANCE, draft.profile()).getOrThrow();
		AtomicFiles.writeAtomic(templatePath(pack, draft.templateId()),
				(GSON.toJson(templateJson) + "\n").getBytes(StandardCharsets.UTF_8));
		AtomicFiles.writeAtomic(profilePath(pack, draft.profileId()),
				(GSON.toJson(profileJson) + "\n").getBytes(StandardCharsets.UTF_8));
		return pack;
	}

	public Path exportZip(String packName, Path target) throws IOException {
		Path source = packRoot(packName);
		if (!Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS)) {
			throw new IOException("Local datapack does not exist: " + packName);
		}
		Path normalizedTarget = target.toAbsolutePath().normalize();
		if (normalizedTarget.startsWith(source)) {
			throw new IOException("ZIP export target must be outside the datapack directory");
		}
		Files.createDirectories(normalizedTarget.getParent());
		try (ZipArchiveOutputStream output = new ZipArchiveOutputStream(normalizedTarget.toFile());
				Stream<Path> paths = Files.walk(source)) {
			List<Path> files = paths.filter(Files::isRegularFile).sorted().toList();
			for (Path file : files) {
				AtomicFiles.verifyManagedPath(source, file);
				if (Files.isSymbolicLink(file)) {
					throw new IOException("Symbolic links are not allowed in datapack exports");
				}
				String name = source.relativize(file).toString().replace('\\', '/');
				output.putArchiveEntry(new ZipArchiveEntry(name));
				Files.copy(file, output);
				output.closeArchiveEntry();
			}
		}
		return normalizedTarget;
	}

	public void importZip(Path zip, String packName) throws IOException {
		Path target = packRoot(packName);
		if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
			throw new IOException("Local datapack already exists: " + packName);
		}
		Path staging = datapacksRoot.resolve(".import-" + UUID.randomUUID());
		AtomicFiles.verifyManagedPath(datapacksRoot, staging);
		try {
			Set<String> allowed = allowedZipEntries(zip);
			SafeZipExtractor.extract(zip, staging, allowed);
			validateMetadata(staging);
			Map<Identifier, WorldGenTemplate> templates = readTemplates(staging);
			Map<Identifier, BoxProfile> profiles = readProfiles(staging);
			if (templates.isEmpty() && profiles.isEmpty()) {
				throw new IOException("ZIP contains no Rogue Engine templates or profiles");
			}
			Files.createDirectories(datapacksRoot);
			Files.move(staging, target);
		} catch (IOException exception) {
			throw exception;
		} catch (RuntimeException exception) {
			throw new IOException("Invalid local datapack: " + exception.getMessage(), exception);
		} finally {
			deleteTree(staging);
		}
	}

	public Map<Identifier, WorldGenTemplate> templates() {
		try {
			return readTemplates(datapacksRoot);
		} catch (IOException exception) {
			throw new java.io.UncheckedIOException(exception);
		}
	}

	public Map<Identifier, BoxProfile> profiles() {
		try {
			return readProfiles(datapacksRoot);
		} catch (IOException exception) {
			throw new java.io.UncheckedIOException(exception);
		}
	}

	private Path packRoot(String packName) throws IOException {
		if (packName == null || !packName.matches("[a-z0-9_-]+")) {
			throw new IOException("Invalid datapack name");
		}
		Path root = datapacksRoot.resolve(packName);
		AtomicFiles.verifyManagedPath(datapacksRoot, root);
		return root;
	}

	private static Path templatePath(Path pack, Identifier id) {
		return pack.resolve("data").resolve(id.getNamespace()).resolve("rogueengine")
				.resolve("worldgen_template").resolve(id.getPath() + ".json");
	}

	private static Path profilePath(Path pack, Identifier id) {
		return pack.resolve("data").resolve(id.getNamespace()).resolve("rogueengine")
				.resolve("profile").resolve(id.getPath() + ".json");
	}

	private static Set<String> allowedZipEntries(Path zip) throws IOException {
		Set<String> allowed = new java.util.HashSet<>();
		try (java.util.zip.ZipFile archive = new java.util.zip.ZipFile(zip.toFile())) {
			var entries = archive.entries();
			while (entries.hasMoreElements()) {
				String name = entries.nextElement().getName();
				if (name.equals("pack.mcmeta") || name.matches(
						"data/[a-z0-9_.-]+/rogueengine/(worldgen_template|profile)/[a-z0-9_./-]+\\.json")) {
					allowed.add(name);
				}
			}
		}
		return allowed;
	}

	private static void validateMetadata(Path pack) throws IOException {
		Path metadata = pack.resolve("pack.mcmeta");
		if (!Files.isRegularFile(metadata, LinkOption.NOFOLLOW_LINKS)) {
			throw new IOException("Local datapack is missing pack.mcmeta");
		}
		JsonElement root = JsonParser.parseString(Files.readString(metadata, StandardCharsets.UTF_8));
		if (!(root instanceof JsonObject object) || !(object.get("pack") instanceof JsonObject packObject)
				|| packObject.get("pack_format").getAsInt() != PACK_FORMAT) {
			throw new IOException("Unsupported local datapack format");
		}
	}

	private static Map<Identifier, WorldGenTemplate> readTemplates(Path root) throws IOException {
		return readFiles(root, "worldgen_template", WorldGenTemplateCodec.CODEC);
	}

	private static Map<Identifier, BoxProfile> readProfiles(Path root) throws IOException {
		return readFiles(root, "profile", BoxProfileCodec.CODEC);
	}

	private static <T> Map<Identifier, T> readFiles(Path root, String registryFolder,
			com.mojang.serialization.Codec<T> codec) throws IOException {
		Map<Identifier, T> result = new LinkedHashMap<>();
		if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
			return result;
		}
		List<Path> packDirectories;
		if (Files.isDirectory(root.resolve("data"), LinkOption.NOFOLLOW_LINKS)) {
			packDirectories = List.of(root);
		} else {
			try (Stream<Path> packs = Files.list(root)) {
				packDirectories = packs.filter(path -> Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
						.filter(path -> !path.getFileName().toString().startsWith("."))
						.sorted(Comparator.comparing(path -> path.getFileName().toString())).toList();
			}
		}
		for (Path pack : packDirectories) {
			Path data = pack.resolve("data");
			if (!Files.isDirectory(data, LinkOption.NOFOLLOW_LINKS)) {
				continue;
			}
			try (Stream<Path> paths = Files.walk(data)) {
				List<Path> files = paths.filter(Files::isRegularFile)
						.filter(path -> path.toString().replace('\\', '/')
								.contains("/rogueengine/" + registryFolder + "/"))
						.filter(path -> path.getFileName().toString().endsWith(".json")).sorted().toList();
				for (Path file : files) {
					AtomicFiles.verifyManagedPath(pack, file);
					Identifier id = resourceId(data, file, registryFolder);
					JsonElement json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
					T value = codec.parse(JsonOps.INSTANCE, json).getOrThrow();
					result.putIfAbsent(id, value);
				}
			}
		}
		return result;
	}

	private static Identifier resourceId(Path dataRoot, Path file, String registryFolder) {
		Path relative = dataRoot.relativize(file);
		String namespace = relative.getName(0).toString();
		if (relative.getNameCount() < 4 || !"rogueengine".equals(relative.getName(1).toString())
				|| !registryFolder.equals(relative.getName(2).toString())) {
			throw new IllegalArgumentException("Invalid Rogue Engine resource path: " + relative);
		}
		String path = relative.subpath(3, relative.getNameCount()).toString().replace('\\', '/');
		path = path.substring(0, path.length() - ".json".length());
		return Identifier.fromNamespaceAndPath(namespace, path);
	}

	private static void deleteTree(Path root) throws IOException {
		if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
			return;
		}
		try (Stream<Path> paths = Files.walk(root)) {
			for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
				Files.delete(path);
			}
		}
	}
}
