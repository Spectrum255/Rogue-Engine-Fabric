# 中央浮岛生成器 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 增加 `central_island` 世界生成模板，在单个圆形/椭圆浮岛内生成连续地形，边界外始终为虚空，并支持原版地表与自定义分层两种模式。

**Architecture:** `CentralIslandSettings` 保存高度、厚度、收尖、起伏、边缘噪声和可选分层；`CentralIslandDensityFunction` 计算单岛密度mask；`CentralIslandGenerator` 包装原版噪声生成器、跳过岛外区块，并在自定义分层模式下替换表层/土层/矿石。生成器与密度函数都在注册表冻结前注册，可序列化进现有 `levelStemJson`。

**Tech Stack:** Java 25、Minecraft 26.3、Fabric Loader 0.20.1、Fabric API、JUnit 5、Gradle 9.8.2。

**设计依据：** 已批准的世界生成设计第 2 节。

**工作区说明：** 无 Git 元数据，不安排 commit。

---

## 已核对的 26.3 API

- `NoiseBasedChunkGenerator` 为 final，使用 `ChunkGenerator` 包装。
- `NoiseRouter` 可直接替换 `finalDensity`，其余气候、洞穴和水文组件保留。
- `DensityFunction`、`DensitySampler`、`BuiltInRegistries.DENSITY_FUNCTION_TYPE` 与边界阶段相同。
- `ChunkAccess.getPos/getMinY/getMaxY/setBlockState` 可用于岛外跳过和分层后处理。
- `BlockState.FULL_CODEC` 在模板 JSON 中使用 `id` 字段表达方块状态。
- `MaterialRules.state(BlockState)` 可为自定义分层提供纯岩石基础材质规则。

## Task 1：浮岛参数模型与模板 Codec

**Files:**
- Create: `src/main/java/spectrum255/rogueengine/profile/CentralIslandSettings.java`
- Modify: `src/main/java/spectrum255/rogueengine/profile/WorldGenTemplate.java`
- Modify: `src/main/java/spectrum255/rogueengine/profile/WorldGenTemplateCodec.java`
- Create: `src/test/java/spectrum255/rogueengine/profile/CentralIslandSettingsTest.java`
- Modify: `src/test/java/spectrum255/rogueengine/profile/WorldGenTemplateCodecTest.java`

- [ ] **Step 1: 写参数校验失败测试**

```java
@Test
void validatesIslandShapeAndLayerDepths() {
    CentralIslandSettings.CustomLayers layers = new CentralIslandSettings.CustomLayers(
            Blocks.GRASS_BLOCK.defaultBlockState(), Blocks.DIRT.defaultBlockState(),
            Blocks.STONE.defaultBlockState(), 1, 3, Optional.of(Blocks.COAL_ORE.defaultBlockState()),
            0.02, 4, 32);
    CentralIslandSettings settings = new CentralIslandSettings(
            CentralIslandSettings.SurfaceMode.CUSTOM_LAYERS, 128, 64, 1.5, 4.0, 0.35,
            Optional.of(layers));
    assertTrue(settings.validate().result().isPresent());

    CentralIslandSettings invalid = new CentralIslandSettings(
            CentralIslandSettings.SurfaceMode.CUSTOM_LAYERS, 128, 64, 1.5, 4.0, 0.35,
            Optional.empty());
    assertFalse(invalid.validate().result().isPresent());
}
```

- [ ] **Step 2: 运行测试确认类缺失**

Run: `.\gradlew.bat test --tests spectrum255.rogueengine.profile.CentralIslandSettingsTest`

Expected: 编译失败。

- [ ] **Step 3: 实现 `CentralIslandSettings`**

```java
public record CentralIslandSettings(
        SurfaceMode surfaceMode, int centerY, int maxThickness, double bottomTaper,
        double surfaceRoughness, double edgeNoise, Optional<CustomLayers> customLayers) {
    public enum SurfaceMode { VANILLA_SURFACE, CUSTOM_LAYERS }

    public record CustomLayers(
            BlockState topBlock, BlockState soilBlock, BlockState rockBlock,
            int topDepth, int soilDepth, Optional<BlockState> oreBlock,
            double oreChance, int oreMinDepth, int oreMaxDepth) {}
}
```

校验规则：

- `centerY` 为 `[-64, 319]`。
- `maxThickness` 为 `[16, 256]`。
- `bottomTaper` 为 `[0.25, 4.0]`，`surfaceRoughness` 为 `[0, 32]`，`edgeNoise` 为 `[0, 1]`。
- `CUSTOM_LAYERS` 必须提供 `customLayers`，`VANILLA_SURFACE` 必须省略。
- 分层方块不能为空气；`topDepth` 为 `[1, 16]`，`soilDepth` 为 `[0, 64]`。
- `oreChance` 为 `[0, 1]`；提供矿石方块时深度范围必须非负且 `min <= max`。

Codec 使用 `BlockState.FULL_CODEC`、`StringRepresentable.fromEnum` 和 `optionalFieldOf`。

- [ ] **Step 4: 扩展 `WorldGenTemplate`**

新增 `CentralIslandSettings island` 字段，旧构造器委托为 `null`。`generator_kind` 集合加入 `central_island`。校验：

```java
boolean central = generatorKind.equals("central_island");
if (central != (island != null)) {
    return DataResult.error(() -> "island is required only for central_island templates");
}
if (central) {
    if (terrainBoundary == null || terrainBoundary.outsideMode() != TerrainBoundary.OutsideMode.VOID) {
        return DataResult.error(() -> "central_island requires a void terrain boundary");
    }
    DataResult<CentralIslandSettings> result = island.validate();
    if (result.result().isEmpty()) {
        return result.map(ignored -> this);
    }
}
```

Codec 增加 `CentralIslandSettings.CODEC.optionalFieldOf("island")`。

- [ ] **Step 5: 添加模板解析测试**

覆盖：

- `central_island + terrain_boundary + island` 正常解析。
- 缺少边界、非 `void` 边界、缺少 `island`、非浮岛模板携带 `island` 均拒绝。
- 自定义分层方块与矿石参数往返。

Run: `.\gradlew.bat test --tests 'spectrum255.rogueengine.profile.*'`

Expected: 全部通过。

## Task 2：单岛密度函数

**Files:**
- Create: `src/main/java/spectrum255/rogueengine/worldgen/CentralIslandDensityFunction.java`
- Modify: `src/main/java/spectrum255/rogueengine/worldgen/WorldGenRegistry.java`
- Create: `src/test/java/spectrum255/rogueengine/worldgen/CentralIslandDensityFunctionTest.java`

- [ ] **Step 1: 写密度失败测试**

```java
@Test
void createsOneSolidCenterAndVoidOutside() {
    DensityFunction noise = DensityFunctions.zero();
    CentralIslandDensityFunction function = new CentralIslandDensityFunction(noise, boundary, settings);
    DensitySampler sampler = function.compileSampler(null);
    assertTrue(sampler.sampleValue(SamplerContext.EMPTY_UNCACHED, 0, 128, 0) > 0.0F);
    assertTrue(sampler.sampleValue(SamplerContext.EMPTY_UNCACHED, 0, 129, 0) < 0.0F);
    assertTrue(sampler.sampleValue(SamplerContext.EMPTY_UNCACHED, 0, 96, 0) > 0.0F);
    assertEquals(-1.0F, sampler.sampleValue(SamplerContext.EMPTY_UNCACHED, 200, 128, 0));
}
```

- [ ] **Step 2: 运行测试确认类缺失**

Run: `.\gradlew.bat test --tests spectrum255.rogueengine.worldgen.CentralIslandDensityFunctionTest`

Expected: 编译失败。

- [ ] **Step 3: 实现 `CentralIslandDensityFunction`**

核心采样公式：

```java
double nx = (x - boundary.centerX()) / boundary.radiusX();
double nz = (z - boundary.centerZ()) / boundary.radiusZ();
double radius = Math.sqrt(nx * nx + nz * nz);
if (radius >= 1.0) return -1.0F;

double noiseValue = noise.sampleValue(context, x, settings.centerY(), z);
double centerBlend = Math.min(1.0, radius / 0.2);
double surface = settings.centerY()
        + settings.surfaceRoughness() * noiseValue * centerBlend
        - radius * radius * settings.maxThickness() * 0.15;
double thickness = settings.maxThickness()
        * Math.pow(Math.max(0.0, 1.0 - radius * radius), settings.bottomTaper());
double bottom = surface - thickness;
float topDensity = (float) ((surface - y + 0.5) / 3.0);
float bottomDensity = (float) ((y - bottom + 0.5) / 3.0);
return Math.max(-1.0F, Math.min(1.0F, Math.min(topDensity, bottomDensity)));
```

`edgeNoise` 在半径阈值前调制有效边缘：`effectiveRadius = radius + edgeNoise * noiseValue * 0.08`，超过 `1` 返回 `-1`。中心 `20%` 半径内平滑淡出起伏，保证中心高度稳定。

`range()` 返回 `[-1,1]`，`domainAxes()` 返回 `ALL_AXES`，`rewriteChildren` 重写 `noise`。Codec 字段为 `noise`、`boundary`、`settings`。`WorldGenRegistry` 注册 `rogueengine:central_island_density`。

- [ ] **Step 4: 运行密度测试**

Run: `.\gradlew.bat test --tests spectrum255.rogueengine.worldgen.CentralIslandDensityFunctionTest`

Expected: 中心实心、中心上方空气、底部收尖、岛外虚空均通过。

## Task 3：浮岛生成器包装

**Files:**
- Create: `src/main/java/spectrum255/rogueengine/worldgen/CentralIslandGenerator.java`
- Modify: `src/main/java/spectrum255/rogueengine/worldgen/WorldGenRegistry.java`
- Create: `src/test/java/spectrum255/rogueengine/worldgen/CentralIslandGeneratorTest.java`

- [ ] **Step 1: 写生成器失败测试**

```java
@Test
void wrapsDelegateAndReportsVoidOutside() {
    CentralIslandGenerator generator = CentralIslandGenerator.create(delegate(), boundary, settings);
    assertSame(delegate().getBiomeSource(), generator.getBiomeSource());
    assertEquals(generator.getMinY(), generator.getBaseHeight(1000, 1000,
            Heightmap.Types.WORLD_SURFACE, null, null));
    assertTrue(generator.getBaseColumn(1000, 1000, null, null).getBlock(generator.getMinY()).isAir());
    assertEquals(129, generator.spawnY());
}
```

- [ ] **Step 2: 运行测试确认类缺失**

Run: `.\gradlew.bat test --tests spectrum255.rogueengine.worldgen.CentralIslandGeneratorTest`

Expected: 编译失败。

- [ ] **Step 3: 实现 `CentralIslandGenerator`**

`create(...)`：

1. 用 `router.ridges()` 与 `router.erosion()` 组合生成二维起伏噪声。
2. 用 `new CentralIslandDensityFunction(noise, boundary, settings)` 替换 `finalDensity`。
3. `VANILLA_SURFACE` 保留基础 `materialRule`；`CUSTOM_LAYERS` 使用 `MaterialRules.state(rockBlock)` 并将 `defaultBlock` 设为岩石。
4. 构造新的 `NoiseBasedChunkGenerator` 与包装生成器。

包装生成器行为：

- 构造器校验边界为 `VOID`、设置合法。
- `buildTerrain` 对完全不与边界相交的区块直接返回，不调用 delegate。
- 相交区块调用 delegate；`CUSTOM_LAYERS` 完成后处理每列非空气方块。
- 自定义分层从岛表面向下扫描：顶部 `topDepth` 层、随后 `soilDepth` 层、其余岩石；矿石按确定性坐标哈希和 `oreChance` 替换指定深度岩石。
- `getBaseHeight/getBaseColumn` 在岛外返回虚空；岛内委托。
- `createStructures/createReferences/applyBiomeDecoration` 跳过完全岛外区块。
- `spawnY()` 返回 `centerY + 1`。
- Codec 字段为 `delegate`、`boundary`、`settings`，注册 `rogueengine:central_island`。

- [ ] **Step 4: 运行生成器测试**

Run: `.\gradlew.bat test --tests spectrum255.rogueengine.worldgen.CentralIslandGeneratorTest`

Expected: 包装、岛外虚空和出生高度通过。完整 Codec 分发由服务端冒烟验证。

## Task 4：模板解析与出生点集成

**Files:**
- Modify: `src/main/java/spectrum255/rogueengine/worldgen/RandomizationMode.java`
- Modify: `src/main/java/spectrum255/rogueengine/worldgen/WorldGenParameterResolver.java`
- Modify: `src/main/java/spectrum255/rogueengine/profile/ResolvedBoxProfile.java`
- Modify: `src/main/java/spectrum255/rogueengine/dimension/PlayerTransferService.java`
- Create: `src/test/java/spectrum255/rogueengine/profile/CentralIslandResolutionTest.java`

- [ ] **Step 1: 写解析失败测试**

覆盖：

1. `central_island` 物化为 `CentralIslandGenerator`。
2. 模板边界超出硬边界时拒绝。
3. 固定出生点在水平边界外时拒绝。
4. `central_island` 不允许噪声参数 overrides。
5. 搜索出生点目标 Y 使用 `centerY + 1`。

- [ ] **Step 2: 运行测试确认失败**

Run: `.\gradlew.bat test --tests spectrum255.rogueengine.profile.CentralIslandResolutionTest`

Expected: 新行为尚未实现。

- [ ] **Step 3: 实现解析分发**

`RandomizationMode` 增加 `CENTRAL_ISLAND`；`WorldGenParameterResolver` 对浮岛返回中性 `NoiseParameters(0,1,1,1,1,0,0,0)`，并在 overrides 非空时拒绝。

`ResolvedBoxProfile.materializeTemplate` 增加：

```java
case "central_island" -> CentralIslandGenerator.create(
        noiseGenerator, template.terrainBoundary(), template.island());
```

`applyTerrainBoundary` 对 `CentralIslandGenerator` 只做硬边界包含与固定出生点水平校验，不再套 `BoundedNoiseGenerator`。

- [ ] **Step 4: 实现搜索出生高度**

`PlayerTransferService` 在搜索分支：

- `BoundedNoiseGenerator` 使用边界中心 X/Z，Y 保持 64。
- `CentralIslandGenerator` 使用边界中心 X/Z 和 `spawnY()`。

- [ ] **Step 5: 运行集成测试**

Run: `.\gradlew.bat test --tests 'spectrum255.rogueengine.profile.*' --tests 'spectrum255.rogueengine.worldgen.*'`

Expected: 全部通过。

## Task 5：内置示例、服务端冒烟与文档

**Files:**
- Create: `src/main/resources/data/rogueengine/rogueengine/worldgen_template/central_island.json`
- Create: `src/main/resources/data/rogueengine/rogueengine/profile/central_island.json`
- Create: `src/main/resources/data/rogueengine/rogueengine/worldgen_template/central_island_layers.json`
- Create: `src/main/resources/data/rogueengine/rogueengine/profile/central_island_layers.json`
- Modify: `verification/server-smoke.ps1`
- Modify: `docs/worldgen-templates.md`
- Modify: `README.md`

- [ ] **Step 1: 添加两种地表模式示例**

- `central_island`：`VANILLA_SURFACE`，半径 `128`，中心 Y `128`，厚度 `64`，收尖 `1.5`，起伏 `4`，边缘噪声 `0.35`。
- `central_island_layers`：相同岛体，`CUSTOM_LAYERS`，草方块顶层、3 格泥土、石材岩石，2% 煤矿概率，深度 `4–32`。
- Profile 硬边界 `512`，固定出生点 `[0,129,0]`。

- [ ] **Step 2: 扩展服务端冒烟**

创建两个浮岛箱庭，验证：

- `Created box` 且 `level_stem_json` 包含 `rogueengine:central_island`。
- catalog 中硬边界为 `512`，生成器快照包含岛屿参数。
- 归档、恢复通过。
- 自定义分层示例的 `level_stem_json` 包含草方块、泥土和煤矿配置。

- [ ] **Step 3: 更新文档**

记录浮岛模板字段、单岛语义、中心稳定出生区、两种地表模式、矿石规则、边界外虚空和性能约束。

- [ ] **Step 4: 最终验证**

Run: `.\gradlew.bat clean build test`

Run: `.\verification\server-smoke.ps1`

Expected: 单元测试、两种浮岛模板创建/归档/恢复全部通过。

## 完成标准

- 一个维度只生成一个连续圆形/椭圆浮岛，边界外快速返回虚空。
- 中心出生区高度稳定，底部按收尖参数变薄。
- `vanilla_surface` 与 `custom_layers` 均可用，自定义分层支持可选矿石。
- 浮岛参数、密度函数和生成器可完整序列化进 `levelStemJson`。
- 固定/搜索出生点、硬边界和水平边界校验正确。
- 单元测试、既有服务端冒烟和两种浮岛示例冒烟全部通过。
