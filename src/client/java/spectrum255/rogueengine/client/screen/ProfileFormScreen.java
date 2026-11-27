package spectrum255.rogueengine.client.screen;

import java.util.Optional;

import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import spectrum255.rogueengine.client.DraftState;
import spectrum255.rogueengine.profile.BoxProfile;
import spectrum255.rogueengine.tool.ConfigDraft;

public final class ProfileFormScreen extends AbstractConfigScreen {
	private final BoxProfile profile;
	private boolean fixedSeed;
	private boolean fixedSpawn;
	private EditBox seed;
	private EditBox radius;
	private EditBox x;
	private EditBox y;
	private EditBox z;
	private EditBox yaw;
	private EditBox borderX;
	private EditBox borderZ;
	private EditBox borderSize;
	private EditBox warning;

	public ProfileFormScreen(Screen parent, DraftState state) {
		super(Component.translatable("rogueengine.config.edit_profile"), parent, state);
		this.profile = state.draft().profile();
		this.fixedSeed = profile.seed().mode().equals("fixed");
		this.fixedSpawn = profile.spawn() instanceof BoxProfile.FixedSpawn;
	}

	@Override
	protected void init() {
		addButton(20, 30, 180, "rogueengine.config.seed_mode", () -> fixedSeed = !fixedSeed);
		seed = field(20, 65, 180, "rogueengine.config.seed_value",
				profile.seed().value().map(Object::toString).orElse(""));
		addButton(20, 105, 180, "rogueengine.config.spawn_mode", () -> fixedSpawn = !fixedSpawn);
		radius = field(20, 140, 180, "rogueengine.config.search_radius",
				Integer.toString(profile.spawn() instanceof BoxProfile.SearchSpawn search ? search.radius() : 32));
		x = field(20, 175, 180, "rogueengine.config.spawn_x",
				Double.toString(profile.spawn() instanceof BoxProfile.FixedSpawn fixed ? fixed.x() : 0));
		y = field(20, 210, 180, "rogueengine.config.spawn_y",
				Double.toString(profile.spawn() instanceof BoxProfile.FixedSpawn fixed ? fixed.y() : 129));
		z = field(20, 245, 180, "rogueengine.config.spawn_z",
				Double.toString(profile.spawn() instanceof BoxProfile.FixedSpawn fixed ? fixed.z() : 0));
		yaw = field(20, 280, 180, "rogueengine.config.yaw",
				Float.toString(profile.spawn() instanceof BoxProfile.FixedSpawn fixed ? fixed.yaw() : 0));

		borderX = field(220, 65, 180, "rogueengine.config.border_x", Double.toString(profile.border().centerX()));
		borderZ = field(220, 100, 180, "rogueengine.config.border_z", Double.toString(profile.border().centerZ()));
		borderSize = field(220, 135, 180, "rogueengine.config.border_size", Double.toString(profile.border().initialSize()));
		warning = field(220, 170, 180, "rogueengine.config.warning_blocks", Double.toString(profile.border().warningBlocks()));

		addButton(20, 330, 180, "gui.apply", this::apply);
		addButton(width / 2 - 100, height - 50, 200, "gui.done", this::onClose);
	}

	private void apply() {
		try {
			BoxProfile.Seed newSeed = fixedSeed
					? new BoxProfile.Seed("fixed", Optional.of(longNumber(seed)))
					: new BoxProfile.Seed("random", Optional.empty());
			BoxProfile.Spawn newSpawn = fixedSpawn
					? new BoxProfile.FixedSpawn("fixed", number(x), number(y), number(z), floatNumber(yaw))
					: new BoxProfile.SearchSpawn("search", integer(radius));
			BoxProfile updated = new BoxProfile(profile.schemaVersion(), profile.dimensionType(), profile.generator(),
					newSeed, newSpawn, new BoxProfile.Border(number(borderX), number(borderZ),
							number(borderSize), number(warning)), profile.inventory());
			ConfigDraft draft = state.draft();
			state.draft(new ConfigDraft(draft.packName(), draft.namespace(), draft.templateId(), draft.profileId(),
					draft.template(), updated));
			setStatus("rogueengine.config.applied");
		} catch (RuntimeException exception) {
			setStatusMessage(Component.literal(exception.getMessage()));
		}
	}
}
