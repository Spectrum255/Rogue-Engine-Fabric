package spectrum255.rogueengine.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import spectrum255.rogueengine.client.screen.AbstractConfigScreen;
import spectrum255.rogueengine.client.screen.JsonEditorScreen;
import spectrum255.rogueengine.client.screen.MapPreviewScreen;
import spectrum255.rogueengine.client.screen.ProfileFormScreen;
import spectrum255.rogueengine.client.screen.TemplateFormScreen;
import spectrum255.rogueengine.client.screen.TransferScreen;
import spectrum255.rogueengine.profile.BoxProfile;
import spectrum255.rogueengine.profile.BoxProfileCodec;
import spectrum255.rogueengine.profile.WorldGenTemplate;
import spectrum255.rogueengine.profile.WorldGenTemplateCodec;
import spectrum255.rogueengine.tool.ConfigDraft;

public final class RogueEngineConfigScreen extends AbstractConfigScreen {
	private final List<String> builtIns = List.of(
			"noise_chaos", "full_chaos", "preset_chaos", "bounded_noise",
			"central_island", "central_island_layers");
	private List<Identifier> localIds = List.of();
	private EditBox packName;
	private EditBox namespace;
	private EditBox templateId;
	private EditBox profileId;
	private EditBox libraryId;

	public RogueEngineConfigScreen(Screen parent, DraftState state) {
		super(Component.translatable("rogue-engine-fabric.config.title"), parent, state);
	}

	@Override
	protected void init() {
		localIds = state.service().templates().keySet().stream().sorted().toList();
		packName = field(20, 30, 180, "rogue-engine-fabric.config.pack_name", state.draft().packName());
		namespace = field(220, 30, 180, "rogue-engine-fabric.config.namespace", state.draft().namespace());
		templateId = field(20, 75, 180, "rogue-engine-fabric.config.template_id", state.draft().templateId().toString());
		profileId = field(220, 75, 180, "rogue-engine-fabric.config.profile_id", state.draft().profileId().toString());

		int y = 125;
		for (int i = 0; i < builtIns.size(); i++) {
			String name = builtIns.get(i);
			addButton(20, y + i * 25, 180, "rogue-engine-fabric.config.load_" + name, () -> loadBuiltIn(name));
		}
		addButton(220, y, 180, "rogue-engine-fabric.config.edit_template",
				() -> minecraft.setScreenAndShow(new TemplateFormScreen(this, state)));
		addButton(220, y + 25, 180, "rogue-engine-fabric.config.edit_profile",
				() -> minecraft.setScreenAndShow(new ProfileFormScreen(this, state)));
		addButton(220, y + 50, 180, "rogue-engine-fabric.config.edit_json",
				() -> minecraft.setScreenAndShow(new JsonEditorScreen(this, state)));
		addButton(220, y + 75, 180, "rogue-engine-fabric.config.transfer",
				() -> minecraft.setScreenAndShow(new TransferScreen(this, state)));
		addButton(220, y + 100, 180, "rogue-engine-fabric.config.apply_ids", this::applyIds);
		addButton(220, y + 125, 180, "rogue-engine-fabric.config.save", this::save);

		libraryId = field(20, 300, 250, "rogue-engine-fabric.config.library_id", "");
		addButton(280, 312, 120, "rogue-engine-fabric.config.load_local", this::loadLocal);
		addButton(20, 350, 180, "rogue-engine-fabric.config.refresh", () -> {
			localIds = state.service().templates().keySet().stream().sorted().toList();
			setStatus("rogue-engine-fabric.config.refreshed");
		});
		addButton(220, 350, 180, "rogue-engine-fabric.config.preview",
				() -> minecraft.setScreenAndShow(new MapPreviewScreen(this, state)));
		addButton(width / 2 - 100, height - 50, 200, "gui.done", this::onClose);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		graphics.text(font, Component.translatable("rogue-engine-fabric.config.local_library"), 420, 120, 0xFFFFFF);
		for (int i = 0; i < Math.min(localIds.size(), 12); i++) {
			graphics.text(font, localIds.get(i).toString(), 420, 136 + i * 12, 0xCCCCCC);
		}
	}

	private void applyIds() {
		try {
			ConfigDraft draft = state.draft();
			Identifier newTemplateId = Identifier.parse(templateId.getValue().trim());
			Identifier newProfileId = Identifier.parse(profileId.getValue().trim());
			String newNamespace = namespace.getValue().trim();
			BoxProfile.TemplateGenerator generator = (BoxProfile.TemplateGenerator) draft.profile().generator();
			BoxProfile profile = new BoxProfile(draft.profile().schemaVersion(), draft.profile().dimensionType(),
					new BoxProfile.TemplateGenerator("template", newTemplateId, generator.overrides(), generator.preset()),
					draft.profile().seed(), draft.profile().spawn(), draft.profile().border(), draft.profile().inventory());
			state.draft(new ConfigDraft(packName.getValue().trim(), newNamespace, newTemplateId,
					newProfileId, draft.template(), profile));
			setStatus("rogue-engine-fabric.config.applied");
		} catch (RuntimeException exception) {
			setStatusMessage(Component.literal(exception.getMessage()));
		}
	}

	private void save() {
		applyIds();
		try {
			ConfigDraft draft = state.draft();
			state.service().save(draft);
			localIds = state.service().templates().keySet().stream().sorted().toList();
			setStatus("rogue-engine-fabric.config.saved", draft.packName());
		} catch (IOException | RuntimeException exception) {
			setStatusMessage(Component.literal(exception.getMessage()));
		}
	}

	private void loadBuiltIn(String name) {
		try {
			WorldGenTemplate template = read("/data/rogue-engine-fabric/rogue-engine-fabric/worldgen_template/" + name + ".json",
					WorldGenTemplateCodec.CODEC);
			BoxProfile builtInProfile = read("/data/rogue-engine-fabric/rogue-engine-fabric/profile/" + name + ".json",
					BoxProfileCodec.CODEC);
			ConfigDraft current = state.draft();
			BoxProfile.TemplateGenerator generator = (BoxProfile.TemplateGenerator) builtInProfile.generator();
			BoxProfile profile = new BoxProfile(builtInProfile.schemaVersion(), builtInProfile.dimensionType(),
					new BoxProfile.TemplateGenerator("template", current.templateId(), generator.overrides(), generator.preset()),
					builtInProfile.seed(), builtInProfile.spawn(), builtInProfile.border(), builtInProfile.inventory());
			state.draft(new ConfigDraft(current.packName(), current.namespace(), current.templateId(),
					current.profileId(), template, profile));
			packName.setValue(current.packName());
			namespace.setValue(current.namespace());
			templateId.setValue(current.templateId().toString());
			profileId.setValue(current.profileId().toString());
			setStatus("rogue-engine-fabric.config.loaded", name);
		} catch (IOException | RuntimeException exception) {
			setStatusMessage(Component.literal(exception.getMessage()));
		}
	}

	private void loadLocal() {
		try {
			Identifier id = Identifier.parse(libraryId.getValue().trim());
			WorldGenTemplate template = state.service().templates().get(id);
			if (template == null) {
				throw new IllegalArgumentException("Unknown local template: " + id);
			}
			BoxProfile profile = state.service().profiles().get(id);
			if (profile == null) {
				profile = ConfigDraft.defaultDraft().profile();
			}
			BoxProfile.TemplateGenerator generator = (BoxProfile.TemplateGenerator) profile.generator();
			profile = new BoxProfile(profile.schemaVersion(), profile.dimensionType(),
					new BoxProfile.TemplateGenerator("template", id, generator.overrides(), generator.preset()),
					profile.seed(), profile.spawn(), profile.border(), profile.inventory());
			String pack = packName.getValue().trim();
			state.draft(new ConfigDraft(pack, id.getNamespace(), id, id, template, profile));
			namespace.setValue(id.getNamespace());
			templateId.setValue(id.toString());
			profileId.setValue(id.toString());
			setStatus("rogue-engine-fabric.config.loaded", id.toString());
		} catch (RuntimeException exception) {
			setStatusMessage(Component.literal(exception.getMessage()));
		}
	}

	private static <T> T read(String path, com.mojang.serialization.Codec<T> codec) throws IOException {
		try (InputStream input = RogueEngineConfigScreen.class.getResourceAsStream(path)) {
			if (input == null) {
				throw new IOException("Missing built-in resource: " + path);
			}
			return codec.parse(JsonOps.INSTANCE, JsonParser.parseReader(
					new InputStreamReader(input, StandardCharsets.UTF_8))).getOrThrow();
		}
	}
}
