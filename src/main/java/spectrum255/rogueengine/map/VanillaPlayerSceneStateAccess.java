package spectrum255.rogueengine.map;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;

public final class VanillaPlayerSceneStateAccess implements PlayerSceneStateAccess {
	private final ServerPlayer player;

	public VanillaPlayerSceneStateAccess(ServerPlayer player) {
		this.player = player;
	}

	@Override
	public UUID playerId() {
		return player.getUUID();
	}

	@Override
	public PlayerSceneState capture(UUID instanceId, long appliedResetCount) {
		var ops = RegistryOps.create(NbtOps.INSTANCE, player.level().registryAccess());
		List<CompoundTag> effects = new ArrayList<>();
		for (MobEffectInstance effect : player.getActiveEffects()) {
			effects.add((CompoundTag) MobEffectInstance.CODEC.encodeStart(ops, effect).result().orElseThrow());
		}
		return new PlayerSceneState(player.getUUID(), instanceId, player.getHealth(),
				PlayerFoodState.capture(player.getFoodData()), player.getAirSupply(), player.experienceLevel,
				player.experienceProgress, player.totalExperience, effects, appliedResetCount);
	}

	@Override
	public void apply(PlayerSceneState state) {
		player.setHealth(state.health());
		player.setAirSupply(state.airSupply());
		state.food().apply(player.getFoodData(), player.level().registryAccess());
		player.setExperienceLevels(state.experienceLevel());
		player.setExperiencePoints(state.experienceTotal());
		player.experienceProgress = state.experienceProgress();
		player.totalExperience = state.experienceTotal();

		var ops = RegistryOps.create(NbtOps.INSTANCE, player.level().registryAccess());
		new ArrayList<>(player.getActiveEffectsMap().keySet()).forEach(player::removeEffect);
		for (CompoundTag tag : state.effects()) {
			MobEffectInstance effect = MobEffectInstance.CODEC.parse(ops, tag).result().orElseThrow();
			player.addEffect(new MobEffectInstance(effect));
		}
	}
}
