package spectrum255.rogueengine.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.portal.TeleportTransition;

import spectrum255.rogueengine.lifecycle.ServerLifecycleHooks;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerTeleportMixin {
	@Inject(method = "teleport(Lnet/minecraft/world/level/portal/TeleportTransition;)Lnet/minecraft/server/level/ServerPlayer;",
			at = @At("HEAD"), cancellable = true)
	private void prepareInventoryTransition(TeleportTransition transition, CallbackInfoReturnable<ServerPlayer> callbackInfo) {
		ServerPlayer player = (ServerPlayer) (Object) this;
		if (!ServerLifecycleHooks.current(player.level().getServer()).inventories().beforeTeleport(player, transition)) {
			callbackInfo.setReturnValue(null);
		}
	}

	@Inject(method = "teleport(Lnet/minecraft/world/level/portal/TeleportTransition;)Lnet/minecraft/server/level/ServerPlayer;",
			at = @At("RETURN"))
	private void rollbackRejectedInventoryTransition(TeleportTransition transition, CallbackInfoReturnable<ServerPlayer> callbackInfo) {
		ServerPlayer player = (ServerPlayer) (Object) this;
		if (callbackInfo.getReturnValue() != null) {
			ServerLifecycleHooks.current(player.level().getServer()).playerStates()
					.commitEntry(new spectrum255.rogueengine.map.VanillaPlayerSceneStateAccess(player));
			return;
		}
		ServerLifecycleHooks.current(player.level().getServer()).inventories().rollback(player);
	}
}
