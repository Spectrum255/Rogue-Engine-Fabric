package spectrum255.rogueengine.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;

import spectrum255.rogueengine.profile.BoxProfile;
import spectrum255.rogueengine.profile.BoxProfileCodec;
import spectrum255.rogueengine.profile.WorldGenTemplate;
import spectrum255.rogueengine.profile.WorldGenTemplateCodec;

class BuiltInSnapshotResourceTest {
	@BeforeAll
	static void bootstrap() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	void builtInSnapshotResourcesParse() throws IOException {
		MapScene scene = readScene();
		MapPool pool = readPool();
		WorldGenTemplate template = readTemplate();
		BoxProfile profile = readProfile();

		assertEquals(MapScene.SourceKind.SNAPSHOT, scene.source().kind());
		assertEquals(Identifier.parse("rogueengine:example_room"), scene.source().id());
		assertEquals(1, pool.entries().size());
		assertEquals("map_pool", template.generatorKind());
		assertEquals("rogueengine:example_snapshot_pool", template.mapPoolId().toString());
		assertEquals("rogueengine:map_pool_snapshot",
				assertInstanceOf(BoxProfile.TemplateGenerator.class, profile.generator()).id().toString());
	}

	@Test
	void builtInSnapshotContainsBlocksBlockEntityAndEntity() throws IOException {
		try (InputStream input = BuiltInSnapshotResourceTest.class.getResourceAsStream(
				"/data/rogueengine/rogueengine/map_snapshot/example_room.nbt")) {
			assertNotNull(input, "Missing built-in snapshot");
			CompoundTag tag = NbtIo.readCompressed(input, NbtAccounter.create(64 * 1024 * 1024));
			MapSnapshot snapshot = MapSnapshotCodec.read(tag,
					RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
			assertEquals(new Vec3i(5, 1, 5), snapshot.size());
			assertEquals(25, snapshot.blocks().size());
			assertEquals(1, snapshot.blockEntities().size());
			assertEquals(1, snapshot.entities().size());
		}
	}

	private MapScene readScene() throws IOException {
		try (InputStream input = resource("/data/rogueengine/rogueengine/map_scene/example_room_snapshot.json")) {
			return MapSceneCodec.CODEC.parse(JsonOps.INSTANCE,
					JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8))).getOrThrow();
		}
	}

	private MapPool readPool() throws IOException {
		try (InputStream input = resource("/data/rogueengine/rogueengine/map_pool/example_snapshot_pool.json")) {
			return MapPoolCodec.CODEC.parse(JsonOps.INSTANCE,
					JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8))).getOrThrow();
		}
	}

	private WorldGenTemplate readTemplate() throws IOException {
		try (InputStream input = resource("/data/rogueengine/rogueengine/worldgen_template/map_pool_snapshot.json")) {
			return WorldGenTemplateCodec.CODEC.parse(JsonOps.INSTANCE,
					JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8))).getOrThrow();
		}
	}

	private BoxProfile readProfile() throws IOException {
		try (InputStream input = resource("/data/rogueengine/rogueengine/profile/map_pool_snapshot.json")) {
			return BoxProfileCodec.CODEC.parse(JsonOps.INSTANCE,
					JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8))).getOrThrow();
		}
	}

	private InputStream resource(String path) throws IOException {
		InputStream input = BuiltInSnapshotResourceTest.class.getResourceAsStream(path);
		assertNotNull(input, "Missing built-in snapshot resource: " + path);
		return input;
	}
}
