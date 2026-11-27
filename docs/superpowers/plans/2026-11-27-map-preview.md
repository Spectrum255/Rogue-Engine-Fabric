# 地图预览 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 Mod Menu 配置界面中增加地图预览，支持数据包/世界受管快照与原版结构 NBT 的俯视高度图、旋转和镜像切换。

**Architecture:** common 层 `MapPreviewModel` 将方块列表转换为俯视高度与 MapColor；client 层 `MapPreviewLoader` 从资源管理器或单机世界目录读取快照/结构；`MapPreviewScreen` 负责控件和缩放渲染。预览不创建额外世界，不加载方块实体或实体。

**Tech Stack:** Java 25、Minecraft 26.3、Fabric Loader 0.20.1、Mod Menu 21.0.1、JUnit 5、Gradle 9.8.2。

**设计依据：** 已批准的客户端配置与地图预览设计。

**工作区说明：** 无 Git 元数据，不安排 commit。

---

## 已核对的 26.3 API

- `BlockBehaviour.BlockStateBase.getMapColor(BlockGetter, BlockPos)`。
- `EmptyBlockGetter.INSTANCE` 可在无世界上下文时取得 MapColor。
- `MapColor.calculateARGBColor(MapColor.Brightness)`。
- `StructureTemplate()`、`load(HolderGetter<Block>, CompoundTag)`、`getSize()`、`filterBlocks(BlockPos, StructurePlaceSettings, Block, boolean)`。
- `BuiltInRegistries.BLOCK` 可作为结构加载的 `HolderGetter<Block>`。
- `Minecraft.getResourceManager()`、`getSingleplayerServer()`、`gameDirectory`。
- `MapSnapshotCatalog` 已支持世界文件优先、数据包回退。
- `SnapshotTransform.transformPos` 已支持旋转/镜像。

## Task 1：俯视预览模型

**Files:**
- Create: `src/main/java/spectrum255/rogueengine/map/MapPreviewModel.java`
- Create: `src/test/java/spectrum255/rogueengine/map/MapPreviewModelTest.java`

- [x] **Step 1: 写失败测试**

```java
@Test
void buildsTopDownHeightmapAndColor() {
    MapSnapshot snapshot = new MapSnapshot(1, new Vec3i(2, 1, 2),
            List.of(Blocks.STONE.defaultBlockState(), Blocks.GRASS_BLOCK.defaultBlockState()),
            List.of(new MapSnapshot.SnapshotBlock(new BlockPos(0, 0, 0), 0),
                    new MapSnapshot.SnapshotBlock(new BlockPos(1, 0, 1), 1)),
            List.of(), List.of());
    MapPreviewModel model = MapPreviewModel.fromSnapshot(snapshot, Rotation.NONE, Mirror.NONE);

    assertEquals(2, model.width());
    assertEquals(2, model.depth());
    assertEquals(0, model.heightAt(0, 0));
    assertEquals(0, model.heightAt(1, 1));
    assertEquals(MapColor.STONE.calculateARGBColor(MapColor.Brightness.HIGH), model.colorAt(0, 0));
    assertEquals(MapColor.GRASS.calculateARGBColor(MapColor.Brightness.HIGH), model.colorAt(1, 1));
}
```

- [x] **Step 2: 运行测试确认类缺失**

Run: `.\gradlew.bat test --tests spectrum255.rogueengine.map.MapPreviewModelTest`

Expected: 编译失败。

- [x] **Step 3: 实现 `MapPreviewModel`**

```java
public record MapPreviewModel(int width, int depth, int[] heights, int[] colors) {
    public record PreviewBlock(BlockPos pos, BlockState state) {}

    public int heightAt(int x, int z)
    public int colorAt(int x, int z)
    public static MapPreviewModel fromSnapshot(MapSnapshot snapshot, Rotation rotation, Mirror mirror)
    public static MapPreviewModel fromBlocks(Vec3i size, List<PreviewBlock> blocks, Rotation rotation, Mirror mirror)
}
```

实现要求：

- 对每个非空气方块执行 `SnapshotTransform.transformPos(pos, rotation, mirror, BlockPos.ZERO)`。
- 计算变换后 X/Z 最小/最大值，得到预览宽深。
- 同一 X/Z 只保留最高非空气方块。
- 颜色使用 `state.getMapColor(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).calculateARGBColor(MapColor.Brightness.HIGH)`。
- 空列高度为 `Integer.MIN_VALUE`、颜色为 `0`。
- 校验数组长度与 `width * depth` 一致，最大边长为 512。

- [x] **Step 4: 添加旋转/镜像测试**

验证 `Rotation.CLOCKWISE_90` 交换宽深，`Mirror.LEFT_RIGHT` 反转 X；空列保持透明。

- [x] **Step 5: 运行模型测试**

Run: `.\gradlew.bat test --tests spectrum255.rogueengine.map.MapPreviewModelTest`

Expected: 高度图、颜色、旋转和镜像测试通过。

## Task 2：客户端快照与结构加载器

**Files:**
- Create: `src/client/java/spectrum255/rogueengine/client/MapPreviewLoader.java`

- [x] **Step 1: 实现资源路径读取**

快照资源 ID：

```java
Identifier.fromNamespaceAndPath(id.getNamespace(),
        "rogue-engine-fabric/map_snapshot/" + id.getPath() + ".nbt")
```

结构资源 ID：

```java
Identifier.fromNamespaceAndPath(id.getNamespace(), "structure/" + id.getPath() + ".nbt")
```

使用 `Minecraft.getInstance().getResourceManager().getResource(resourceId)`，通过 `Resource.open()` 读取字节。

- [x] **Step 2: 实现世界受管快照回退**

若 `minecraft.getSingleplayerServer()` 非空，通过 `MinecraftServerAccessor` 取得 `LevelResource.ROOT`，构造 `MapSnapshotCatalog`；否则使用游戏目录下不存在的临时根路径，仅读取数据包资源。

```java
public static MapPreviewModel loadSnapshot(Identifier id, Rotation rotation, Mirror mirror) throws IOException
```

- [x] **Step 3: 实现结构加载**

```java
public static MapPreviewModel loadStructure(Identifier id, Rotation rotation, Mirror mirror) throws IOException
```

步骤：

1. 读取并 gzip 解压结构 NBT。
2. `StructureTemplate template = new StructureTemplate(); template.load(BuiltInRegistries.BLOCK, tag);`
3. 使用 `filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), Blocks.AIR, false)` 获取方块。
4. 转换为 `MapPreviewModel.PreviewBlock` 并调用 `fromBlocks`。

- [x] **Step 4: 编译客户端**

Run: `.\gradlew.bat compileClientJava`

Expected: 资源加载、世界目录回退和结构解析编译通过。

## Task 3：预览界面与主界面入口

**Files:**
- Create: `src/client/java/spectrum255/rogueengine/client/screen/MapPreviewScreen.java`
- Modify: `src/client/java/spectrum255/rogueengine/client/RogueEngineConfigScreen.java`

- [x] **Step 1: 实现 `MapPreviewScreen`**

控件：

- 地图 ID 输入框。
- 来源按钮：`snapshot` / `structure`。
- 旋转按钮：`none` / `clockwise_90` / `clockwise_180` / `counterclockwise_90`。
- 镜像按钮：`none` / `left_right` / `front_back`。
- 加载按钮。
- 完成按钮。

状态：

```java
private MapPreviewModel model;
private String status = "";
```

加载时根据来源调用 `MapPreviewLoader`，异常写入状态栏。

- [x] **Step 2: 实现俯视渲染**

在 `extractRenderState` 中：

- 可用区域为 `x=20..width-20`、`y=60..height-60`。
- `cell = max(1, min(availableWidth / model.width, availableHeight / model.depth))`。
- 预览居中。
- 每个非空列用 `graphics.fill(x, y, x + cell, y + cell, color)` 绘制。
- 空列使用深灰背景或留空。
- 在底部状态栏显示 `width × depth`、来源和错误。

- [x] **Step 3: 接入主界面**

`RogueEngineConfigScreen` 增加“地图预览”按钮，打开 `MapPreviewScreen(this, state)`。

- [x] **Step 4: 编译客户端**

Run: `.\gradlew.bat compileClientJava`

Expected: 预览界面与主界面入口编译通过。

## Task 4：本地化、文档与验证

**Files:**
- Modify: `src/client/resources/assets/rogue-engine-fabric/lang/en_us.json`
- Modify: `src/client/resources/assets/rogue-engine-fabric/lang/zh_cn.json`
- Modify: `src/client/resources/assets/rogue-engine-fabric/lang/zh_tw.json`
- Modify: `docs/client-tools.md`
- Modify: `README.md`

- [x] **Step 1: 添加翻译**

新增 `rogue-engine-fabric.config.preview`、`map_id`、`source`、`rotation`、`mirror`、`load`、`preview_loaded` 等键，三种语言占位符一致。

- [x] **Step 2: 更新文档**

记录：

- 预览入口和来源类型。
- 数据包资源路径与世界受管快照路径。
- 旋转/镜像语义。
- 预览仅显示俯视高度/MapColor，不预览实体、方块实体或处理器结果。
- 专用服务器客户端只能预览数据包资源，不能读取服务器世界文件。

- [x] **Step 3: 全量验证**

Run: `.\gradlew.bat clean build test`

Run: `.\verification\server-smoke.ps1`

Expected: 单元测试、客户端编译、服务端冒烟全部通过。

## 执行记录

- 已实现 `MapPreviewModel`、`MapPreviewLoader`、`MapPreviewScreen` 和 Mod Menu 入口。
- 在线验收顺带修复 `SnapshotCaptureService` 将绝对方块坐标写入局部快照的缺陷，并新增回归测试。
- `clean build test`、`server-smoke.ps1`、`save-import-smoke.ps1` 和 `client-acceptance.ps1` 均通过。

## 完成标准

- 可从 Mod Menu 打开地图预览。
- 快照和结构 NBT 都能生成俯视高度图。
- 旋转和镜像会真实改变预览方向与尺寸。
- 单机世界受管快照优先于数据包资源。
- 预览不创建额外世界，不修改源数据。
- 单元测试、客户端编译和现有服务端冒烟全部通过。
