package spectrum255.rogueengine.worldgen;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

import spectrum255.rogueengine.RogueEngineFabric;
import spectrum255.rogueengine.profile.TerrainBoundary;

public final class BoundedNoiseGenerator extends ChunkGenerator {
	public static final MapCodec<BoundedNoiseGenerator> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			NoiseBasedChunkGenerator.CODEC.fieldOf("delegate").forGetter(BoundedNoiseGenerator::delegate),
			TerrainBoundary.CODEC.fieldOf("boundary").forGetter(BoundedNoiseGenerator::boundary),
			TerrainBoundary.OutsideMode.CODEC.fieldOf("outside_mode").forGetter(BoundedNoiseGenerator::outsideMode))
			.apply(instance, BoundedNoiseGenerator::new));

	private final NoiseBasedChunkGenerator delegate;
	private final TerrainBoundary boundary;
	private final TerrainBoundary.OutsideMode outsideMode;

	public BoundedNoiseGenerator(NoiseBasedChunkGenerator delegate, TerrainBoundary boundary,
			TerrainBoundary.OutsideMode outsideMode) {
		super(Objects.requireNonNull(delegate, "delegate").getBiomeSource());
		this.delegate = delegate;
		this.boundary = Objects.requireNonNull(boundary, "boundary").validate().getOrThrow();
		this.outsideMode = Objects.requireNonNull(outsideMode, "outsideMode");
		if (outsideMode != boundary.outsideMode()) {
			throw new IllegalArgumentException("Outside mode does not match the terrain boundary");
		}
	}

	public static BoundedNoiseGenerator create(NoiseBasedChunkGenerator delegate, TerrainBoundary boundary,
			TerrainBoundary.OutsideMode outsideMode) {
		Objects.requireNonNull(delegate, "delegate");
		Objects.requireNonNull(boundary, "boundary").validate().getOrThrow();
		Objects.requireNonNull(outsideMode, "outsideMode");
		if (outsideMode != boundary.outsideMode()) {
			throw new IllegalArgumentException("Outside mode does not match the terrain boundary");
		}
		NoiseGeneratorSettings base = delegate.generatorSettings().value();
		NoiseRouter router = base.noiseRouter();
		DensityFunction bounded = new BoundedDensityFunction(router.finalDensity(), boundary);
		NoiseRouter boundedRouter = new NoiseRouter(
				router.temperature(), router.vegetation(), router.continents(), router.erosion(),
				router.depth(), router.ridges(), router.chunkSurfaceLevel(), bounded);
		NoiseGeneratorSettings settings = new NoiseGeneratorSettings(
				base.noiseSettings(), base.defaultBlock(), base.defaultFluid(), boundedRouter,
				base.materialRule(), base.spawnTarget(), base.seaLevel(), base.disableMobGeneration(),
				base.aquifers(), base.useLegacyRandomSource(), base.debugFunctions());
		return new BoundedNoiseGenerator(
				new NoiseBasedChunkGenerator(delegate.getBiomeSource(), Holder.direct(settings)),
				boundary, outsideMode);
	}

	public NoiseBasedChunkGenerator delegate() {
		return delegate;
	}

	public TerrainBoundary boundary() {
		return boundary;
	}

	public TerrainBoundary.OutsideMode outsideMode() {
		return outsideMode;
	}

	@Override
	protected MapCodec<? extends ChunkGenerator> codec() {
		return CODEC;
	}

	@Override
	public Optional<Identifier> getTypeNameForDataFixer() {
		return Optional.of(RogueEngineFabric.id("bounded_noise"));
	}

	public boolean stable(ResourceKey<NoiseGeneratorSettings> key) {
		return delegate.stable(key);
	}

	@Override
	public int getGenDepth() {
		return delegate.getGenDepth();
	}

	@Override
	public int getSeaLevel() {
		return delegate.getSeaLevel();
	}

	@Override
	public int getMinY() {
		return delegate.getMinY();
	}

	@Override
	public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level,
			RandomState randomState) {
		if (boundary.contains(x, z)) {
			return delegate.getBaseHeight(x, z, type, level, randomState);
		}
		return outsideMode == TerrainBoundary.OutsideMode.BEDROCK_FLOOR ? getMinY() + 1 : getMinY();
	}

	@Override
	public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState randomState) {
		if (boundary.contains(x, z)) {
			return delegate.getBaseColumn(x, z, level, randomState);
		}
		BlockState[] states = new BlockState[getGenDepth()];
		Arrays.fill(states, Blocks.AIR.defaultBlockState());
		if (outsideMode == TerrainBoundary.OutsideMode.BEDROCK_FLOOR) {
			states[0] = Blocks.BEDROCK.defaultBlockState();
		}
		return new NoiseColumn(getMinY(), states);
	}

	@Override
	public CompletableFuture<ChunkAccess> buildTerrain(ChunkAccess chunk, Blender blender,
			RandomState randomState, StructureManager structureManager, BiomeManager biomeManager,
			WorldGenRegion region, Set<Holder<Biome>> biomes) {
		if (!boundary.intersects(chunk.getPos())) {
			if (outsideMode == TerrainBoundary.OutsideMode.BEDROCK_FLOOR) {
				applyBedrockFloor(chunk);
			}
			return CompletableFuture.completedFuture(chunk);
		}
		return delegate.buildTerrain(chunk, blender, randomState, structureManager, biomeManager, region, biomes)
				.thenApply(generated -> {
					if (outsideMode == TerrainBoundary.OutsideMode.BEDROCK_FLOOR) {
						applyBedrockFloor(generated);
					}
					return generated;
				});
	}

	@Override
	public void createStructures(RegistryAccess registries, ChunkGeneratorStructureState state,
			StructureManager structureManager, ChunkAccess chunk, StructureTemplateManager templateManager,
			ResourceKey<Level> levelKey) {
		if (boundary.intersects(chunk.getPos())) {
			delegate.createStructures(registries, state, structureManager, chunk, templateManager, levelKey);
		}
	}

	@Override
	public void createReferences(WorldGenLevel level, StructureManager structureManager, ChunkAccess chunk) {
		if (boundary.intersects(chunk.getPos())) {
			delegate.createReferences(level, structureManager, chunk);
		}
	}

	@Override
	public void applyBiomeDecoration(WorldGenLevel level, ChunkAccess chunk, StructureManager structureManager) {
		if (boundary.intersects(chunk.getPos())) {
			delegate.applyBiomeDecoration(level, chunk, structureManager);
		}
	}

	@Override
	public void spawnOriginalMobs(WorldGenRegion region) {
		delegate.spawnOriginalMobs(region);
	}

	@Override
	public void addDebugScreenInfo(List<String> info, RandomState randomState, BlockPos pos,
			SamplerContext samplerContext) {
		delegate.addDebugScreenInfo(info, randomState, pos, samplerContext);
		info.add("Rogue Engine terrain boundary: " + boundary.shape().getSerializedName()
				+ " r=" + boundary.radiusX() + "x" + boundary.radiusZ());
	}

	@Override
	public void validate() {
		delegate.validate();
	}

	private void applyBedrockFloor(ChunkAccess chunk) {
		ChunkPos pos = chunk.getPos();
		BlockPos.MutableBlockPos blockPos = new BlockPos.MutableBlockPos();
		for (int x = pos.getMinBlockX(); x <= pos.getMaxBlockX(); x++) {
			for (int z = pos.getMinBlockZ(); z <= pos.getMaxBlockZ(); z++) {
				if (!boundary.contains(x, z)) {
					chunk.setBlockState(blockPos.set(x, getMinY(), z), Blocks.BEDROCK.defaultBlockState(), 0);
				}
			}
		}
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof BoundedNoiseGenerator generator
				&& delegate.generatorSettings().equals(generator.delegate.generatorSettings())
				&& getBiomeSource().equals(generator.getBiomeSource())
				&& boundary.equals(generator.boundary) && outsideMode == generator.outsideMode;
	}

	@Override
	public int hashCode() {
		return Objects.hash(delegate.generatorSettings(), getBiomeSource(), boundary, outsideMode);
	}
}
