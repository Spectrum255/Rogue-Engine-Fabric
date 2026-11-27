package spectrum255.rogueengine.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.material.MapColor;

class MapPreviewModelTest {
	@BeforeAll
	static void bootstrapMinecraft() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	void buildsTopDownHeightmapAndColor() {
		MapSnapshot snapshot = new MapSnapshot(1, new Vec3i(2, 1, 2),
				List.of(Blocks.STONE.defaultBlockState(), Blocks.GRASS_BLOCK.defaultBlockState()),
				List.of(new MapSnapshot.SnapshotBlock(new BlockPos(0, 0, 0), 0),
						new MapSnapshot.SnapshotBlock(new BlockPos(1, 0, 1), 1)),
				List.of(), List.of());

		MapPreviewModel model = MapPreviewModel.fromSnapshot(snapshot, Rotation.NONE, Mirror.NONE);

		assertEquals(2, model.width());
		assertEquals(2, model.depth());
		assertEquals(0, model.heightAt(0, 0));
		assertEquals(0, model.heightAt(1, 1));
		assertEquals(MapColor.STONE.calculateARGBColor(MapColor.Brightness.HIGH), model.colorAt(0, 0));
		assertEquals(MapColor.GRASS.calculateARGBColor(MapColor.Brightness.HIGH), model.colorAt(1, 1));
		assertEquals(Integer.MIN_VALUE, model.heightAt(0, 1));
		assertEquals(0, model.colorAt(0, 1));
	}

	@Test
	void rotationSwapsDimensionsAndMirrorReversesX() {
		MapSnapshot snapshot = new MapSnapshot(1, new Vec3i(3, 1, 2),
				List.of(Blocks.STONE.defaultBlockState()),
				List.of(new MapSnapshot.SnapshotBlock(new BlockPos(0, 0, 0), 0),
						new MapSnapshot.SnapshotBlock(new BlockPos(2, 0, 1), 0)),
				List.of(), List.of());

		MapPreviewModel rotated = MapPreviewModel.fromSnapshot(snapshot, Rotation.CLOCKWISE_90, Mirror.NONE);
		assertEquals(2, rotated.width());
		assertEquals(3, rotated.depth());
		assertTrue(rotated.heightAt(1, 0) > Integer.MIN_VALUE);
		assertTrue(rotated.heightAt(0, 2) > Integer.MIN_VALUE);

		MapPreviewModel mirrored = MapPreviewModel.fromSnapshot(snapshot, Rotation.NONE, Mirror.LEFT_RIGHT);
		assertEquals(3, mirrored.width());
		assertTrue(mirrored.heightAt(2, 0) > Integer.MIN_VALUE);
		assertTrue(mirrored.heightAt(0, 1) > Integer.MIN_VALUE);
	}
}
