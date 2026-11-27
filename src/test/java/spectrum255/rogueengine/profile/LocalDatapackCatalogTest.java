package spectrum255.rogueengine.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.mojang.serialization.Lifecycle;

import net.minecraft.SharedConstants;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.server.Bootstrap;

import spectrum255.rogueengine.tool.ConfigDraft;
import spectrum255.rogueengine.tool.DatapackAuthoringService;

class LocalDatapackCatalogTest {
	@TempDir
	java.nio.file.Path temp;

	@BeforeAll
	static void bootstrapMinecraft() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	void localPackIdsAreVisibleWhenRegistryMisses() throws IOException {
		DatapackAuthoringService service = new DatapackAuthoringService(temp.resolve("datapacks"));
		ConfigDraft draft = ConfigDraft.defaultDraft();
		service.save(draft);
		Registry<WorldGenTemplate> templates = new MappedRegistry<>(
				WorldGenTemplateCatalog.REGISTRY_KEY, Lifecycle.stable()).freeze();
		Registry<BoxProfile> profiles = new MappedRegistry<>(
				ProfileCatalog.REGISTRY_KEY, Lifecycle.stable()).freeze();
		WorldGenTemplateCatalog templateCatalog = new WorldGenTemplateCatalog(templates, service);
		ProfileCatalog profileCatalog = new ProfileCatalog(profiles, service);

		assertTrue(templateCatalog.ids().contains(draft.templateId()));
		assertTrue(profileCatalog.ids().contains(draft.profileId()));
		assertEquals(draft.template(), templateCatalog.get(draft.templateId()).orElseThrow());
		assertEquals(draft.profile(), profileCatalog.get(draft.profileId()).orElseThrow());
	}
}
