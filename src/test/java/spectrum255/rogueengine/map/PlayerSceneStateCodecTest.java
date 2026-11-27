package spectrum255.rogueengine.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.resources.RegistryOps;

import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

class PlayerSceneStateCodecTest {
	@BeforeAll
	static void bootstrap() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	void roundTripsStateAndEffects() {
		RegistryAccess registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
		RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, registries);
		CompoundTag effect = (CompoundTag) MobEffectInstance.CODEC.encodeStart(ops,
				new MobEffectInstance(MobEffects.REGENERATION, 200, 1)).result().orElseThrow();
		PlayerSceneState state = new PlayerSceneState(UUID.randomUUID(), UUID.randomUUID(), 18.5F,
				new PlayerFoodState(17, 6.5F, 3.25F, 0), 300, 7, 0.35F, 120, List.of(effect), 4L);

		CompoundTag tag = PlayerSceneStateCodec.write(state, registries);
		PlayerSceneState restored = PlayerSceneStateCodec.read(tag, registries);

		assertEquals(state, restored);
	}

	@Test
	void rejectsWrongSchemaVersion() {
		RegistryAccess registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
		CompoundTag tag = new CompoundTag();
		tag.putInt("schema_version", 2);
		assertThrows(IllegalArgumentException.class, () -> PlayerSceneStateCodec.read(tag, registries));
	}
}
