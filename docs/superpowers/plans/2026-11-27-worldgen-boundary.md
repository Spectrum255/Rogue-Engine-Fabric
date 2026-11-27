# 模板地形边界与原版硬世界边界 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让世界生成模板声明圆形/椭圆可玩地形边界，边界外快速生成虚空或基岩底层，同时保留并校验 Profile 中独立的原版硬世界边界。

**Architecture:** `TerrainBoundary` 保存模板边界；`BoundedDensityFunction` 在最终密度层屏蔽边界外地形；`BoundedNoiseGenerator` 包装原版噪声生成器，跳过边界外区块、可选铺设基岩底层，并提供可序列化 Codec。模板解析时校验硬边界包含关系与出生点，实例快照继续保存完整生成器。

**Tech Stack:** Java 25、Minecraft 26.3、Fabric Loader 0.20.1、Fabric API、JUnit 5、Gradle 9.8.2。

**设计依据：** 已批准的世界生成设计第 2 节，以及 `docs/superpowers/specs/2026-11-26-worldgen-core-design.md`。

**工作区说明：** 无 Git 元数据，不安排 commit。

---

## 已核对的 26.3 API

- `DensityFunction` 保留 `compileSampler`、`rewriteChildren`、`range`、`domainAxes`、`codec`。
- `DensityFunctions.DIRECT_CODEC` 与 `BuiltInRegistries.DENSITY_FUNCTION_TYPE` 支持注册自定义 `MapCodec`。
- `NoiseBasedChunkGenerator` 为 final，但 `ChunkGenerator` 可包装并实现完整 Codec。
- `BuiltInRegistries.CHUNK_GENERATOR` 保存 `MapCodec<? extends ChunkGenerator>`。
- `ChunkAccess.setBlockState(BlockPos, BlockState, int)`、`removeBlockEntity`、`getPos` 可用于边界后处理。
- `ServerChunkCache.getGenerator()` 可取得运行时生成器，用于搜索出生点中心。

## Task 1：模板边界模型与 Codec

**Files:**
- Create: `src/main/java/spectrum255/rogueengine/profile/TerrainBoundary.java`
- Modify: `src/main/java/spectrum255/rogueengine/profile/WorldGenTemplate.java`
- Modify: `src/main/java/spectrum255/rogueengine/profile/WorldGenTemplateCodec.java`
- Create: `src/test/java/spectrum255/rogueengine/profile/TerrainBoundaryTest.java`
- Modify: `src/test/java/spectrum255/rogueengine/profile/WorldGenTemplateCodecTest.java`

- [ ] **Step 1: 写边界几何失败测试**

```java
@Test
void circleAndEllipseContainmentAndChunkIntersection() {
    TerrainBoundary circle = new TerrainBoundary(0, 0, 16, 16,
            TerrainBoundary.Shape.CIRCLE, TerrainBoundary.OutsideMode.VOID);
    assertTrue(circle.contains(0, 0));
    assertTrue(circle.contains(16, 0));
    assertFalse(circle.contains(17, 0));
    assertTrue(circle.intersects(new ChunkPos(1, 0)));
    assertFalse(circle.intersects(new ChunkPos(2, 0)));

    TerrainBoundary ellipse = new TerrainBoundary(10, -4, 32, 16,
            TerrainBoundary.Shape.ELLIPSE, TerrainBoundary.OutsideMode.BEDROCK_FLOOR);
    assertTrue(ellipse.contains(42, -4));
    assertFalse(ellipse.contains(10, 13));
}
```

- [ ] **Step 2: 运行测试确认缺少 `TerrainBoundary`**

Run: `.\gradlew.bat test --tests spectrum255.rogueengine.profile.TerrainBoundaryTest`

Expected: 编译失败，缺少 `TerrainBoundary`。

- [ ] **Step 3: 实现 `TerrainBoundary`**

核心定义：

```java
public record TerrainBoundary(
        double centerX, double centerZ, double radiusX, double radiusZ,
        Shape shape, OutsideMode outsideMode) {
    public enum Shape { CIRCLE, ELLIPSE }
    public enum OutsideMode { VOID, BEDROCK_FLOOR }

    public boolean contains(double x, double z) {
        if (shape == Shape.CIRCLE) {
            double dx = (x - centerX) / radiusX;
            double dz = (z - centerZ) / radiusZ;
            return dx * dx + dz * dz <= 1.0D;
        }
        return Math.abs(x - centerX) <= radiusX && Math.abs(z - centerZ) <= radiusZ;
    }

    public boolean intersects(ChunkPos chunk) {
        int minX = chunk.getMinBlockX();
        int maxX = chunk.getMaxBlockX();
        int minZ = chunk.getMinBlockZ();
        int maxZ = chunk.getMaxBlockZ();
        if (maxX < centerX - radiusX || minX > centerX + radiusX
                || maxZ < centerZ - radiusZ || minZ > centerZ + radiusZ) {
            return false;
        }
        if (shape == Shape.ELLIPSE) {
            return true;
        }
        double closestX = Math.max(minX, Math.min(maxX, centerX));
        double closestZ = Math.max(minZ, Math.min(maxZ, centerZ));
        double dx = (closestX - centerX) / radiusX;
        double dz = (closestZ - centerZ) / radiusZ;
        return dx * dx + dz * dz <= 1.0D;
    }

    public DataResult<TerrainBoundary> validate() {
        if (!Double.isFinite(centerX) || !Double.isFinite(centerZ)
                || !Double.isFinite(radiusX) || !Double.isFinite(radiusZ)
                || radiusX < 16.0D || radiusX > 1_000_000.0D
                || radiusZ < 16.0D || radiusZ > 1_000_000.0D
                || shape == null || outsideMode == null
                || shape == Shape.CIRCLE && radiusX != radiusZ) {
            return DataResult.error(() -> "Invalid terrain boundary");
        }
        return DataResult.success(this);
    }

    public DataResult<TerrainBoundary> validateWithin(BoxProfile.Border hardBorder) {
        double half = hardBorder.initialSize() / 2.0D;
        if (Math.abs(centerX - hardBorder.centerX()) + radiusX > half
                || Math.abs(centerZ - hardBorder.centerZ()) + radiusZ > half) {
            return DataResult.error(() -> "Terrain boundary exceeds the hard world border");
        }
        return DataResult.success(this);
    }
}
```

Codec 使用 `RecordCodecBuilder`，`shape` 与 `outside_mode` 通过 `StringRepresentable.fromEnum` 编码。`CIRCLE` 要求 `radiusX == radiusZ`；`ELLIPSE` 允许不同半径。

- [ ] **Step 4: 扩展模板模型与 Codec**

`WorldGenTemplate` 增加最后一个字段 `TerrainBoundary terrainBoundary`，旧五参数构造器委托为 `null`。`WorldGenTemplateCodec` 增加：

```java
TerrainBoundary.CODEC.optionalFieldOf("terrain_boundary")
        .forGetter(template -> Optional.ofNullable(template.terrainBoundary()))
```

`validate()` 在边界非空时调用 `terrainBoundary.validate()` 并传播错误。`map_pool` 模板允许该字段但当前生成器物化路径不消费。

- [ ] **Step 5: 添加模板解析测试并运行**

测试 JSON：

```json
{
  "schema_version": 1,
  "generator_kind": "noise_chaos",
  "base_stem": "minecraft:overworld",
  "terrain_boundary": {
    "center_x": 0.0,
    "center_z": 0.0,
    "radius_x": 128.0,
    "radius_z": 128.0,
    "shape": "circle",
    "outside_mode": "void"
  }
}
```

断言解析后边界为 `CIRCLE/VOID`；圆形半径不等、负数半径、未知模式均拒绝。

Run: `.\gradlew.bat test --tests 'spectrum255.rogueengine.profile.*'`

Expected: 新测试与旧模板测试全部通过。

## Task 2：边界密度函数

**Files:**
- Create: `src/main/java/spectrum255/rogueengine/worldgen/BoundedDensityFunction.java`
- Create: `src/main/java/spectrum255/rogueengine/worldgen/WorldGenRegistry.java`
- Create: `src/main/java/spectrum255/rogueengine/mixin/BuiltInRegistriesMixin.java`
- Create: `src/test/java/spectrum255/rogueengine/worldgen/BoundedDensityFunctionTest.java`
- Modify: `src/main/resources/rogue-engine-fabric.mixins.json`

- [ ] **Step 1: 写密度屏蔽与 Codec 失败测试**

```java
@Test
void masksOutsideBoundaryAndRoundTrips() {
    BoundedDensityFunction function = new BoundedDensityFunction(DensityFunctions.constant(0.5F), boundary);
    DensitySampler sampler = function.compileSampler(null);
    assertEquals(0.5F, sampler.sampleValue(SamplerContext.EMPTY_UNCACHED, 0, 64, 0));
    assertEquals(-1.0F, sampler.sampleValue(SamplerContext.EMPTY_UNCACHED, 100, 64, 0));

    JsonElement json = BoundedDensityFunction.CODEC.codec().encodeStart(JsonOps.INSTANCE, function).getOrThrow();
    assertEquals(function, BoundedDensityFunction.CODEC.codec().parse(JsonOps.INSTANCE, json).getOrThrow());
}
```

- [ ] **Step 2: 运行测试确认类与注册缺失**

Run: `.\gradlew.bat test --tests spectrum255.rogueengine.worldgen.BoundedDensityFunctionTest`

Expected: 编译失败。

- [ ] **Step 3: 实现 `BoundedDensityFunction`**

```java
public final class BoundedDensityFunction implements DensityFunction {
    private final DensityFunction input;
    private final TerrainBoundary boundary;

    @Override
    public DensitySampler compileSampler(CompileContext context) {
        DensitySampler delegate = input.compileSampler(context);
        return new DensitySampler() {
            @Override
            public float sampleValue(SamplerContext samplerContext, int x, int y, int z) {
                return boundary.contains(x, z) ? delegate.sampleValue(samplerContext, x, y, z) : -1.0F;
            }

            @Override
            public void sampleVolume(SamplerContext samplerContext, DensityBuffer buffer, DensityVolume volume) {
                DensitySampler.sampleVolumeNaive(samplerContext, buffer, volume, this);
            }
        };
    }

    @Override public DensityFunction rewriteChildren(DfRewriteRule rule) {
        return new BoundedDensityFunction(rule.rewrite(input), boundary);
    }
    @Override public Interval range() { return Interval.encapsulating(input.range(), Interval.ofExact(-1.0F)); }
    @Override public int domainAxes() { return DensityFunction.ALL_AXES; }
    @Override public MapCodec<BoundedDensityFunction> codec() { return CODEC; }
}
```

`CODEC` 字段为 `input`、`boundary`。`WorldGenRegistry.register()` 使用 `Registry.register(BuiltInRegistries.DENSITY_FUNCTION_TYPE, RogueEngineFabric.id("bounded_density"), CODEC)`，重复注册时跳过。

- [ ] **Step 4: 在注册表冻结前注册**

新增 `BuiltInRegistriesMixin`，注入 `BuiltInRegistries.createContents()` 的 `RETURN`，调用 `WorldGenRegistry.register()`。该方法在 `BuiltInRegistries.bootStrap()` 调用 `freeze()` 前执行。把 mixin 加入 `rogue-engine-fabric.mixins.json`。普通单元测试不启动 Fabric/Mixin，因此直接验证 `BoundedDensityFunction.CODEC`；注册表分发由服务端冒烟覆盖。

- [ ] **Step 5: 运行密度函数测试**

Run: `.\gradlew.bat test --tests spectrum255.rogueengine.worldgen.BoundedDensityFunctionTest`

Expected: 边界内保留输入密度、边界外为 `-1`，Codec 往返通过。

## Task 3：有界噪声生成器

**Files:**
- Create: `src/main/java/spectrum255/rogueengine/worldgen/BoundedNoiseGenerator.java`
- Modify: `src/main/java/spectrum255/rogueengine/worldgen/WorldGenRegistry.java`
- Create: `src/test/java/spectrum255/rogueengine/worldgen/BoundedNoiseGeneratorTest.java`

- [ ] **Step 1: 写生成器包装与 Codec 失败测试**

```java
@Test
void wrapsDelegateAndReportsOutsideTerrain() {
    NoiseBasedChunkGenerator delegate = new NoiseBasedChunkGenerator(
            new FixedBiomeSource(null), Holder.direct(baseSettings()));
    BoundedNoiseGenerator generator = BoundedNoiseGenerator.create(delegate, boundary,
            TerrainBoundary.OutsideMode.VOID);

    assertSame(delegate, generator.delegate());
    assertEquals(delegate.getGenDepth(), generator.getGenDepth());
    assertEquals(delegate.getSeaLevel(), generator.getSeaLevel());
    assertEquals(generator.getMinY(), generator.getBaseHeight(1000, 1000,
            Heightmap.Types.WORLD_SURFACE, null, null));
    assertTrue(generator.getBaseColumn(1000, 1000, null, null).getBlock(generator.getMinY()).isAir());
}
```

- [ ] **Step 2: 运行测试确认类缺失**

Run: `.\gradlew.bat test --tests spectrum255.rogueengine.worldgen.BoundedNoiseGeneratorTest`

Expected: 编译失败。

- [ ] **Step 3: 实现 `BoundedNoiseGenerator`**

构造器：

```java
public BoundedNoiseGenerator(NoiseBasedChunkGenerator delegate,
        TerrainBoundary boundary, TerrainBoundary.OutsideMode outsideMode) {
    super(delegate.getBiomeSource());
    this.delegate = Objects.requireNonNull(delegate);
    this.boundary = Objects.requireNonNull(boundary).validate().getOrThrow();
    this.outsideMode = Objects.requireNonNull(outsideMode);
}
```

`create(...)` 在包装前把 `delegate.generatorSettings().value().noiseRouter().finalDensity()` 替换为 `new BoundedDensityFunction(original, boundary)`，从而让原版生成管线在边界外直接不填地形。

`buildTerrain(...)`：

- 区块包围盒不与边界相交且 `VOID`：直接返回已完成区块，不调用 delegate。
- 不相交且 `BEDROCK_FLOOR`：铺设 `y=delegate.getMinY()` 的基岩层后返回。
- 相交：调用 delegate，随后对每个边界外 x/z 清理完整高度；`BEDROCK_FLOOR` 再设置底层基岩。

`getBaseHeight` / `getBaseColumn` 在边界外返回虚空或基岩底层；`getGenDepth`、`getSeaLevel`、`getMinY`、`spawnOriginalMobs`、`addDebugScreenInfo`、`validate` 委托。`createStructures`、`createReferences`、`applyBiomeDecoration` 对完全边界外区块跳过。

Codec：

```java
public static final MapCodec<BoundedNoiseGenerator> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
        NoiseBasedChunkGenerator.CODEC.fieldOf("delegate").forGetter(BoundedNoiseGenerator::delegate),
        TerrainBoundary.CODEC.fieldOf("boundary").forGetter(BoundedNoiseGenerator::boundary),
        TerrainBoundary.OutsideMode.CODEC.fieldOf("outside_mode").forGetter(BoundedNoiseGenerator::outsideMode)
).apply(instance, BoundedNoiseGenerator::new));
```

`WorldGenRegistry` 同时注册 `rogue-engine-fabric:bounded_noise` 到 `BuiltInRegistries.CHUNK_GENERATOR`。

- [ ] **Step 4: 运行生成器测试**

Run: `.\gradlew.bat test --tests spectrum255.rogueengine.worldgen.BoundedNoiseGeneratorTest`

Expected: 包装、边界外高度和基岩底层通过；完整 `ChunkGenerator.CODEC` 分发与 `LevelStem` 快照由服务端冒烟验证。

## Task 4：模板解析、硬边界与出生点集成

**Files:**
- Modify: `src/main/java/spectrum255/rogueengine/profile/BoxProfile.java`
- Modify: `src/main/java/spectrum255/rogueengine/profile/BoxProfileCodec.java`
- Modify: `src/main/java/spectrum255/rogueengine/profile/ResolvedBoxProfile.java`
- Modify: `src/main/java/spectrum255/rogueengine/dimension/PlayerTransferService.java`
- Create: `src/test/java/spectrum255/rogueengine/profile/TerrainBoundaryResolutionTest.java`

- [ ] **Step 1: 写解析失败测试**

覆盖：

1. 模板边界超出硬边界时拒绝。
2. 固定出生点在模板边界外时拒绝。
3. 固定出生点在边界内时生成 `BoundedNoiseGenerator`。
4. `ProfileSnapshot` 编码后再解析，生成器仍为 `BoundedNoiseGenerator`，边界与硬边界独立保留。
5. Profile 硬边界允许 `30_000_000`，超过 `WorldBorder.MAX_SIZE` 拒绝。

- [ ] **Step 2: 运行测试确认失败**

Run: `.\gradlew.bat test --tests spectrum255.rogueengine.profile.TerrainBoundaryResolutionTest`

Expected: 新行为尚未实现。

- [ ] **Step 3: 扩展硬边界校验**

`BoxProfile.validate()` 使用 `WorldBorder.MAX_SIZE` 与 `WorldBorder.MAX_CENTER_COORDINATE`，不再把硬边界限制为 `1_000_000`。`BoxProfileCodec.BORDER_CODEC` 的 `initial_size` 与 `warning_blocks` 改用 `Codec.DOUBLE`，由 `validate()` 统一检查有限值与范围。

- [ ] **Step 4: 在模板物化时包装生成器**

`ResolvedBoxProfile.materializeTemplate(...)` 在现有 chaos switch 后：

```java
ChunkGenerator bounded = template.terrainBoundary() == null ? generated
        : BoundedNoiseGenerator.create((NoiseBasedChunkGenerator) generated,
                template.terrainBoundary(), template.terrainBoundary().outsideMode());
template.terrainBoundary().validateWithin(profile.border()).getOrThrow();
if (profile.spawn() instanceof BoxProfile.FixedSpawn fixed
        && !template.terrainBoundary().contains(fixed.x(), fixed.z())) {
    throw new IllegalArgumentException("Fixed spawn must be inside the template terrain boundary");
}
```

物化 profile 继续保存原 `border` 作为硬边界；生成器快照保存模板边界。

- [ ] **Step 5: 搜索出生点使用模板中心**

`PlayerTransferService` 在 `SearchSpawn` 分支读取 `level.getChunkSource().getGenerator()`；若是 `BoundedNoiseGenerator`，目标中心改用其 `boundary().centerX/centerZ`，否则保持原硬边界中心。

- [ ] **Step 6: 运行集成测试**

Run: `.\gradlew.bat test --tests 'spectrum255.rogueengine.profile.*' --tests 'spectrum255.rogueengine.worldgen.*'`

Expected: 全部通过。

## Task 5：内置示例、服务端冒烟与文档

**Files:**
- Create: `src/main/resources/data/rogue-engine-fabric/rogue-engine-fabric/worldgen_template/bounded_noise.json`
- Create: `src/main/resources/data/rogue-engine-fabric/rogue-engine-fabric/profile/bounded_noise.json`
- Modify: `verification/server-smoke.ps1`
- Modify: `docs/worldgen-templates.md`
- Modify: `README.md`

- [ ] **Step 1: 添加内置有界模板**

`bounded_noise.json` 使用 `noise_chaos`、保守范围、半径 `128` 的圆形边界和 `outside_mode=void`。Profile 硬边界为 `256`，使用半径 `32` 的搜索出生点。

- [ ] **Step 2: 扩展服务端冒烟**

创建 `rogue-engine-fabric:bounded_noise`，验证：

- 命令返回 `Preparing box`，随后 `state=ACTIVE`。
- catalog 的 `level_stem_json` 包含 `rogue-engine-fabric:bounded_noise`。
- `profile_json.border.initial_size == 256`。
- 手动刷新、归档、恢复通过。

- [ ] **Step 3: 更新文档**

记录 `terrain_boundary` 字段、圆形/椭圆、`void`/`bedrock_floor`、硬边界默认建议值 `30_000_000`、出生点与包含关系校验、快照持久化和未实现浮岛边界仍需后续阶段。

- [ ] **Step 4: 最终验证**

Run: `.\gradlew.bat clean build test`

Run: `.\verification\server-smoke.ps1`

Expected: 单元测试、有界模板创建/刷新/归档/恢复全部通过。

## 完成标准

- 模板可声明圆形或椭圆地形边界，边界外不生成原版地表。
- `void` 与 `bedrock_floor` 两种边界外模式生效。
- 原版硬边界独立保存，支持设计默认的 `30_000_000` 规模。
- 模板边界必须位于硬边界内，固定出生点必须位于模板边界内。
- 搜索出生点使用模板边界中心。
- 生成器与边界 mask 可完整序列化进 `levelStemJson`，归档、恢复和崩溃恢复不丢失。
- 单元测试、既有服务端冒烟和新增有界模板冒烟全部通过。
