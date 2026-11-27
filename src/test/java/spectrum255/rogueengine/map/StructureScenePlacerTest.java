package spectrum255.rogueengine.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

class StructureScenePlacerTest {
	@BeforeAll
	static void bootstrapMinecraft() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	void computesUnrotatedBoundingBox() {
		StructureTemplate template = template();
		MapScene scene = scene(Rotation.NONE, new BlockPos(0, 64, 0), new BlockPos(15, 79, 15));

		BoundingBox box = StructureScenePlacer.boundingBox(template, scene, new BlockPos(0, 64, 0));

		assertEquals(new BoundingBox(0, 64, 0, 1, 64, 0), box);
	}

	@Test
	void computesBoundingBoxFromSnapshotPlacement() {
		StructureTemplate template = template();

		BoundingBox box = StructureScenePlacer.boundingBox(template, Rotation.NONE,
				net.minecraft.world.level.block.Mirror.NONE, new BlockPos(0, 64, 0));

		assertEquals(new BoundingBox(0, 64, 0, 1, 64, 0), box);
	}

	@Test
	void rotationSwapsWidthAndDepth() {
		StructureTemplate template = template();
		MapScene scene = scene(Rotation.CLOCKWISE_90, new BlockPos(0, 64, 0), new BlockPos(15, 79, 15));

		BoundingBox box = StructureScenePlacer.boundingBox(template, scene, new BlockPos(0, 64, 0));

		assertEquals(1, box.getXSpan());
		assertEquals(2, box.getZSpan());
	}

	@Test
	void rejectsPlacementOutsideSceneBounds() {
		StructureTemplate template = template();
		MapScene scene = scene(Rotation.NONE, new BlockPos(0, 64, 0), new BlockPos(0, 64, 0));

		assertThrows(IllegalArgumentException.class,
				() -> StructureScenePlacer.boundingBox(template, scene, new BlockPos(8, 64, 8)));
	}

	private StructureTemplate template() {
		CompoundTag root = new CompoundTag();
		ListTag size = new ListTag();
		size.add(IntTag.valueOf(2));
		size.add(IntTag.valueOf(1));
		size.add(IntTag.valueOf(1));
		root.put("size", size);
		CompoundTag palette = new CompoundTag();
		palette.put("Name", StringTag.valueOf("minecraft:stone"));
		ListTag palettes = new ListTag();
		palettes.add(palette);
		root.put("palette", palettes);
		ListTag blocks = new ListTag();
		for (int x = 0; x < 2; x++) {
			CompoundTag block = new CompoundTag();
			ListTag pos = new ListTag();
			pos.add(IntTag.valueOf(x));
			pos.add(IntTag.valueOf(0));
			pos.add(IntTag.valueOf(0));
			block.put("pos", pos);
			block.putInt("state", 0);
			blocks.add(block);
		}
		root.put("blocks", blocks);
		root.put("entities", new ListTag());
		StructureTemplate template = new StructureTemplate();
		template.load(net.minecraft.core.registries.BuiltInRegistries.BLOCK, root);
		return template;
	}

	private MapScene scene(Rotation rotation, BlockPos min, BlockPos max) {
		return new MapScene(1, Identifier.withDefaultNamespace("scene"), new MapScene.SceneBounds(min, max),
				BlockPos.ZERO, min, rotation, net.minecraft.world.level.block.Mirror.NONE, List.of(),
				new MapScene.RefreshPolicy(List.of(MapScene.RefreshTrigger.MANUAL), MapScene.PlayerResetPolicy.NONE));
	}
}
