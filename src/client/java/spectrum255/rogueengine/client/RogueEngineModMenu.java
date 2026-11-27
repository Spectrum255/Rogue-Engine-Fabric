package spectrum255.rogueengine.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

import net.minecraft.client.gui.screens.Screen;

import spectrum255.rogueengine.tool.ConfigDraft;
import spectrum255.rogueengine.tool.DatapackAuthoringService;

public final class RogueEngineModMenu implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return parent -> new RogueEngineConfigScreen(parent,
				new DraftState(new DatapackAuthoringService(DatapackAuthoringService.configRoot()),
						ConfigDraft.defaultDraft()));
	}
}
