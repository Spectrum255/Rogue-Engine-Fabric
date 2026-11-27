package spectrum255.rogueengine.client.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import spectrum255.rogueengine.client.DraftState;

public abstract class AbstractConfigScreen extends Screen {
	protected final Screen parent;
	protected final DraftState state;
	private Component status = Component.literal("");

	protected AbstractConfigScreen(Component title, Screen parent, DraftState state) {
		super(title);
		this.parent = parent;
		this.state = state;
	}

	protected void addButton(int x, int y, int width, String key, Runnable action) {
		addRenderableWidget(Button.builder(Component.translatable(key), button -> action.run())
				.bounds(x, y, width, 20).build());
	}

	protected EditBox field(int x, int y, int width, String key, String value) {
		EditBox box = new EditBox(font, x, y + 12, width, 20, Component.translatable(key));
		box.setValue(value);
		box.setMaxLength(512);
		addRenderableWidget(box);
		return box;
	}

	protected void setStatus(String key, Object... arguments) {
		status = Component.translatable(key, arguments);
	}

	protected void setStatusMessage(Component message) {
		status = message;
	}

	protected static double number(EditBox box) {
		return Double.parseDouble(box.getValue().trim());
	}

	protected static int integer(EditBox box) {
		return Integer.parseInt(box.getValue().trim());
	}

	protected static long longNumber(EditBox box) {
		return Long.parseLong(box.getValue().trim());
	}

	protected static float floatNumber(EditBox box) {
		return Float.parseFloat(box.getValue().trim());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		graphics.centeredText(font, title, width / 2, 10, 0xFFFFFF);
		graphics.text(font, status, 10, height - 28, 0xFFFFFF);
	}

	@Override
	public void onClose() {
		minecraft.setScreenAndShow(parent);
	}
}
