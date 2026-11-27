package spectrum255.rogueengine.probe.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.server.level.ServerLevel;
import spectrum255.rogueengine.probe.DimensionProbe;

@Mixin(ServerLevel.class)
public abstract class ServerLevelSeedMixin {
	@Inject(method = "getSeed", at = @At("HEAD"), cancellable = true)
	private void useInstanceSeed(CallbackInfoReturnable<Long> callbackInfo) {
		ServerLevel level = (ServerLevel) (Object) this;
		Long seed = DimensionProbe.seedFor(level.dimension());
		if (seed != null) {
			callbackInfo.setReturnValue(seed);
		}
	}
}
