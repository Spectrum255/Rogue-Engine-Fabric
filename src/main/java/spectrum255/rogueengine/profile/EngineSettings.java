package spectrum255.rogueengine.profile;

import java.util.Objects;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.Identifier;

public record EngineSettings(
		int schemaVersion,
		int maxActiveInstances,
		int maxPendingOperations,
		int preloadRadius,
		int preloadChunksPerTick,
		int preloadTickBudgetMs,
		int preloadTimeoutSeconds,
		int archiveMinTickBudgetMs,
		int destroyConfirmationTtlSeconds,
		int quarantineRetentionDays,
		Identifier fallbackDimension) {
	public static final EngineSettings DEFAULT = new EngineSettings(
			1, 8, 16, 2, 2, 5, 60, 20, 30, 7, Identifier.withDefaultNamespace("overworld"));

	public EngineSettings {
		Objects.requireNonNull(fallbackDimension, "fallbackDimension");
	}

	public DataResult<EngineSettings> validate() {
		if (schemaVersion != 1) {
			return DataResult.error(() -> "Unsupported engine settings schema_version: " + schemaVersion);
		}
		if (maxActiveInstances() < 1 || maxActiveInstances() > 64
				|| maxPendingOperations() < 1 || maxPendingOperations() > 256
				|| preloadRadius() < 0 || preloadRadius() > 8
				|| preloadChunksPerTick() < 1 || preloadChunksPerTick() > 64
				|| preloadTickBudgetMs() < 1 || preloadTickBudgetMs() > 50
				|| preloadTimeoutSeconds() < 5 || preloadTimeoutSeconds() > 600
				|| archiveMinTickBudgetMs() < 5 || archiveMinTickBudgetMs() > 50
				|| destroyConfirmationTtlSeconds() < 5 || destroyConfirmationTtlSeconds() > 300
				|| quarantineRetentionDays() < 1 || quarantineRetentionDays() > 365) {
			return DataResult.error(() -> "Engine settings are outside safe bounds");
		}
		return DataResult.success(this);
	}

	public static final Codec<EngineSettings> CODEC = RecordCodecBuilder.<EngineSettings>create(instance -> instance.group(
			Codec.intRange(1, 1).fieldOf("schema_version").forGetter(EngineSettings::schemaVersion),
			Codec.intRange(1, 64).fieldOf("max_active_instances").forGetter(EngineSettings::maxActiveInstances),
			Codec.intRange(1, 256).fieldOf("max_pending_operations").forGetter(EngineSettings::maxPendingOperations),
			Codec.intRange(0, 8).fieldOf("preload_radius").forGetter(EngineSettings::preloadRadius),
			Codec.intRange(1, 64).fieldOf("preload_chunks_per_tick").forGetter(EngineSettings::preloadChunksPerTick),
			Codec.intRange(1, 50).fieldOf("preload_tick_budget_ms").forGetter(EngineSettings::preloadTickBudgetMs),
			Codec.intRange(5, 600).fieldOf("preload_timeout_seconds").forGetter(EngineSettings::preloadTimeoutSeconds),
			Codec.intRange(5, 50).fieldOf("archive_min_tick_budget_ms").forGetter(EngineSettings::archiveMinTickBudgetMs),
			Codec.intRange(5, 300).fieldOf("destroy_confirmation_ttl_seconds").forGetter(EngineSettings::destroyConfirmationTtlSeconds),
			Codec.intRange(1, 365).fieldOf("quarantine_retention_days").forGetter(EngineSettings::quarantineRetentionDays),
			Identifier.CODEC.fieldOf("fallback_dimension").forGetter(EngineSettings::fallbackDimension))
			.apply(instance, EngineSettings::new))
			.validate(EngineSettings::validate);
}
