package spectrum255.rogueengine.map;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.effect.MobEffectInstance;

public final class PlayerSceneStateCodec {
	private PlayerSceneStateCodec() {
	}

	public static CompoundTag write(PlayerSceneState state, HolderLookup.Provider registries) {
		CompoundTag root = new CompoundTag();
		root.putInt("schema_version", 1);
		root.put("player_uuid", StringTag.valueOf(state.playerId().toString()));
		root.put("instance_uuid", StringTag.valueOf(state.instanceId().toString()));
		root.putFloat("health", state.health());
		writeFood(root, state.food());
		root.putInt("air_supply", state.airSupply());
		root.putInt("experience_level", state.experienceLevel());
		root.putFloat("experience_progress", state.experienceProgress());
		root.putInt("experience_total", state.experienceTotal());
		ListTag effects = new ListTag();
		state.effects().forEach(effect -> effects.add(effect.copy()));
		root.put("effects", effects);
		root.putLong("applied_reset_count", state.appliedResetCount());
		return root;
	}

	public static PlayerSceneState read(CompoundTag root, HolderLookup.Provider registries) {
		if (root.getIntOr("schema_version", 0) != 1) {
			throw new IllegalArgumentException("Unsupported player scene state schema");
		}
		List<CompoundTag> effects = new ArrayList<>();
		for (Tag element : root.getListOrEmpty("effects")) {
			if (!(element instanceof CompoundTag effect)) {
				throw new IllegalArgumentException("Invalid player effect entry");
			}
			var ops = RegistryOps.create(NbtOps.INSTANCE, registries);
			MobEffectInstance.CODEC.parse(ops, effect).result().orElseThrow();
			effects.add(effect.copy());
		}
		return new PlayerSceneState(uuid(root, "player_uuid"), uuid(root, "instance_uuid"),
				root.getFloatOr("health", 0.0F), readFood(root), root.getIntOr("air_supply", -1),
				root.getIntOr("experience_level", -1), root.getFloatOr("experience_progress", -1.0F),
				root.getIntOr("experience_total", -1), List.copyOf(effects),
				root.getLongOr("applied_reset_count", -1L));
	}

	private static void writeFood(CompoundTag root, PlayerFoodState food) {
		CompoundTag tag = new CompoundTag();
		tag.putInt("foodLevel", food.foodLevel());
		tag.putInt("foodTickTimer", food.tickTimer());
		tag.putFloat("foodSaturationLevel", food.saturation());
		tag.putFloat("foodExhaustionLevel", food.exhaustion());
		root.put("food", tag);
	}

	private static PlayerFoodState readFood(CompoundTag root) {
		if (!(root.get("food") instanceof CompoundTag tag)) {
			throw new IllegalArgumentException("Missing player food state");
		}
		return new PlayerFoodState(tag.getIntOr("foodLevel", -1), tag.getFloatOr("foodSaturationLevel", -1.0F),
				tag.getFloatOr("foodExhaustionLevel", -1.0F), tag.getIntOr("foodTickTimer", -1));
	}

	private static UUID uuid(CompoundTag root, String key) {
		try {
			return UUID.fromString(root.getStringOr(key, ""));
		} catch (IllegalArgumentException exception) {
			throw new IllegalArgumentException("Invalid " + key, exception);
		}
	}
}
