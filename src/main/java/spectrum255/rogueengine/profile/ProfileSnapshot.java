package spectrum255.rogueengine.profile;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import com.mojang.serialization.JsonOps;

import net.minecraft.core.HolderLookup;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.level.dimension.LevelStem;

public record ProfileSnapshot(String profileJson, String profileHash, String dependencyHash, String levelStemJson) {
	public static ProfileSnapshot capture(ResolvedBoxProfile resolved, HolderLookup.Provider registries) {
		RegistryOps<com.google.gson.JsonElement> jsonOps = RegistryOps.create(JsonOps.INSTANCE, registries);
		String profileJson = BoxProfileCodec.CODEC.encodeStart(jsonOps, resolved.materializedProfile()).result().orElseThrow().toString();
		LevelStem stem = resolved.levelStem();
		String stemJson = LevelStem.CODEC.encodeStart(jsonOps, stem).result().orElseThrow().toString();
		return new ProfileSnapshot(profileJson, sha256(profileJson), sha256(stemJson), stemJson);
	}

	public static String sha256(String value) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is unavailable", exception);
		}
	}
}
