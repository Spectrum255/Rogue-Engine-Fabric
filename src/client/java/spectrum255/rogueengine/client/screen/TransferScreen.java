package spectrum255.rogueengine.client.screen;

import java.io.IOException;
import java.nio.file.Path;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import spectrum255.rogueengine.client.DraftState;

public final class TransferScreen extends AbstractConfigScreen {
	private EditBox packName;
	private EditBox zipPath;

	public TransferScreen(Screen parent, DraftState state) {
		super(Component.translatable("rogue-engine-fabric.config.transfer"), parent, state);
	}

	@Override
	protected void init() {
		packName = field(20, 40, 220, "rogue-engine-fabric.config.pack_name", state.draft().packName());
		zipPath = field(20, 100, 360, "rogue-engine-fabric.config.zip_path", "rogue-engine-export.zip");
		addButton(20, 160, 160, "rogue-engine-fabric.config.import_zip", this::importZip);
		addButton(200, 160, 160, "rogue-engine-fabric.config.export_zip", this::exportZip);
		addButton(width / 2 - 100, height - 50, 200, "gui.done", this::onClose);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		graphics.textWithWordWrap(font, Component.translatable("rogue-engine-fabric.config.dedicated_hint"),
				20, 205, width - 40, 12);
	}

	private void importZip() {
		try {
			state.service().importZip(resolvePath(), packName.getValue().trim());
			setStatus("rogue-engine-fabric.config.imported", packName.getValue().trim());
		} catch (IOException | RuntimeException exception) {
			setStatusMessage(Component.literal(exception.getMessage()));
		}
	}

	private void exportZip() {
		try {
			Path target = state.service().exportZip(packName.getValue().trim(), resolvePath());
			setStatus("rogue-engine-fabric.config.exported", target.toString());
		} catch (IOException | RuntimeException exception) {
			setStatusMessage(Component.literal(exception.getMessage()));
		}
	}

	private Path resolvePath() {
		Path path = Path.of(zipPath.getValue().trim());
		return path.isAbsolute() ? path : minecraft.gameDirectory.toPath().resolve(path);
	}
}
