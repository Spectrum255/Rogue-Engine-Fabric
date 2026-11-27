package spectrum255.rogueengine.worldgen;

public enum RandomizationMode {
	NOISE_CHAOS,
	FULL_CHAOS,
	PRESET_CHAOS,
	CENTRAL_ISLAND;

	public static RandomizationMode from(String value) {
		return switch (value) {
			case "noise_chaos" -> NOISE_CHAOS;
			case "full_chaos" -> FULL_CHAOS;
			case "preset_chaos" -> PRESET_CHAOS;
			case "central_island" -> CENTRAL_ISLAND;
			default -> throw new IllegalArgumentException("Unknown randomization mode: " + value);
		};
	}
}
