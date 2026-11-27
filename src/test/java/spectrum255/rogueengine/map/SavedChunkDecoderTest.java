package spectrum255.rogueengine.map;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.mojang.serialization.Codec;

import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.Strategy;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

class SavedChunkDecoderTest {
	@BeforeAll
	static void bootstrap() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	void decodesSectionBlocksAndLocalizesBlockEntities() {
		RegistryAccess registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
		CompoundTag chunk = chunkWithStoneSection(registries);
		CompoundTag chest = new CompoundTag();
		chest.putString("id", "minecraft:chest");
		chest.putInt("x", 2);
		chest.putInt("y", 128);
		chest.putInt("z", 3);
		ListTag blockEntities = new ListTag();
		blockEntities.add(chest);
		chunk.put("block_entities", blockEntities);

		SavedChunkDecoder.Decoded decoded = SavedChunkDecoder.decode(chunk, new ChunkPos(0, 0),
				new BoundingBox(0, 128, 0, 4, 128, 4), registries, true);

		assertEquals(25, decoded.blocks().size());
		assertEquals(1, decoded.blockEntities().size());
		assertEquals(2, decoded.blockEntities().getFirst().getInt("x").orElseThrow());
		assertEquals(0, decoded.blockEntities().getFirst().getInt("y").orElseThrow());
		assertEquals(3, decoded.blockEntities().getFirst().getInt("z").orElseThrow());
	}

	@Test
	void rejectsNonFullChunk() {
		RegistryAccess registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
		CompoundTag chunk = chunkWithStoneSection(registries);
		chunk.putString("Status", "minecraft:empty");
		org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
				() -> SavedChunkDecoder.decode(chunk, new ChunkPos(0, 0),
						new BoundingBox(0, 128, 0, 4, 128, 4), registries, true));
	}

	private CompoundTag chunkWithStoneSection(RegistryAccess registries) {
		Strategy<BlockState> strategy = Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY);
		Codec<PalettedContainer<BlockState>> codec = PalettedContainer.codecRW(
				BlockState.FULL_CODEC, strategy, Blocks.AIR.defaultBlockState());
		PalettedContainer<BlockState> states = new PalettedContainer<>(Blocks.AIR.defaultBlockState(), strategy);
		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 16; x++) {
				for (int z = 0; z < 16; z++) {
					states.set(x, y, z, Blocks.STONE.defaultBlockState());
				}
			}
		}
		CompoundTag section = new CompoundTag();
		section.putByte("Y", (byte) 8);
		section.store("block_states", codec, states);
		ListTag sections = new ListTag();
		sections.add(section);
		CompoundTag chunk = new CompoundTag();
		chunk.putInt("xPos", 0);
		chunk.putInt("zPos", 0);
		chunk.putString("Status", "minecraft:full");
		chunk.put("sections", sections);
		return chunk;
	}
}
