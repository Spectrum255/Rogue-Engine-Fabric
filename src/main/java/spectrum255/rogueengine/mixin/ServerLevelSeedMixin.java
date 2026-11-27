package spectrum255.rogueengine.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.server.level.ServerLevel;
import spectrum255.rogueengine.dimension.DimensionSeeds;

@Mixin(ServerLevel.class)
public abstract class ServerLevelSeedMixin {
	@Inject(method = "getSeed", at = @At("HEAD"), cancellable = true)
	private void useInstanceSeed(CallbackInfoReturnable<Long> callbackInfo) {
		ServerLevel level = (ServerLevel) (Object) this;
		DimensionSeeds.seedFor(level.dimension()).ifPresent(callbackInfo::setReturnValue);
	}
}
