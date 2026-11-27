package spectrum255.rogueengine.client.screen;

import java.util.List;
import java.util.Optional;

import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import spectrum255.rogueengine.client.DraftState;
import spectrum255.rogueengine.profile.CentralIslandSettings;
import spectrum255.rogueengine.profile.TerrainBoundary;
import spectrum255.rogueengine.profile.WorldGenTemplate;
import spectrum255.rogueengine.tool.ConfigDraft;

public final class TemplateFormScreen extends AbstractConfigScreen {
	private static final List<String> KINDS = List.of(
			"noise_chaos", "full_chaos", "preset_chaos", "central_island");
	private final WorldGenTemplate template;
	private int kindIndex;
	private boolean boundaryEnabled;
	private boolean islandEnabled;
	private TerrainBoundary.Shape shape;
	private TerrainBoundary.OutsideMode outsideMode;
	private CentralIslandSettings.SurfaceMode surfaceMode;
	private EditBox centerX;
	private EditBox centerZ;
	private EditBox radiusX;
	private EditBox radiusZ;
	private EditBox centerY;
	private EditBox thickness;
	private EditBox taper;
	private EditBox roughness;
	private EditBox edgeNoise;

	public TemplateFormScreen(Screen parent, DraftState state) {
		super(Component.translatable("rogueengine.config.edit_template"), parent, state);
		this.template = state.draft().template();
		this.kindIndex = Math.max(0, KINDS.indexOf(template.generatorKind()));
		this.boundaryEnabled = template.terrainBoundary() != null;
		this.islandEnabled = template.island() != null;
		this.shape = template.terrainBoundary() == null ? TerrainBoundary.Shape.CIRCLE : template.terrainBoundary().shape();
		this.outsideMode = template.terrainBoundary() == null
				? TerrainBoundary.OutsideMode.VOID : template.terrainBoundary().outsideMode();
		this.surfaceMode = template.island() == null
				? CentralIslandSettings.SurfaceMode.VANILLA_SURFACE : template.island().surfaceMode();
	}

	@Override
	protected void init() {
		addButton(20, 30, 180, "rogueengine.config.generator_kind", () -> {
			kindIndex = (kindIndex + 1) % KINDS.size();
			setStatus("rogueengine.config.selected", KINDS.get(kindIndex));
		});
		addButton(20, 58, 180, "rogueengine.config.boundary_toggle", () -> boundaryEnabled = !boundaryEnabled);
		centerX = field(20, 90, 180, "rogueengine.config.center_x", boundaryValue(template.terrainBoundary() == null ? 0 : template.terrainBoundary().centerX()));
		centerZ = field(20, 125, 180, "rogueengine.config.center_z", boundaryValue(template.terrainBoundary() == null ? 0 : template.terrainBoundary().centerZ()));
		radiusX = field(20, 160, 180, "rogueengine.config.radius_x", boundaryValue(template.terrainBoundary() == null ? 128 : template.terrainBoundary().radiusX()));
		radiusZ = field(20, 195, 180, "rogueengine.config.radius_z", boundaryValue(template.terrainBoundary() == null ? 128 : template.terrainBoundary().radiusZ()));
		addButton(20, 230, 180, "rogueengine.config.shape", () -> shape = shape == TerrainBoundary.Shape.CIRCLE
				? TerrainBoundary.Shape.ELLIPSE : TerrainBoundary.Shape.CIRCLE);
		addButton(20, 255, 180, "rogueengine.config.outside_mode", () -> outsideMode = outsideMode == TerrainBoundary.OutsideMode.VOID
				? TerrainBoundary.OutsideMode.BEDROCK_FLOOR : TerrainBoundary.OutsideMode.VOID);

		addButton(220, 30, 180, "rogueengine.config.island_toggle", () -> islandEnabled = !islandEnabled);
		centerY = field(220, 65, 180, "rogueengine.config.center_y", Integer.toString(template.island() == null ? 128 : template.island().centerY()));
		thickness = field(220, 100, 180, "rogueengine.config.thickness", Integer.toString(template.island() == null ? 64 : template.island().maxThickness()));
		taper = field(220, 135, 180, "rogueengine.config.bottom_taper", boundaryValue(template.island() == null ? 1.5 : template.island().bottomTaper()));
		roughness = field(220, 170, 180, "rogueengine.config.surface_roughness", boundaryValue(template.island() == null ? 4 : template.island().surfaceRoughness()));
		edgeNoise = field(220, 205, 180, "rogueengine.config.edge_noise", boundaryValue(template.island() == null ? 0.35 : template.island().edgeNoise()));
		addButton(220, 240, 180, "rogueengine.config.surface_mode", () -> surfaceMode = surfaceMode == CentralIslandSettings.SurfaceMode.VANILLA_SURFACE
				? CentralIslandSettings.SurfaceMode.CUSTOM_LAYERS : CentralIslandSettings.SurfaceMode.VANILLA_SURFACE);

		addButton(20, 310, 180, "gui.apply", this::apply);
		addButton(width / 2 - 100, height - 50, 200, "gui.done", this::onClose);
	}

	private void apply() {
		try {
			TerrainBoundary boundary = boundaryEnabled ? new TerrainBoundary(number(centerX), number(centerZ),
					number(radiusX), number(radiusZ), shape, outsideMode) : null;
			CentralIslandSettings island = islandEnabled ? new CentralIslandSettings(surfaceMode,
					integer(centerY), integer(thickness), number(taper), number(roughness), number(edgeNoise),
					template.island() == null ? Optional.empty() : template.island().customLayers()) : null;
			WorldGenTemplate updated = new WorldGenTemplate(template.schemaVersion(), KINDS.get(kindIndex),
					template.baseStem(), template.ranges(), template.presets(), template.mapPoolId(),
					template.defaultSelection(), boundary, island);
			ConfigDraft draft = state.draft();
			state.draft(new ConfigDraft(draft.packName(), draft.namespace(), draft.templateId(), draft.profileId(),
					updated, draft.profile()));
			setStatus("rogueengine.config.applied");
		} catch (RuntimeException exception) {
			setStatusMessage(Component.literal(exception.getMessage()));
		}
	}

	private static String boundaryValue(double value) {
		return Double.toString(value);
	}
}
