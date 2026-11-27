package spectrum255.rogueengine.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.portal.TeleportTransition;

import spectrum255.rogueengine.dimension.PlayerRespawnPolicy;
import spectrum255.rogueengine.dimension.PlayerTransferService;
import spectrum255.rogueengine.lifecycle.ServerLifecycleHooks;
import spectrum255.rogueengine.map.SceneResetService;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerRespawnMixin {
	@Inject(method = "die(Lnet/minecraft/world/damagesource/DamageSource;)V", at = @At("HEAD"))
	private void prepareBoxDeathInventory(DamageSource source, CallbackInfo callbackInfo) {
		ServerPlayer player = (ServerPlayer) (Object) this;
		var runtime = ServerLifecycleHooks.current(player.level().getServer());
		runtime.inventories().beforeDeath(player);
		new SceneResetService(runtime).onDeath(player);
	}

	@Inject(method = "findRespawnPositionAndUseSpawnBlock", at = @At("HEAD"), cancellable = true)
	private void useFallbackForBoxDeath(boolean keepData, TeleportTransition.PostTeleportTransition postTeleportTransition,
			CallbackInfoReturnable<TeleportTransition> callbackInfo) {
		ServerPlayer player = (ServerPlayer) (Object) this;
		if (!PlayerRespawnPolicy.shouldUseFallback(player.level().dimension())) {
			return;
		}
		callbackInfo.setReturnValue(PlayerTransferService.fallbackRespawnTransition(player));
	}
}
