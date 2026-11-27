# 世界生成模板数据包指南

本文档对应 Rogue Engine Fabric 的世界生成核心阶段，适用于 Minecraft 26.3、Fabric Loader 0.20.1 和 Java 25+。

## 1. 功能概览

模组新增动态注册表 `rogue-engine-fabric:worldgen_template`，允许数据包定义可复现的世界生成参数模板。Profile 的 `schema_version` 支持 1 和 2：

- schema 1：继续使用原有 `stem` 或 `inline` 生成器。
- schema 2：可使用 `generator.kind=template` 引用世界生成模板。

当前支持三类模板：

| `generator_kind` | 行为 |
|---|---|
| `noise_chaos` | 随机化密度偏移、密度尺度、山脊/大陆/侵蚀尺度、气候偏移和海平面偏移。 |
| `full_chaos` | 在噪声随机化基础上混合密度路由、交换温度/植被输入并反转山脊信号，产生更怪异的地形。 |
| `preset_chaos` | 从模板声明的多组参数预设中选择一组；可在 profile 中用 `preset` 指定名称。 |

所有随机化都使用实例 seed、模板内容哈希和模板 ID 确定性计算。相同输入得到相同参数，不同 seed 产生可复现差异。

## 2. 文件路径

模板路径：

```text
data/<pack_namespace>/rogue-engine-fabric/worldgen_template/<template_id>.json
```

Profile 路径：

```text
data/<pack_namespace>/rogue-engine-fabric/profile/<profile_id>.json
```

`<pack_namespace>` 是数据包命名空间，第二段 `rogue-engine-fabric` 是模组动态注册表的命名空间，不能拼成 `rouge-engine-fabric`。

## 3. 模板格式

```json
{
  "schema_version": 1,
  "generator_kind": "noise_chaos",
  "base_stem": "minecraft:overworld",
  "ranges": {
    "density_offset": {"min": -0.2, "max": 0.2},
    "density_scale": {"min": 0.8, "max": 1.2},
    "ridge_scale": {"min": 0.8, "max": 1.2},
    "continent_scale": {"min": 0.8, "max": 1.2},
    "erosion_scale": {"min": 0.8, "max": 1.2},
    "temperature_shift": {"min": -0.1, "max": 0.1},
    "vegetation_shift": {"min": -0.1, "max": 0.1},
    "sea_level_offset": {"min": -8, "max": 8}
  },
  "presets": []
}
```

### 字段约束

- `schema_version`：当前必须为 1。
- `generator_kind`：`noise_chaos`、`full_chaos` 或 `preset_chaos`。
- `base_stem`：必须引用已注册的 `LevelStem`。当前模板要求基础生成器为 `NoiseBasedChunkGenerator`，且其 `DimensionType` 必须与 profile 的 `dimension_type` 一致。
- `ranges`：所有字段必填。
  - `density_offset`：`[-1.0, 1.0]`。
  - `density_scale`、`ridge_scale`、`continent_scale`、`erosion_scale`：`(0.0, 4.0]`。
  - `temperature_shift`、`vegetation_shift`：`[-1.0, 1.0]`。
  - `sea_level_offset`：`[-64, 64]`。
  - 每个范围的 `min` 必须小于或等于 `max`，且不能为 NaN 或无穷值。
- `presets`：
  - `noise_chaos` 和 `full_chaos` 可以为空。
  - `preset_chaos` 至少需要一个预设。
  - 预设名称不能为空且必须唯一。
  - 预设参数必须全部位于对应范围内。

### 模板地形边界与硬世界边界

模板可额外声明 `terrain_boundary`，让边界外不再生成原版地表：

```json
{
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

- `shape` 支持 `circle` 和 `ellipse`。圆形要求两个半径相等；椭圆使用独立的 X/Z 半径。
- 半径范围为 `16–1_000_000`，中心坐标必须有限。
- `outside_mode=void` 时边界外为空气；`bedrock_floor` 时在维度最低 Y 铺设基岩底层。
- 边界必须完整位于 Profile 的硬世界边界内。固定出生点必须位于模板边界内；搜索出生点以模板边界中心为搜索中心。
- Profile 的 `border` 仍是独立的原版 `WorldBorder`，上限为 `WorldBorder.MAX_SIZE`，可使用设计默认的 `30_000_000` 规模。
- 模板边界和硬边界都会随生成器快照持久化；已有实例不受模板修改影响。

### 中央浮岛

`generator_kind=central_island` 只生成一个连续圆形/椭圆浮岛，岛外始终为空气。它必须声明 `outside_mode=void` 的 `terrain_boundary`，并通过 `island` 配置岛体：

```json
{
  "generator_kind": "central_island",
  "terrain_boundary": {
    "center_x": 0.0,
    "center_z": 0.0,
    "radius_x": 128.0,
    "radius_z": 128.0,
    "shape": "circle",
    "outside_mode": "void"
  },
  "island": {
    "surface_mode": "vanilla_surface",
    "center_y": 128,
    "max_thickness": 64,
    "bottom_taper": 1.5,
    "surface_roughness": 4.0,
    "edge_noise": 0.35
  }
}
```

字段说明：

- `center_y`：岛中心地表高度，固定出生点应站在 `center_y + 1`。
- `max_thickness`：中心最大厚度，范围 `16–256`。
- `bottom_taper`：底部收尖程度，范围 `0.25–4.0`；越大边缘越薄。
- `surface_roughness`：地表起伏幅度，范围 `0–32`；中心 20% 半径内平滑淡出，保证出生区稳定。
- `edge_noise`：边缘噪声强度，范围 `0–1`，只改变单岛外缘，不生成额外岛屿。
- `surface_mode=vanilla_surface`：保留基础 stem 的生物群系与地表规则。
- `surface_mode=custom_layers`：使用 `custom_layers` 替换顶层、土层和岩石，并可配置确定性矿石。

自定义分层示例：

```json
"custom_layers": {
  "top_block": {"id": "minecraft:grass_block"},
  "soil_block": {"id": "minecraft:dirt"},
  "rock_block": {"id": "minecraft:stone"},
  "top_depth": 1,
  "soil_depth": 3,
  "ore_block": {"id": "minecraft:coal_ore"},
  "ore_chance": 0.02,
  "ore_min_depth": 4,
  "ore_max_depth": 32
}
```

浮岛模板不允许噪声参数 `overrides`。边界必须位于硬世界边界内；固定出生点必须位于水平边界内。搜索出生点使用边界中心与 `center_y + 1`。

## 4. 预设随机模板

`preset_chaos` 的 `presets` 示例：

```json
{
  "name": "wild",
  "parameters": {
    "density_offset": 0.25,
    "density_scale": 1.25,
    "ridge_scale": 1.4,
    "continent_scale": 0.8,
    "erosion_scale": 1.1,
    "temperature_shift": 0.15,
    "vegetation_shift": -0.1,
    "sea_level_offset": 4
  }
}
```

Profile 不指定 `preset` 时按 seed 自动选择；指定时直接选择对应预设。未知预设会在创建箱庭前被拒绝。

## 5. Schema v2 Profile

```json
{
  "schema_version": 2,
  "dimension_type": "minecraft:overworld",
  "generator": {
    "kind": "template",
    "id": "rogue-engine-fabric:noise_chaos",
    "overrides": {
      "density_offset": 0.1
    }
  },
  "seed": {"mode": "random"},
  "spawn": {"mode": "search", "radius": 32},
  "border": {
    "center_x": 0.0,
    "center_z": 0.0,
    "initial_size": 512.0,
    "warning_blocks": 16.0
  }
}
```

`overrides` 支持模板参数的同名数值字段。未知字段、越界值、非有限值和非法 `sea_level_offset` 会被拒绝。

## 6. 内置内容

模组内置以下模板：

- `rogue-engine-fabric:noise_chaos`
- `rogue-engine-fabric:full_chaos`
- `rogue-engine-fabric:preset_chaos`
- `rogue-engine-fabric:bounded_noise`（圆形 `void` 边界示例）
- `rogue-engine-fabric:central_island`（原版地表浮岛）
- `rogue-engine-fabric:central_island_layers`（自定义分层与煤矿浮岛）

同时提供同名 profile，可直接使用：

```text
/rogue-engine instance create rogue-engine-fabric:noise_chaos Noise Box
/rogue-engine instance create rogue-engine-fabric:full_chaos Full Chaos Box
/rogue-engine instance create rogue-engine-fabric:preset_chaos Preset Box
/rogue-engine instance create rogue-engine-fabric:bounded_noise Bounded Box
/rogue-engine instance create rogue-engine-fabric:central_island Island Box
/rogue-engine instance create rogue-engine-fabric:central_island_layers Layered Island Box
```

## 7. 快照与兼容性

创建箱庭时，模组会：

1. 读取模板并计算模板内容 SHA-256 哈希。
2. 根据实例 seed 解析具体参数。
3. 构建具体 `NoiseBasedChunkGenerator`。
4. 将 profile 物化为 `generator.kind=inline`。
5. 把完整生成器写入 `catalog-v1.json` 的 `profile_json` 和 `level_stem_json`。

因此，创建后修改、删除或卸载原模板不会改变已有箱庭。归档、恢复和崩溃恢复使用已保存的具体生成器。

原有 schema 1 profile 与已有 `catalog-v1.json` 继续可读，不需要迁移。

## 8. 当前限制

- 模板基础生成器目前仅支持 `NoiseBasedChunkGenerator`。
- `full_chaos` 当前随机化密度与气候相关参数，不会任意替换所有原版生成器组件。
- 可选 Mod Menu 配置界面、本地数据包工具与地图预览已实现。
- 不保证 Minecraft 26.4 或其他版本兼容。

## 9. 验证

```powershell
$env:JAVA_HOME='<JDK 25+ home>'
.\gradlew.bat build test
.\verification\server-smoke.ps1
```

服务端冒烟测试会加载三类随机模板、`bounded_noise` 和两种浮岛模板，分别创建箱庭，检查 profile 已物化为内联生成器、边界/岛屿参数与硬边界已写入快照，并完成归档与恢复。
