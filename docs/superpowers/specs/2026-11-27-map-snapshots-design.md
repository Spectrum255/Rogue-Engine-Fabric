# 地图场景快照格式、捕获与放置设计

## 目标

在现有结构地图集基础上增加可复用的场景快照来源：快照保存局部方块、方块实体和非玩家实体，可由数据包或世界受管目录加载，并支持服务端区域捕获命令。快照箱庭继续遵循创建时抽取、固定地图、刷新、归档和销毁隔离语义。

## 非目标

- 不实现完整 Minecraft 存档扫描。
- 不实现 ZIP 导入或导出。
- 不实现客户端图形界面或地图预览。
- 不开放客户端任意文件路径读取。
- 不修改源捕获区域。

## 资源与加载模型

### 数据包快照

```text
data/<pack_namespace>/rogue-engine-fabric/map_snapshot/<snapshot_id>.nbt
```

文件是 gzip 压缩的二进制 NBT。`MapSnapshotCatalog` 按需读取，不把大体积二进制注册进动态注册表；数据包重载后下一次创建自然读取新资源。

### 世界受管快照

```text
<world>/data/rogue-engine-fabric/map-snapshots/<namespace>/<path>.nbt
```

区域捕获命令写入该目录。世界文件与数据包资源使用同一解析器；两者 ID 冲突时，世界受管文件优先，便于捕获后立即测试和覆盖。

### MapScene schema v2

结构场景继续使用 schema v1：

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
  "refresh": {"triggers": ["manual"], "player_reset": "none"}
}
```

快照场景使用 schema v2：

```json
{
  "schema_version": 2,
  "source": {"kind": "snapshot", "id": "example:room_snapshot"},
  "bounds": {"min": [0, 128, 0], "max": [4, 131, 4]},
  "anchor": [0, 128, 0],
  "spawn": [2, 129, 2],
  "rotation": "none",
  "mirror": "none",
  "processors": [],
  "refresh": {"triggers": ["manual", "on_empty"], "player_reset": "full"}
}
```

`schema_version=2` 必须提供 `source`；`kind` 为 `structure` 或 `snapshot`。schema v1 自动映射为 `source.kind=structure`，旧数据包不需要迁移。

## 快照 NBT 格式

根 Tag 为 Compound，字段如下：

- `schema_version`：固定为 `1`。
- `size`：三个正整数，表示局部闭区间尺寸，局部坐标为 `[0,size.x-1] × [0,size.y-1] × [0,size.z-1]`。
- `palette`：`BlockState.CODEC` 编码的方块状态列表。
- `blocks`：稀疏方块列表，每项包含 `pos:[x,y,z]` 和 `state:<palette_index>`。空气不写入；放置前目标区域会清理为空气。
- `block_entities`：方块实体完整 NBT 列表，`x/y/z` 使用局部坐标。
- `entities`：顶层非玩家实体完整 NBT 列表，`Position` 使用局部坐标，`Rotation` 保存局部朝向。乘客保留在 `Passengers` 嵌套列表中。

实体在捕获时移除所有层级的 `UUID`，放置时由 `EntityProcessor` 生成新 UUID。只捕获没有载具的顶层实体，乘客随顶层实体递归保存和重建。`minecraft:player` 及任何玩家实体在捕获和加载时拒绝。

## 变换语义

- 局部位置通过原版 `StructureTemplate.transform` 应用镜像和旋转，再偏移 `MapScene.anchor`。
- 局部包围盒变换后必须完全位于 `MapScene.bounds`。
- 方块状态中的 `DirectionalBlock.FACING` 按镜像和旋转转换；其他方块属性保持原值。
- 实体根位置使用同一变换，实体朝向按旋转和镜像转换；乘客相对位置随载具重建。
- `MapScene.processors` 对结构来源继续使用原版处理器；快照来源不使用结构处理器，因为快照已经保存最终方块和方块实体状态。

## 捕获命令

```text
/rogue-engine map snapshot capture <snapshot_id> <from> <to>
```

- 需要 admin 权限。
- 从命令执行者当前维度读取两个角点之间的闭区间。
- 排除玩家实体，不修改源区域。
- 输出世界受管文件路径、SHA-256 和方块/方块实体/实体数量。
- 文件通过 `AtomicFiles.writeAtomic` 写入，失败不留下半成品。
- 捕获完成后立即可被同 ID 的 MapScene 引用，无需重载。

捕获限制：

- 每轴最多 `512` 格。
- 区域体积最多 `1_000_000` 格。
- 方块实体最多 `100_000`，实体最多 `100_000`。
- 压缩文件最多 `16 MiB`，解压 NBT 最多 `64 MiB`。
- 调色板最多 `4096` 项，乘客递归深度最多 `64`。

## 目录与解析

`MapSnapshotCatalog` 提供统一加载入口：

1. 将快照 ID 映射为世界受管路径并执行路径、符号链接和大小校验。
2. 若世界文件存在，优先读取。
3. 否则通过 `MinecraftServer.getResourceManager()` 打开数据包资源。
4. 使用服务器 registries 解析方块状态和实体引用。
5. 返回解析后的 `MapSnapshot` 和原始字节；原始字节的 SHA-256 作为场景源哈希。

目录不持有跨 reload 缓存。创建箱庭时读取一次并复制到实例缓存，之后修改源文件不影响已有箱庭。

## 放置与刷新

新增 `SnapshotPlacementService`，与现有结构放置服务并存：

1. 创建或刷新前校验缓存文件哈希、快照格式和变换后包围盒。
2. 将变换后区域按区块加入队列。
3. 每 tick 按 `max_chunks_per_tick` 和 `max_tick_ms` 处理有限区块。
4. 每区块先清理交集区域和区内实体，再写入稀疏方块。
5. 方块状态写入后，通过 `BlockEntity.loadStatic` 和 `LevelChunk.setBlockEntity` 恢复方块实体。
6. 所有区块完成后，按实体预算使用 `EntityType.loadEntityRecursive` 重建顶层实体和乘客，并加入维度。
7. 校验出生点后提交 `ACTIVE`。

`ScenePlacementService` 的现有任务计数预算继续用于结构放置；快照使用真正的逐区块预算。`SceneResetService` 在清理后按快照来源重新放置，仍保持同一场景 ID、抽取 seed 和刷新次数语义。

## 创建集成

`BoxCreationService.prepareMap` 根据 `MapScene.source` 分发：

- `structure`：保持现有 `StructureTemplateManager` 流程。
- `snapshot`：从 `MapSnapshotCatalog` 读取原始 NBT，写入实例缓存并计算哈希。

`SceneSelectionSnapshot` 的现有 `hashes` 对象增加可选 `source_kind` 和 `source_id`。旧快照缺少字段时默认按结构来源处理，因此刷新旧箱庭不需要迁移。创建和刷新统一通过来源分发，不再依赖当前数据包重新解析 MapScene。

## 失败与安全

- 快照格式、引用、尺寸、数量或哈希无效时，创建进入现有 `RECOVERY_REQUIRED` 路径。
- 刷新放置失败时进入 `RECOVERY_REQUIRED`，可由现有 WAL 恢复重试。
- 捕获命令失败时不覆盖已有快照；只有完整写入成功后才替换目标文件。
- 世界路径通过 `AtomicFiles.verifyManagedPath` 校验，拒绝路径穿越、符号链接和重解析点。
- 不读取客户端提供的任意路径。
- 实例缓存继续位于 `scenes/<instance_uuid>/source.nbt`，销毁时随场景目录隔离。

## 兼容性

- MapScene schema v1 和现有结构 JSON 保持可用。
- 旧 `SceneSelectionSnapshot` 默认结构来源。
- catalog、WAL、地图集抽取状态 schema 不变。
- 旧箱庭、玩家状态文件和场景缓存回退逻辑保持有效。
- 快照来源不使用结构处理器，该限制在作者指南中明确。

## 验证

### 单元测试

- MapScene v1/v2 Codec 与来源校验。
- 快照 NBT 往返、稀疏方块、方块实体和乘客实体。
- 非法尺寸、调色板索引、重复位置、玩家实体和递归深度拒绝。
- 世界/数据包加载优先级与哈希。
- 局部坐标旋转、镜像、包围盒和方块朝向变换。
- 实体 UUID 重建和乘客关系。
- 分批队列的预算与失败完成。

### 服务端冒烟

在现有结构地图冒烟之外增加快照箱庭：

- 创建快照地图并确认 `ACTIVE`。
- catalog 保存 `source_kind=snapshot` 和源 ID。
- 手动刷新成功且不改变选中地图。
- 归档和恢复后快照与刷新次数保持。

### 客户端在线验收

1. 使用管理员捕获命令截取一个小区域。
2. 创建引用该快照的 MapScene 和 Profile。
3. 进入箱庭，确认方块、容器内容和非玩家实体恢复。
4. 修改区域后刷新，确认恢复捕获时状态。
5. 确认实体 UUID 不保留源 UUID，乘客关系正常。
