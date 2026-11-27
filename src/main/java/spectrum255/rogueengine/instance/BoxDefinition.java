package spectrum255.rogueengine.instance;

import java.util.Objects;
import java.util.UUID;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import net.minecraft.core.HolderLookup;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;

import spectrum255.rogueengine.map.SceneSelectionSnapshot;
import spectrum255.rogueengine.profile.BoxProfile;
import spectrum255.rogueengine.profile.BoxProfileCodec;
import spectrum255.rogueengine.profile.ProfileSnapshot;
import spectrum255.rogueengine.profile.ResolvedBoxProfile;

public record BoxDefinition(
		UUID instanceId,
		Identifier profileId,
		String profileJson,
		String profileHash,
		String dependencyHash,
		String levelStemJson,
		long seed,
		SceneSelectionSnapshot sceneSnapshot) {
	public BoxDefinition {
		Objects.requireNonNull(instanceId, "instanceId");
		Objects.requireNonNull(profileId, "profileId");
		if (profileJson == null || profileJson.isBlank() || profileHash == null || profileHash.isBlank()
				|| dependencyHash == null || dependencyHash.isBlank() || levelStemJson == null || levelStemJson.isBlank()) {
			throw new IllegalArgumentException("Box definition snapshots are required");
		}
		if (sceneSnapshot != null && !sceneSnapshot.instanceId().equals(instanceId)) {
			throw new IllegalArgumentException("Scene snapshot instance UUID does not match box UUID");
		}
	}

	public BoxDefinition(UUID instanceId, Identifier profileId, String profileJson, String profileHash,
			String dependencyHash, String levelStemJson, long seed) {
		this(instanceId, profileId, profileJson, profileHash, dependencyHash, levelStemJson, seed, null);
	}

	public static BoxDefinition capture(UUID instanceId, Identifier profileId, ResolvedBoxProfile resolved,
			long seed, HolderLookup.Provider registries) {
		ProfileSnapshot snapshot = ProfileSnapshot.capture(resolved, registries);
		return new BoxDefinition(instanceId, profileId, snapshot.profileJson(), snapshot.profileHash(),
				snapshot.dependencyHash(), snapshot.levelStemJson(), seed);
	}

	public static BoxDefinition capture(UUID instanceId, Identifier profileId, ResolvedBoxProfile resolved,
			long seed, HolderLookup.Provider registries, SceneSelectionSnapshot sceneSnapshot) {
		BoxDefinition base = capture(instanceId, profileId, resolved, seed, registries);
		return new BoxDefinition(instanceId, profileId, base.profileJson(), base.profileHash(),
				base.dependencyHash(), base.levelStemJson(), seed, sceneSnapshot);
	}

	public BoxProfile profile(HolderLookup.Provider registries) {
		JsonElement json = JsonParser.parseString(profileJson);
		return BoxProfileCodec.CODEC.parse(RegistryOps.create(com.mojang.serialization.JsonOps.INSTANCE, registries), json)
				.getOrThrow();
	}
}
