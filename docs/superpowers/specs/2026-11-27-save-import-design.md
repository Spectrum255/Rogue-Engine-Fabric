# Minecraft 存档区域导入设计

## 目标

从 Minecraft 26.3 存档文件夹或 ZIP 中只读提取指定维度和闭区间区域，生成现有场景快照格式，并写入世界受管快照目录。导入成功后立即可被 MapScene schema v2 的 `source.kind=snapshot` 引用。

## 非目标

- 不实现 DataFixer 升级；只接受与当前服务端相同的 `DataVersion`。
- 不自动生成 MapScene、MapPool、模板或 Profile；导入产物是可直接引用的快照 ID。
- 不扫描整个维度；本阶段要求显式 `from/to` 区域。
- 不保存结构引用元数据；结构生成的方块仍包含在方块数据中。
- 不修改源存档，不提取玩家数据。
- 不实现 ZIP 导出。

## 命令

```text
/rogueengine map snapshot import <snapshot_id> <source> <dimension> <from> <to> <include_entities> <include_block_entities>
```

- 需要 admin 权限。
- 只允许控制台、RCON 等非玩家命令源执行，避免客户端指定服务端文件路径。
- `source` 是存档文件夹或 `.zip` 文件路径；相对路径基于服务端工作目录。
- `dimension` 使用原版维度 ID，例如 `minecraft:overworld`、`minecraft:the_nether`、`minecraft:the_end` 或自定义 `namespace:path`。
- `from/to` 使用原版方块坐标参数，闭区间且会归一化。
- 两个布尔参数分别控制非玩家实体和方块实体；容器内容随方块实体保存。

命令通过现有 `MutationGate` 执行，避免与箱庭创建、销毁等操作并发。完成后输出目标文件、SHA-256、方块、方块实体和实体数量。

## 源存档读取

### 文件夹

- 要求 `level.dat` 为普通文件，拒绝符号链接和重解析点。
- 存在 `session.lock` 时拒绝导入，避免读取正在写入的存档。
- 只读取选中维度的 `region/*.mca` 和 `entities/*.mca`。
- 维度目录按原版规则解析：
  - `minecraft:overworld`：`region/`、`entities/`
  - `minecraft:the_nether`：`DIM-1/region/`、`DIM-1/entities/`
  - `minecraft:the_end`：`DIM1/region/`、`DIM1/entities/`
  - 其他：`dimensions/<namespace>/<path>/region/`、`dimensions/<namespace>/<path>/entities/`

### ZIP

- 使用 Apache Commons Compress `ZipFile` 读取中央目录并统一校验条目，再流式提取选中文件，不整体解压。
- 只提取 `level.dat` 和选中维度的 `region/*.mca`、`entities/*.mca` 到世界临时目录。
- 所有条目先校验名称：拒绝绝对路径、盘符、反斜杠、NUL、`.`/`..` 段和 Unix 符号链接模式。
- 限制条目数、声明总大小、单文件大小、压缩比和实际提取总大小。
- 临时目录位于 `<world>/data/rogueengine/imports/<operation_uuid>/`，成功或失败都递归清理。
- ZIP 内即使包含 `session.lock` 也不提取，因此不影响导入。

## 版本与区块校验

- 读取 `level.dat` 的 `Data.DataVersion`，必须等于 `SharedConstants.getCurrentVersion().dataVersion().version()`。
- 不执行 DataFixer，不静默接受其他版本。
- 使用 `RegionFileStorage` 只读模式读取 Anvil 区域文件。
- 校验区块 NBT 的 `xPos/zPos` 与请求区块一致，`Status` 必须为 `minecraft:full`；非完整区块跳过。
- 单个区块 NBT 最大 `64 MiB`。
- 区域相交区块最多 `4096` 个。
- 缺失区块视为空气，不修改源存档。

## 方块与方块实体提取

- 使用 `Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY)` 和 `PalettedContainer.codecRW(BlockState.FULL_CODEC, strategy, Blocks.AIR.defaultBlockState())` 解析每个 section 的 `block_states`。
- section 的世界 Y 为 `section.Y * 16 + localY`。
- 只保留区域内的非空气方块，局部坐标为 `worldPos - region.min`。
- 方块状态按值去重形成调色板。
- `block_entities` 列表通过 `BlockEntity.getPosFromTag` 定位，只保留区域内且启用方块实体的条目，并把 `x/y/z` 改写为局部坐标。
- `include_block_entities=false` 时保留方块但丢弃方块实体 NBT。

## 实体提取

- 优先读取独立 `entities/*.mca` 中 `Entities` 列表；兼容完整区块 NBT 内的旧 `entities` 列表。
- 校验实体区块 `Position` 与请求区块一致。
- 收集所有实体及嵌套乘客 UUID，排除作为乘客重复保存的顶层条目。
- 拒绝 `minecraft:player` 及任何玩家乘客。
- 只保留根实体 `Pos` 位于区域内的条目，局部化所有层级 `Position`。
- 移除所有层级 `UUID`，放置时由现有快照放置器重建。
- `include_entities=false` 时完全跳过实体读取。

## 输出与原子性

- 复用 `MapSnapshot`、`MapSnapshotCodec` 和 `MapSnapshotCatalog`。
- 输出路径：

```text
<world>/data/rogueengine/map-snapshots/<namespace>/<path>.nbt
```

- 完整解析和校验完成后，使用 `AtomicFiles.writeAtomic` 一次写入。
- 写入前检查现有快照大小、场景尺寸、调色板、方块实体和实体数量限制。
- 失败不覆盖旧快照，不留下半成品注册条目。
- 导入不修改源文件夹或 ZIP。

## 限制

- ZIP 最多 `20_000` 个条目。
- ZIP 声明总大小最多 `2 GiB`，单条目最多 `256 MiB`，压缩比最多 `256`。
- 实际提取文件总大小最多 `512 MiB`。
- 文件夹选中区域文件总大小最多 `512 MiB`。
- 场景每轴最多 `512` 格，体积最多 `1_000_000` 格。
- 调色板最多 `4096`，方块实体和顶层实体各最多 `100_000`。
- 输出快照最大 `16 MiB`，解压 NBT 最大 `64 MiB`。

## 安全与兼容性

- 所有世界内路径通过 `AtomicFiles.verifyManagedPath` 校验。
- 不读取源存档玩家数据、服务端配置或任意其他文件。
- 不持久化源绝对路径，catalog 和 WAL schema 不变。
- 旧箱庭、结构地图和现有快照不受影响。
- 导入产物只增加世界受管快照，不改变动态注册表。

## 验证

### 单元测试

- ZIP 路径穿越、盘符、反斜杠、符号链接和大小限制。
- ZIP 选择性提取与临时目录清理。
- 维度目录解析和 `DataVersion` 校验。
- 合成区块 NBT 的 section、调色板和坐标解码。
- 方块实体过滤与局部化。
- 实体乘客去重、玩家拒绝、UUID 移除和局部化。
- 文件夹与 ZIP 导入输出等价。
- 失败不覆盖已有快照。

### 服务端冒烟

新增独立导入冒烟：

1. 生成一个 26.3 小型源存档 fixture。
2. 通过 RCON 执行文件夹导入和 ZIP 导入。
3. 检查世界受管快照存在且哈希一致。
4. 创建引用导入快照的 MapScene/Profile 箱庭并验证 `ACTIVE`。
5. 刷新、归档和恢复保持通过。

### 文档

更新 `docs/map-pools.md` 和 README，记录命令、版本限制、内容选项、ZIP 安全、输出路径和未实现项。
