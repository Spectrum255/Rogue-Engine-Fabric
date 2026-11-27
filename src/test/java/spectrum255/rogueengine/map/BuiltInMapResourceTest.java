package spectrum255.rogueengine.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import spectrum255.rogueengine.profile.BoxProfile;
import spectrum255.rogueengine.profile.BoxProfileCodec;
import spectrum255.rogueengine.profile.WorldGenTemplate;
import spectrum255.rogueengine.profile.WorldGenTemplateCodec;

class BuiltInMapResourceTest {
	@BeforeAll
	static void bootstrapMinecraft() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	void builtInScenesAndPoolParse() throws IOException {
		MapScene first = readScene("example_room");
		MapScene second = readScene("example_room_alt");
		MapPool pool = readPool();

		assertEquals(2, pool.entries().size());
		assertFalse(first.refresh().triggers().isEmpty());
		assertFalse(second.refresh().triggers().isEmpty());
	}

	@Test
	void builtInProfileReferencesMapPoolTemplate() throws IOException {
		String path = "/data/rogue-engine-fabric/rogue-engine-fabric/profile/map_pool.json";
		try (InputStream input = BuiltInMapResourceTest.class.getResourceAsStream(path)) {
			assertNotNull(input, "Missing built-in map pool profile: " + path);
			BoxProfile profile = BoxProfileCodec.CODEC.parse(JsonOps.INSTANCE,
					JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8))).getOrThrow();
			BoxProfile.TemplateGenerator generator = assertInstanceOf(BoxProfile.TemplateGenerator.class, profile.generator());
			assertEquals("rogue-engine-fabric:map_pool", generator.id().toString());
		}
	}

	@Test
	void builtInTemplateReferencesPoolAndStructureLoadsAtDeclaredBounds() throws Exception {
		String path = "/data/rogue-engine-fabric/rogue-engine-fabric/worldgen_template/map_pool.json";
		try (InputStream input = BuiltInMapResourceTest.class.getResourceAsStream(path)) {
			assertNotNull(input, "Missing built-in map pool template: " + path);
			WorldGenTemplate template = WorldGenTemplateCodec.CODEC.parse(JsonOps.INSTANCE,
					JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8))).getOrThrow();
			assertEquals("map_pool", template.generatorKind());
			assertEquals("rogue-engine-fabric:example_pool", template.mapPoolId().toString());
		}
		try (InputStream structure = BuiltInMapResourceTest.class.getResourceAsStream(
				"/data/rogue-engine-fabric/structure/example_room.nbt")) {
			assertNotNull(structure, "Missing built-in example structure");
			CompoundTag tag = NbtIo.readCompressed(structure, NbtAccounter.create(16 * 1024 * 1024));
			StructureTemplate template = new StructureTemplate();
			template.load(BuiltInRegistries.BLOCK, tag);
			MapScene scene = readScene("example_room");
			assertEquals(new Vec3i(5, 1, 5), template.getSize());
			assertEquals(new BoundingBox(0, 128, 0, 4, 128, 4),
					StructureScenePlacer.boundingBox(template, scene, new BlockPos(0, 128, 0)));
			ByteArrayOutputStream output = new ByteArrayOutputStream();
			NbtIo.writeCompressed(template.save(new CompoundTag()), output);
			StructureTemplate cached = new StructureTemplate();
			cached.load(BuiltInRegistries.BLOCK,
					NbtIo.readCompressed(new java.io.ByteArrayInputStream(output.toByteArray()),
							NbtAccounter.create(16 * 1024 * 1024)));
			assertEquals(new Vec3i(5, 1, 5), cached.getSize());
			assertEquals(new BoundingBox(0, 128, 0, 4, 128, 4),
					StructureScenePlacer.boundingBox(cached, scene, new BlockPos(0, 128, 0)));
		}
	}

	private MapScene readScene(String name) throws IOException {
		String path = "/data/rogue-engine-fabric/rogue-engine-fabric/map_scene/" + name + ".json";
		try (InputStream input = BuiltInMapResourceTest.class.getResourceAsStream(path)) {
			assertNotNull(input, "Missing built-in map scene: " + path);
			return MapSceneCodec.CODEC.parse(JsonOps.INSTANCE,
					JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8))).getOrThrow();
		}
	}

	private MapPool readPool() throws IOException {
		String path = "/data/rogue-engine-fabric/rogue-engine-fabric/map_pool/example_pool.json";
		try (InputStream input = BuiltInMapResourceTest.class.getResourceAsStream(path)) {
			assertNotNull(input, "Missing built-in map pool: " + path);
			return MapPoolCodec.CODEC.parse(JsonOps.INSTANCE,
					JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8))).getOrThrow();
		}
	}
}
