package spectrum255.rogueengine.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.core.registries.BuiltInRegistries;

import spectrum255.rogueengine.worldgen.WorldGenRegistry;

@Mixin(BuiltInRegistries.class)
public final class BuiltInRegistriesMixin {
	private BuiltInRegistriesMixin() {
	}

	@Inject(method = "createContents", at = @At("RETURN"))
	private static void rogueEngine$registerWorldGenCodecs(CallbackInfo callbackInfo) {
		WorldGenRegistry.register();
	}
}
