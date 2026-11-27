package spectrum255.rogueengine.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.server.MinecraftServer;
import spectrum255.rogueengine.lifecycle.ServerLifecycleHooks;

@Mixin(MinecraftServer.class)
public abstract class MinecraftServerDimensionLifecycleMixin {
	@Inject(method = "createLevels", at = @At("TAIL"))
	private void restoreRogueEngineDimensions(CallbackInfo callbackInfo) {
		ServerLifecycleHooks.restoreDimensions((MinecraftServer) (Object) this);
	}
}
