package spectrum255.rogueengine.client.screen;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import spectrum255.rogueengine.client.DraftState;
import spectrum255.rogueengine.profile.BoxProfile;
import spectrum255.rogueengine.profile.BoxProfileCodec;
import spectrum255.rogueengine.profile.WorldGenTemplate;
import spectrum255.rogueengine.profile.WorldGenTemplateCodec;
import spectrum255.rogueengine.tool.ConfigDraft;

public final class JsonEditorScreen extends AbstractConfigScreen {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private EditBox templateJson;
	private EditBox profileJson;

	public JsonEditorScreen(Screen parent, DraftState state) {
		super(Component.translatable("rogue-engine-fabric.config.edit_json"), parent, state);
	}

	@Override
	protected void init() {
		templateJson = jsonField(40, "rogue-engine-fabric.config.template_json",
				GSON.toJson(WorldGenTemplateCodec.CODEC.encodeStart(JsonOps.INSTANCE, state.draft().template()).getOrThrow()));
		profileJson = jsonField(190, "rogue-engine-fabric.config.profile_json",
				GSON.toJson(BoxProfileCodec.CODEC.encodeStart(JsonOps.INSTANCE, state.draft().profile()).getOrThrow()));
		addButton(20, 340, 180, "gui.apply", this::apply);
		addButton(width / 2 - 100, height - 50, 200, "gui.done", this::onClose);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		graphics.text(font, Component.translatable("rogue-engine-fabric.config.template_json"), 20, 28, 0xFFFFFF);
		graphics.text(font, Component.translatable("rogue-engine-fabric.config.profile_json"), 20, 178, 0xFFFFFF);
	}

	private EditBox jsonField(int y, String key, String value) {
		EditBox box = new EditBox(font, 20, y, width - 40, 120, Component.translatable(key));
		box.setValue(value);
		box.setMaxLength(64 * 1024);
		addRenderableWidget(box);
		return box;
	}

	private void apply() {
		try {
			WorldGenTemplate template = WorldGenTemplateCodec.CODEC.parse(JsonOps.INSTANCE,
					JsonParser.parseString(templateJson.getValue())).getOrThrow();
			BoxProfile profile = BoxProfileCodec.CODEC.parse(JsonOps.INSTANCE,
					JsonParser.parseString(profileJson.getValue())).getOrThrow();
			ConfigDraft draft = state.draft();
			ConfigDraft updated = new ConfigDraft(draft.packName(), draft.namespace(), draft.templateId(),
					draft.profileId(), template, profile);
			updated.validate().getOrThrow();
			state.draft(updated);
			setStatus("rogue-engine-fabric.config.applied");
		} catch (RuntimeException exception) {
			setStatusMessage(Component.literal(exception.getMessage()));
		}
	}
}
