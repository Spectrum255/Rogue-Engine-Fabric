# 随机地图集与固定场景

地图集让每个箱庭在创建时从一个确定的数据包候选集中抽取一个场景。抽取结果会固定到实例快照：普通重入、刷新、归档和恢复都不会重新抽地图；销毁并重新创建箱庭时才会重新抽取。

当前阶段支持原版二进制结构模板 `.nbt`、模组场景快照，以及从 Minecraft 26.3 存档文件夹或 ZIP 导入显式区域。客户端地图预览尚未实现。

## 资源路径

假设数据包命名空间为 `example`，模组 ID 为 `rogue-engine-fabric`：

```text
data/example/structure/room.nbt
data/example/rogue-engine-fabric/map_snapshot/room.nbt
data/example/rogue-engine-fabric/map_scene/room.json
data/example/rogue-engine-fabric/map_pool/dungeon.json
data/example/rogue-engine-fabric/worldgen_template/dungeon.json
data/example/rogue-engine-fabric/profile/dungeon.json
```

Minecraft 26.3 的资源模板目录是单数 `structure/`，文件必须是 gzip 压缩的二进制 NBT。不要把结构放在 `structures/`，也不要在数据包中使用 `.snbt`；这两种形式不会被 26.3 的原版资源模板源加载。

模组内置示例位于：

```text
data/rogue-engine-fabric/structure/example_room.nbt
data/rogue-engine-fabric/rogue-engine-fabric/map_scene/example_room.json
data/rogue-engine-fabric/rogue-engine-fabric/map_scene/example_room_alt.json
data/rogue-engine-fabric/rogue-engine-fabric/map_snapshot/example_room.nbt
data/rogue-engine-fabric/rogue-engine-fabric/map_scene/example_room_snapshot.json
data/rogue-engine-fabric/rogue-engine-fabric/map_pool/example_pool.json
data/rogue-engine-fabric/rogue-engine-fabric/map_pool/example_snapshot_pool.json
data/rogue-engine-fabric/rogue-engine-fabric/worldgen_template/map_pool.json
data/rogue-engine-fabric/rogue-engine-fabric/worldgen_template/map_pool_snapshot.json
data/rogue-engine-fabric/rogue-engine-fabric/profile/map_pool.json
data/rogue-engine-fabric/rogue-engine-fabric/profile/map_pool_snapshot.json
```

可用 `python verification/generate-example-structure.py` 重新生成内置二进制结构，用 `python verification/generate-example-snapshot.py` 重新生成内置场景快照。

## 1. 定义地图场景

`map_scene` 描述一个结构或快照如何放入箱庭。结构来源使用 schema v1：

```json
{
  "schema_version": 1,
  "structure_id": "example:room",
  "bounds": {"min": [0, 128, 0], "max": [4, 131, 4]},
  "anchor": [0, 128, 0],
  "spawn": [2, 129, 2],
  "rotation": "none",
  "mirror": "none",
  "processors": [],
  "refresh": {
    "triggers": ["manual", "on_empty"],
    "player_reset": "none"
  }
}
```

字段说明：

- `schema_version`：结构来源固定为 `1`；快照来源使用 `2`。
- `structure_id`：原版结构模板 ID，对应 `data/<namespace>/structure/<path>.nbt`。
- `bounds.min` / `bounds.max`：世界坐标中的闭区间场景边界。放置后的结构包围盒必须完全位于其中；每轴最多 `512` 格。
- `anchor`：结构模板原点放置到的世界坐标。旋转和镜像均以该原点为基准。
- `spawn`：场景出生点，必须位于 `bounds` 内。放置完成后，出生点脚下必须有实心碰撞面，脚部和头部空间不能碰撞。
- `rotation`：`none`、`clockwise_90`、`clockwise_180`、`counterclockwise_90`。
- `mirror`：`none`、`left_right`、`front_back`。
- `processors`：可选的 `minecraft:processor_list` ID 列表，按原版结构处理器语义应用。
- `refresh.triggers`：至少包含一个触发策略。
- `refresh.player_reset`：`none` 或 `full`。`full` 在每名玩家首次进入时建立独立生存状态基线，刷新后下次进入恢复生命、饥饿、饱和、耗尽、氧气、经验和状态效果；背包、末影箱和位置仍由现有策略管理。

`manual` 要求维度内没有玩家；`on_empty` 在最后一名玩家离开后执行；`on_death` 和 `on_completed` 在玩家仍在线时只记录待刷新请求，等维度清空后执行。`on_completed` 目前仅供后续玩法模块通过内部接口调用，没有公开命令。

### 场景快照来源

快照场景使用 schema v2：

```json
{
  "schema_version": 2,
  "source": {"kind": "snapshot", "id": "example:room"},
  "bounds": {"min": [0, 128, 0], "max": [4, 131, 4]},
  "anchor": [0, 128, 0],
  "spawn": [2, 129, 2],
  "rotation": "none",
  "mirror": "none",
  "processors": [],
  "refresh": {"triggers": ["manual", "on_empty"], "player_reset": "none"}
}
```

快照文件是 gzip 压缩二进制 NBT，包含 `schema_version`、局部 `size`、方块调色板、稀疏 `blocks`、`block_entities` 和顶层 `entities`。方块实体坐标和实体 `Position` 使用局部坐标；乘客保存在 `Passengers` 中。

放置时先清理目标区域，再按区块预算写入方块、方块实体和实体。实体 UUID 会重新生成，乘客关系随载具重建；玩家实体拒绝捕获和加载。快照来源不使用结构 `processors`。

管理员可从当前维度捕获区域：

```text
/rogue-engine map snapshot capture <snapshot_id> <from> <to>
```

捕获结果写入 `<world>/data/rogue-engine-fabric/map-snapshots/<namespace>/<path>.nbt`，立即可用。世界受管文件与数据包资源 ID 冲突时，世界文件优先。

### 从存档导入区域

管理员可将同一版本存档的指定维度区域转换为快照：

```text
/rogue-engine map snapshot import <snapshot_id> <source> <dimension> <from> <to> <include_entities> <include_block_entities>
```

例如：

```text
/rogue-engine map snapshot import example:imported "D:/saves/source-world" minecraft:overworld 0 128 0 64 128 64 true true
/rogue-engine map snapshot import example:imported_zip "./source-world.zip" minecraft:the_nether -32 64 -32 32 96 32 false true
```

- 命令需要 admin 权限，并且只允许控制台、RCON 等非玩家命令源执行。
- `source` 可以是存档文件夹或 `.zip`；相对路径基于服务端工作目录。路径含空格时应加引号。
- `from/to` 是闭区间方块坐标，顺序会自动归一化，不要求坐标所在区块已加载。
- `include_entities` 和 `include_block_entities` 只接受小写 `true` 或 `false`。关闭实体读取时不会读取实体区域；容器内容随方块实体保存。
- 导入是同步串行操作，会与箱庭创建、销毁等操作通过同一个 mutation gate 互斥。

维度目录按原版规则解析：

| 维度 ID | 区域目录 |
| --- | --- |
| `minecraft:overworld` | `region/`、`entities/` |
| `minecraft:the_nether` | `DIM-1/region/`、`DIM-1/entities/` |
| `minecraft:the_end` | `DIM1/region/`、`DIM1/entities/` |
| `namespace:path` | `dimensions/namespace/path/region/`、`dimensions/namespace/path/entities/` |

源存档必须处于关闭状态：存在 `session.lock` 时拒绝导入。`level.dat` 的 `Data.DataVersion` 必须等于当前 Minecraft 26.3 版本；不执行 DataFixer，也不静默接受其他版本。非完整区块跳过，缺失区块视为空气。

ZIP 只提取 `level.dat` 和选中维度的 `region/*.mca`、`entities/*.mca`。所有条目在写入前统一校验，拒绝路径穿越、盘符、反斜杠、NUL、`.`/`..` 路径段和 Unix 符号链接。临时解压目录无论成功或失败都会清理。文件夹和 ZIP 源都不会被修改。

输出仍为：

```text
<world>/data/rogue-engine-fabric/map-snapshots/<namespace>/<path>.nbt
```

命令输出目标路径、SHA-256、方块数、方块实体数和顶层实体数。完整解析和校验完成后才原子替换目标文件；失败不会覆盖已有快照。导入不会自动生成 `MapScene`、`MapPool` 或 Profile，只需像使用其他快照一样声明 schema v2 场景：

```json
{
  "schema_version": 2,
  "source": {"kind": "snapshot", "id": "example:imported"}
}
```

上例省略了 `bounds`、`anchor`、`spawn` 等必填字段，仅说明来源引用方式；完整场景定义见前文。

导入限制：

- ZIP 最多 `20_000` 个条目，声明总大小最多 `2 GiB`，单条目最多 `256 MiB`，压缩比最多 `256`。
- ZIP 实际提取总量和文件夹选中区域文件总量均最多 `512 MiB`。
- 区域最多相交 `4096` 个区块；场景每轴最多 `512` 格，体积最多 `1_000_000` 格。
- 输出快照最大 `16 MiB`，解压 NBT 最大 `64 MiB`；方块调色板最多 `4096` 种状态。
- 方块实体和顶层实体各最多 `100_000`，乘客深度最多 `64`；玩家实体拒绝导入。
- 不保存结构引用元数据，不提取玩家数据，不支持整维度扫描、DataFixer 升级或 ZIP 导出。

可用以下命令重新生成并验证最小 Anvil/ZIP fixture：

```powershell
python verification/generate-save-import-fixture.py verification/fixtures/save-import
.\gradlew.bat test --tests spectrum255.rogueengine.map.SaveImportFixtureTest
```

## 2. 定义地图集

```json
{
  "schema_version": 1,
  "entries": [
    {"scene": "example:room", "weight": 3},
    {"scene": "example:large_room", "weight": 1}
  ],
  "default_range": {"scenes": [], "tags": []},
  "repeat_policy": "cycle_no_repeat",
  "refresh": {
    "triggers": ["manual", "on_empty"],
    "player_reset": "none"
  },
  "budget": {
    "max_chunks_per_tick": 4,
    "max_tick_ms": 5,
    "max_entities": 128,
    "max_block_entities": 256
  }
}
```

字段说明：

- `entries`：至少一个条目；同一场景不能重复；`weight` 必须为正整数。
- `default_range.scenes` / `default_range.tags`：地图集默认范围。空列表表示不限制。场景 ID 与标签命中的场景合并为允许集合。
- `repeat_policy`：
  - `allow`：每次独立加权抽取，允许连续重复。
  - `cycle_no_repeat`：在当前候选范围内循环抽取，候选耗尽后进入下一轮。进度按世界和地图集持久化。
- `budget`：放置服务的保守预算。`max_chunks_per_tick` 为 `1–1024`，`max_tick_ms` 为 `1–50`，实体和方块实体上限均为 `0–100000`。
- `refresh`：当前为兼容字段；实例实际使用的刷新策略取自选中 `map_scene.refresh`。

最终候选范围依次取地图集 `default_range`、模板 `default_range` 和创建命令筛选条件的交集。若交集为空，创建失败，不会静默扩大范围。

## 3. 定义地图集生成模板

```json
{
  "schema_version": 1,
  "generator_kind": "map_pool",
  "base_stem": "minecraft:overworld",
  "map_pool": "example:dungeon",
  "default_range": {"scenes": [], "tags": []}
}
```

- `generator_kind` 必须为 `map_pool`。
- `base_stem` 是箱庭基础地形使用的已注册 `LevelStem`。
- `map_pool` 引用地图集 ID。
- `default_range` 可进一步限定模板允许抽取的场景。

地图集创建时仍使用基础地形生成器；场景结构在箱庭创建后由服务端放置任务粘贴。

## 4. 定义 Profile

Profile 使用 schema v2 引用模板：

```json
{
  "schema_version": 2,
  "dimension_type": "minecraft:overworld",
  "generator": {
    "kind": "template",
    "id": "example:dungeon"
  },
  "seed": {"mode": "random"},
  "spawn": {"mode": "fixed", "x": 2.0, "y": 129.0, "z": 2.0, "yaw": 0.0},
  "border": {
    "center_x": 0.0,
    "center_z": 0.0,
    "initial_size": 64.0,
    "warning_blocks": 8.0
  }
}
```

地图池模板会被物化为自包含生成器快照。已有箱庭不依赖之后修改的模板或地图集定义。若地图集内不同场景使用不同出生点，应确保 Profile 的固定出生点适合所有候选场景，或为不同场景组使用不同 Profile。

## 创建、查询与刷新

创建地图箱庭：

```text
/rogue-engine instance create <profile> [name]
```

创建时进一步筛选场景：

```text
/rogue-engine instance create <profile> create-map <scene1,scene2>
```

`create-map` 后的参数使用逗号分隔场景 ID，并占用剩余命令文本，因此该形式不能再追加自定义箱庭名称。省略筛选时使用地图集与模板范围的交集。

地图创建是异步的，命令返回 `Preparing box <uuid>`。使用以下命令等待结果：

```text
/rogue-engine instance info <uuid>
```

成功状态为 `ACTIVE`；失败状态为 `RECOVERY_REQUIRED`。信息中的 `map=<scene_id>` 和 `resets=<count>` 分别显示固定场景和刷新次数。

手动刷新：

```text
/rogue-engine instance reset <uuid>
```

刷新流程为 `ACTIVE -> RESETTING -> ACTIVE`：写入 `COMPLETE_RESET` WAL、清理场景边界、从实例缓存重新放置结构、检查出生点，然后增加 `reset_count`。刷新不会改变场景 ID、抽取 seed 或循环进度。

## 持久化与销毁

创建成功后，catalog 的实例 `definition.scene_snapshot` 保存地图集、选中场景、源哈希、抽取信息、边界、出生点、刷新策略和刷新次数。场景源副本位于：

```text
<world>/data/rogue-engine-fabric/scenes/<instance_uuid>/source.nbt
```

`player_reset=full` 的每玩家状态文件位于：

```text
<world>/data/rogue-engine-fabric/scenes/<instance_uuid>/players/<player_uuid>.dat
```

状态文件损坏时会取消进入；箱庭内重连恢复失败时会断开连接。

不重复循环进度位于：

```text
<world>/data/rogue-engine-fabric/map-pools/<namespace>/<pool_path>.json
```

- 普通重入保留当前地图状态。
- 刷新只恢复当前选中地图。
- 归档和恢复保留选中地图与刷新次数。
- 永久销毁时，场景缓存随维度目录和库存进入 `rogue-engine-fabric/quarantine/<operation_id>/scenes/`。
- 旧箱庭没有 `scene_snapshot`，继续按普通维度处理，不需要迁移。

## 限制与验证

- 单个缓存结构源或快照最大 `16 MiB`；快照解压 NBT 最大 `64 MiB`。
- 场景每轴最大 `512` 格；刷新清理区域最大 `1_000_000` 格。
- 快照最多 `4096` 种方块状态、`100_000` 个方块实体和 `100_000` 个顶层实体，乘客深度最多 `64`。
- 当前支持结构模板、场景快照，以及 26.3 存档文件夹/ZIP 的显式区域导入；整维度扫描、DataFixer 升级和 ZIP 导出未实现。
- 完成事件没有公开命令或玩法条件系统。

验证命令：

```powershell
$env:JAVA_HOME='<JDK 25 home>'
.\gradlew.bat clean build test
.\verification\server-smoke.ps1
.\verification\save-import-smoke.ps1
```

服务端冒烟覆盖结构地图和快照地图的创建、来源元数据、手动刷新、重置次数持久化、归档和恢复。存档导入冒烟覆盖文件夹/ZIP 等价导入、哈希与计数、导入快照创建箱庭、刷新、归档和恢复。单元测试覆盖资源加载、快照 NBT、结构边界、坐标变换、实体重建、确定性抽取、范围交集、不重复循环、WAL 恢复、场景缓存隔离、ZIP 安全、Anvil 解码和导入原子性。
