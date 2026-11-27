package spectrum255.rogueengine.map;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

public final class StructureScenePlacer {
	private static final int MAX_NBT_BYTES = 16 * 1024 * 1024;

	private StructureScenePlacer() {
	}

	public static BoundingBox boundingBox(StructureTemplate template, MapScene scene, BlockPos origin) {
		Objects.requireNonNull(template, "template");
		Objects.requireNonNull(scene, "scene");
		Objects.requireNonNull(origin, "origin");
		BoundingBox box = boundingBox(template, scene.rotation(), scene.mirror(), origin);
		BoundingBox allowed = new BoundingBox(
				scene.bounds().min().getX(), scene.bounds().min().getY(), scene.bounds().min().getZ(),
				scene.bounds().max().getX(), scene.bounds().max().getY(), scene.bounds().max().getZ());
		if (box.minX() < allowed.minX() || box.minY() < allowed.minY() || box.minZ() < allowed.minZ()
				|| box.maxX() > allowed.maxX() || box.maxY() > allowed.maxY() || box.maxZ() > allowed.maxZ()) {
			throw new IllegalArgumentException("Structure placement is outside the map scene bounds: " + box + " allowed=" + allowed);
		}
		return box;
	}

	public static BoundingBox boundingBox(StructureTemplate template, Rotation rotation, Mirror mirror, BlockPos origin) {
		Objects.requireNonNull(template, "template");
		Objects.requireNonNull(rotation, "rotation");
		Objects.requireNonNull(mirror, "mirror");
		Objects.requireNonNull(origin, "origin");
		StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(rotation).setMirror(mirror);
		return template.getBoundingBox(settings, origin);
	}

	public static PlacementResult place(ServerLevel level, Path source, MapScene scene, BlockPos origin) throws IOException {
		Objects.requireNonNull(level, "level");
		Objects.requireNonNull(source, "source");
		Objects.requireNonNull(scene, "scene");
		Objects.requireNonNull(origin, "origin");
		CompoundTag tag = NbtIo.readCompressed(source, NbtAccounter.create(MAX_NBT_BYTES));
		StructureTemplate template = new StructureTemplate();
		template.load(level.registryAccess().lookupOrThrow(Registries.BLOCK), tag);
		BoundingBox box = boundingBox(template, scene, origin);
		StructurePlaceSettings settings = settings(scene);
		var processorLists = level.registryAccess().lookupOrThrow(Registries.PROCESSOR_LIST);
		for (var processorId : scene.processorIds()) {
			processorLists.getOrThrow(ResourceKey.create(Registries.PROCESSOR_LIST, processorId))
					.value().list().forEach(settings::addProcessor);
		}
		template.placeInWorld(level, origin, origin, settings, RandomSource.create(0L), 3);
		return new PlacementResult(box, scene.spawn());
	}

	private static StructurePlaceSettings settings(MapScene scene) {
		return new StructurePlaceSettings()
				.setRotation(scene.rotation())
				.setMirror(scene.mirror())
				.setIgnoreEntities(false)
				.setFinalizeEntities(true);
	}

	public record PlacementResult(BoundingBox bounds, BlockPos spawn) {
	}
}
