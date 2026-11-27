package spectrum255.rogueengine.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

class DatapackAuthoringServiceTest {
	@TempDir
	Path temp;

	@BeforeAll
	static void bootstrapMinecraft() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	void savesValidatedTemplateAndProfileAtomically() throws IOException {
		DatapackAuthoringService service = new DatapackAuthoringService(temp.resolve("datapacks"));
		ConfigDraft draft = ConfigDraft.defaultDraft();

		Path pack = service.save(draft);

		assertTrue(Files.isRegularFile(pack.resolve("pack.mcmeta")));
		assertTrue(Files.isRegularFile(pack.resolve(
				"data/example/rogue-engine-fabric/worldgen_template/my_world.json")));
		assertTrue(Files.isRegularFile(pack.resolve(
				"data/example/rogue-engine-fabric/profile/my_world.json")));
		assertEquals(draft.template(), service.templates().get(draft.templateId()));
		assertEquals(draft.profile(), service.profiles().get(draft.profileId()));
	}

	@Test
	void exportsAndImportsEquivalentPacks() throws IOException {
		DatapackAuthoringService service = new DatapackAuthoringService(temp.resolve("datapacks"));
		ConfigDraft draft = ConfigDraft.defaultDraft();
		service.save(draft);
		Path zip = temp.resolve("export.zip");

		service.exportZip(draft.packName(), zip);
		DatapackAuthoringService imported = new DatapackAuthoringService(temp.resolve("imported"));
		imported.importZip(zip, "copy_pack");

		assertEquals(draft.template(), imported.templates().get(draft.templateId()));
		assertEquals(draft.profile(), imported.profiles().get(draft.profileId()));
	}

	@Test
	void rejectsTraversalInvalidJsonAndExistingPack() throws IOException {
		DatapackAuthoringService service = new DatapackAuthoringService(temp.resolve("datapacks"));
		Path traversal = temp.resolve("traversal.zip");
		try (ZipArchiveOutputStream out = new ZipArchiveOutputStream(traversal.toFile())) {
			entry(out, "../escape.txt", "bad");
		}
		assertThrows(IOException.class, () -> service.importZip(traversal, "bad_pack"));
		assertFalse(Files.exists(temp.resolve("escape.txt")));

		Path invalid = temp.resolve("invalid.zip");
		try (ZipArchiveOutputStream out = new ZipArchiveOutputStream(invalid.toFile())) {
			entry(out, "pack.mcmeta", "{\"pack\":{\"pack_format\":121}}");
			entry(out, "data/example/rogue-engine-fabric/worldgen_template/broken.json", "{}");
		}
		assertThrows(IOException.class, () -> service.importZip(invalid, "invalid_pack"));
		assertFalse(Files.exists(temp.resolve("datapacks/invalid_pack")));

		ConfigDraft draft = ConfigDraft.defaultDraft();
		service.save(draft);
		Path validZip = temp.resolve("valid.zip");
		service.exportZip(draft.packName(), validZip);
		assertThrows(IOException.class, () -> service.importZip(validZip, draft.packName()));
	}

	@Test
	void rejectsUnsafeDraftNames() {
		ConfigDraft draft = ConfigDraft.defaultDraft();
		ConfigDraft invalid = new ConfigDraft("../bad", draft.namespace(), draft.templateId(),
				draft.profileId(), draft.template(), draft.profile());
		assertTrue(invalid.validate().result().isEmpty());
	}

	private static void entry(ZipArchiveOutputStream out, String name, String content) throws IOException {
		out.putArchiveEntry(new ZipArchiveEntry(name));
		out.write(content.getBytes(StandardCharsets.UTF_8));
		out.closeArchiveEntry();
	}
}
