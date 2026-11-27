package spectrum255.rogueengine.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

import spectrum255.rogueengine.lifecycle.ServerLifecycleHooks;

@Mixin(LivingEntity.class)
public abstract class LivingEntityDeathLootMixin {
	@Inject(method = "dropAllDeathLoot(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;)V",
			at = @At("HEAD"), cancellable = true)
	private void suppressManagedBoxDeathLoot(ServerLevel level, DamageSource source, CallbackInfo callbackInfo) {
		if ((Object) this instanceof ServerPlayer player
				&& ServerLifecycleHooks.current(player.level().getServer()).inventories().shouldSuppressDeathLoot(player)) {
			callbackInfo.cancel();
		}
	}
}
