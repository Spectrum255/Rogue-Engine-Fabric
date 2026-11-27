package spectrum255.rogueengine.client.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

import spectrum255.rogueengine.client.DraftState;
import spectrum255.rogueengine.client.MapPreviewLoader;
import spectrum255.rogueengine.map.MapPreviewModel;

public final class MapPreviewScreen extends AbstractConfigScreen {
	private static final Rotation[] ROTATIONS = {
			Rotation.NONE, Rotation.CLOCKWISE_90, Rotation.CLOCKWISE_180, Rotation.COUNTERCLOCKWISE_90
	};
	private static final Mirror[] MIRRORS = { Mirror.NONE, Mirror.LEFT_RIGHT, Mirror.FRONT_BACK };

	private Source source = Source.SNAPSHOT;
	private int rotationIndex;
	private int mirrorIndex;
	private EditBox mapId;
	private MapPreviewModel model;

	public MapPreviewScreen(Screen parent, DraftState state) {
		super(Component.translatable("rogue-engine-fabric.config.preview"), parent, state);
	}

	@Override
	protected void init() {
		mapId = field(20, 35, 360, "rogue-engine-fabric.config.map_id", "rogue-engine-fabric:example_room");
		addButton(20, 80, 160, "rogue-engine-fabric.config.source", () -> {
			source = source == Source.SNAPSHOT ? Source.STRUCTURE : Source.SNAPSHOT;
			setStatus("rogue-engine-fabric.config.selected", source.name().toLowerCase(java.util.Locale.ROOT));
		});
		addButton(200, 80, 160, "rogue-engine-fabric.config.rotation", () -> {
			rotationIndex = (rotationIndex + 1) % ROTATIONS.length;
			setStatus("rogue-engine-fabric.config.selected", ROTATIONS[rotationIndex].name().toLowerCase(java.util.Locale.ROOT));
		});
		addButton(20, 110, 160, "rogue-engine-fabric.config.mirror", () -> {
			mirrorIndex = (mirrorIndex + 1) % MIRRORS.length;
			setStatus("rogue-engine-fabric.config.selected", MIRRORS[mirrorIndex].name().toLowerCase(java.util.Locale.ROOT));
		});
		addButton(200, 110, 160, "rogue-engine-fabric.config.load", this::load);
		addButton(width / 2 - 100, height - 50, 200, "gui.done", this::onClose);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		if (model == null) {
			graphics.centeredText(font, Component.translatable("rogue-engine-fabric.config.preview_empty"),
					width / 2, 160, 0xAAAAAA);
			return;
		}
		int availableWidth = Math.max(1, width - 40);
		int availableHeight = Math.max(1, height - 205);
		int columns = Math.min(model.width(), availableWidth);
		int rows = Math.min(model.depth(), availableHeight);
		int stepX = Math.max(1, model.width() / columns);
		int stepZ = Math.max(1, model.depth() / rows);
		int cell = Math.max(1, Math.min(availableWidth / columns, availableHeight / rows));
		int drawWidth = columns * cell;
		int drawHeight = rows * cell;
		int startX = (width - drawWidth) / 2;
		int startY = 155 + (availableHeight - drawHeight) / 2;
		for (int row = 0; row < rows; row++) {
			for (int column = 0; column < columns; column++) {
				int color = model.colorAt(column * stepX, row * stepZ);
				int x = startX + column * cell;
				int y = startY + row * cell;
				graphics.fill(x, y, x + cell, y + cell, color == 0 ? 0xFF202020 : color);
			}
		}
	}

	private void load() {
		try {
			Identifier id = Identifier.parse(mapId.getValue().trim());
			Rotation rotation = ROTATIONS[rotationIndex];
			Mirror mirror = MIRRORS[mirrorIndex];
			model = source == Source.SNAPSHOT
					? MapPreviewLoader.loadSnapshot(id, rotation, mirror)
					: MapPreviewLoader.loadStructure(id, rotation, mirror);
			setStatus("rogue-engine-fabric.config.preview_loaded", model.width(), model.depth());
		} catch (Exception exception) {
			model = null;
			setStatusMessage(Component.literal(exception.getMessage()));
		}
	}

	private enum Source {
		SNAPSHOT,
		STRUCTURE
	}
}
