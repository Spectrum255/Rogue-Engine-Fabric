package spectrum255.rogueengine.client;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.storage.LevelResource;

import spectrum255.rogueengine.map.MapPreviewModel;
import spectrum255.rogueengine.map.MapSnapshotCatalog;
import spectrum255.rogueengine.mixin.MinecraftServerAccessor;

public final class MapPreviewLoader {
	private static final int MAX_SOURCE_BYTES = 16 * 1024 * 1024;
	private static final int MAX_NBT_BYTES = 64 * 1024 * 1024;

	private MapPreviewLoader() {
	}

	public static MapPreviewModel loadSnapshot(Identifier id, Rotation rotation, Mirror mirror) throws IOException {
		Minecraft minecraft = Minecraft.getInstance();
		Path worldRoot;
		if (minecraft.hasSingleplayerServer()) {
			worldRoot = ((MinecraftServerAccessor) minecraft.getSingleplayerServer()).getStorageSource()
					.getLevelPath(LevelResource.ROOT);
		} else {
			worldRoot = minecraft.gameDirectory.toPath().resolve(".rogue-engine-preview-empty");
		}
		RegistryAccess registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
		MapSnapshotCatalog catalog = new MapSnapshotCatalog(worldRoot, registries,
				resourceId -> readResource(snapshotResourceId(resourceId)));
		MapSnapshotCatalog.Loaded loaded = catalog.load(id)
				.orElseThrow(() -> new IOException("Map snapshot not found: " + id));
		return MapPreviewModel.fromSnapshot(loaded.snapshot(), rotation, mirror);
	}

	public static MapPreviewModel loadStructure(Identifier id, Rotation rotation, Mirror mirror) throws IOException {
		byte[] bytes = readResource(structureResourceId(id))
				.orElseThrow(() -> new IOException("Structure not found: " + id));
		CompoundTag tag = NbtIo.readCompressed(new ByteArrayInputStream(bytes), NbtAccounter.create(MAX_NBT_BYTES));
		StructureTemplate template = new StructureTemplate();
		template.load(BuiltInRegistries.BLOCK, tag);
		List<MapPreviewModel.PreviewBlock> blocks = new ArrayList<>();
		template.filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), Blocks.AIR, false)
				.forEach(block -> blocks.add(new MapPreviewModel.PreviewBlock(block.pos(), block.state())));
		return MapPreviewModel.fromBlocks(template.getSize(), blocks, rotation, mirror);
	}

	private static Identifier snapshotResourceId(Identifier id) {
		return Identifier.fromNamespaceAndPath(id.getNamespace(),
				"rogueengine/map_snapshot/" + id.getPath() + ".nbt");
	}

	private static Identifier structureResourceId(Identifier id) {
		return Identifier.fromNamespaceAndPath(id.getNamespace(), "structure/" + id.getPath() + ".nbt");
	}

	private static Optional<byte[]> readResource(Identifier id) {
		Minecraft minecraft = Minecraft.getInstance();
		Optional<Resource> resource = minecraft.getResourceManager().getResource(id);
		if (resource.isEmpty()) {
			return Optional.empty();
		}
		try (InputStream input = resource.get().open()) {
			byte[] bytes = input.readNBytes(MAX_SOURCE_BYTES + 1);
			if (bytes.length > MAX_SOURCE_BYTES) {
				throw new IOException("Preview source is too large: " + id);
			}
			return Optional.of(bytes);
		} catch (IOException exception) {
			throw new UncheckedIOException(exception);
		}
	}
}
