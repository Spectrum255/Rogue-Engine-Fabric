# 地图场景快照实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 增加单文件场景快照、数据包/世界目录加载、服务端区域捕获、逐区块放置和创建刷新集成。

**Architecture:** `MapScene` schema v2 用 `source` 分发结构或快照；`MapSnapshotCatalog` 按需解析二进制 NBT；捕获和放置分别使用独立服务，放置按真实区块预算执行。实例缓存和 catalog/WAL schema 保持不变。

**Tech Stack:** Java 25、Minecraft 26.3、Fabric Loader 0.20.1、Fabric API、JUnit 5、Gradle。

**设计依据：** `docs/superpowers/specs/2026-11-27-map-snapshots-design.md`

**工作区说明：** 无 Git 元数据，不安排 commit。

---

## 已核对 API

- `BlockState.FULL_CODEC`、`DirectionalBlock.FACING`。
- `StructureTemplate.transform(BlockPos, Mirror, Rotation, BlockPos)`。
- `LevelChunk.getBlockEntityNbtForSaving`、`BlockEntity.loadStatic`、`LevelChunk.setBlockEntity`。
- `Entity.save(ValueOutput)`、`EntityType.loadEntityRecursive`、`EntitySpawnReason.LOAD`、`EntityProcessor`。
- `TagValueOutput/TagValueInput`、`NbtIo.readCompressed/writeCompressed`。
- `MinecraftServer.getResourceManager()`、`registryAccess()`。

## Task 1：MapScene schema v2 与来源模型

**Files:**
- Modify: `src/main/java/spectrum255/rogueengine/map/MapScene.java`
- Modify: `src/main/java/spectrum255/rogueengine/map/MapSceneCodec.java`
- Modify: `src/test/java/spectrum255/rogueengine/map/MapSceneCodecTest.java`

- [ ] **Step 1: 增加失败测试**

```java
@Test
void parsesSnapshotSourceV2() {
	JsonElement json = JsonParser.parseString("""
			{
			  "schema_version": 2,
			  "source": {"kind": "snapshot", "id": "example:room"},
			  "bounds": {"min": [0, 128, 0], "max": [4, 131, 4]},
			  "anchor": [0, 128, 0],
			  "spawn": [2, 129, 2],
			  "rotation": "none",
			  "mirror": "none",
			  "refresh": {"triggers": ["manual"], "player_reset": "none"}
			}
			""");
	MapScene scene = MapSceneCodec.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
	assertEquals(MapScene.SourceKind.SNAPSHOT, scene.source().kind());
	assertEquals(Identifier.parse("example:room"), scene.source().id());
}

@Test
void rejectsV2WithoutSource() {
	JsonElement json = JsonParser.parseString("""
			{
			  "schema_version": 2,
			  "bounds": {"min": [0, 128, 0], "max": [4, 131, 4]},
			  "anchor": [0, 128, 0],
			  "spawn": [2, 129, 2],
			  "rotation": "none",
			  "mirror": "none",
			  "refresh": {"triggers": ["manual"], "player_reset": "none"}
			}
			""");
	assertTrue(MapSceneCodec.CODEC.parse(JsonOps.INSTANCE, json).result().isEmpty());
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `.\gradlew.bat test --tests spectrum255.rogueengine.map.MapSceneCodecTest`

Expected: 缺少 `Source`/`SourceKind`，v2 JSON 解析失败。

- [ ] **Step 3: 修改 MapScene**

将 `Identifier structureId` 组件替换为 `Source source`，保留旧构造器：

```java
public MapScene(int schemaVersion, Identifier structureId, SceneBounds bounds, BlockPos anchor,
		BlockPos spawn, Rotation rotation, Mirror mirror, List<Identifier> processorIds, RefreshPolicy refresh) {
	this(schemaVersion, new Source(SourceKind.STRUCTURE, structureId), bounds, anchor, spawn,
			rotation, mirror, processorIds, refresh);
}

public Identifier structureId() {
	return source.kind() == SourceKind.STRUCTURE ? source.id() : null;
}

public Identifier snapshotId() {
	return source.kind() == SourceKind.SNAPSHOT ? source.id() : null;
}

public enum SourceKind implements StringRepresentable {
	STRUCTURE("structure"), SNAPSHOT("snapshot");
	public static final Codec<SourceKind> CODEC = StringRepresentable.fromEnum(SourceKind::values);
	@Override public String getSerializedName() { return name().toLowerCase(java.util.Locale.ROOT); }
}

public record Source(SourceKind kind, Identifier id) {
	public Source {
		Objects.requireNonNull(kind, "kind");
		Objects.requireNonNull(id, "id");
	}
}
```

`validate` 要求 schema 1 为 `STRUCTURE`，schema 2 可为两种来源，其余字段校验保持不变。

- [ ] **Step 4: 修改 Codec**

使用私有 `SceneFields` record 复用公共字段，构造 `Codec.either(v1Codec, v2Codec)`：

```java
private record SceneFields(SceneBounds bounds, BlockPos anchor, BlockPos spawn,
		Rotation rotation, Mirror mirror, List<Identifier> processors, RefreshPolicy refresh) { }

private static final Codec<Source> SOURCE_CODEC = RecordCodecBuilder.create(instance -> instance.group(
		MapScene.SourceKind.CODEC.fieldOf("kind").forGetter(MapScene.Source::kind),
		Identifier.CODEC.fieldOf("id").forGetter(MapScene.Source::id))
		.apply(instance, MapScene.Source::new));
```

v1 Codec 读取 `schema_version=1` 和 `structure_id`；v2 Codec 读取 `schema_version=2` 和 `source`。两侧都映射到 `MapScene`，编码时按 `schemaVersion` 选择左侧或右侧。

- [ ] **Step 5: 运行测试确认通过**

Run: `.\gradlew.bat test --tests spectrum255.rogueengine.map.MapSceneCodecTest`

Expected: v1 旧样例、v2 快照和非法 v2 测试通过。

## Task 2：快照数据模型与 NBT Codec

**Files:**
- Create: `src/main/java/spectrum255/rogueengine/map/MapSnapshot.java`
- Create: `src/main/java/spectrum255/rogueengine/map/MapSnapshotCodec.java`
- Create: `src/test/java/spectrum255/rogueengine/map/MapSnapshotCodecTest.java`

- [ ] **Step 1: 编写失败测试**

```java
@Test
void roundTripsSparseBlocksBlockEntitiesAndEntities() {
	RegistryAccess registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
	CompoundTag blockEntity = new CompoundTag();
	blockEntity.putString("id", "minecraft:chest");
	blockEntity.putInt("x", 1);
	blockEntity.putInt("y", 0);
	blockEntity.putInt("z", 1);
	CompoundTag entity = new CompoundTag();
	entity.putString("id", "minecraft:item");
	entity.put("Pos", new net.minecraft.nbt.ListTag());
	MapSnapshot snapshot = new MapSnapshot(1, new Vec3i(3, 2, 3),
			List.of(net.minecraft.world.level.block.Blocks.STONE.defaultBlockState()),
			List.of(new MapSnapshot.SnapshotBlock(new BlockPos(1, 0, 1), 0)),
			List.of(blockEntity), List.of(entity));

	CompoundTag tag = MapSnapshotCodec.write(snapshot, registries);
	MapSnapshot restored = MapSnapshotCodec.read(tag, registries);

	assertEquals(snapshot.size(), restored.size());
	assertEquals(snapshot.blocks(), restored.blocks());
	assertEquals(snapshot.blockEntities(), restored.blockEntities());
	assertEquals(snapshot.entities(), restored.entities());
}

@Test
void rejectsDuplicateBlockPositions() {
	assertThrows(IllegalArgumentException.class, () -> new MapSnapshot(1, new Vec3i(2, 1, 1),
			List.of(Blocks.STONE.defaultBlockState()),
			List.of(new MapSnapshot.SnapshotBlock(BlockPos.ZERO, 0),
					new MapSnapshot.SnapshotBlock(BlockPos.ZERO, 0)), List.of(), List.of()));
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `.\gradlew.bat test --tests spectrum255.rogueengine.map.MapSnapshotCodecTest`

Expected: 缺少 `MapSnapshot` 和 Codec。

- [ ] **Step 3: 实现模型**

```java
public record MapSnapshot(int schemaVersion, Vec3i size, List<BlockState> palette,
		List<SnapshotBlock> blocks, List<CompoundTag> blockEntities, List<CompoundTag> entities) {
	public MapSnapshot {
		if (schemaVersion != 1 || size.getX() < 1 || size.getY() < 1 || size.getZ() < 1
				|| size.getX() > 512 || size.getY() > 512 || size.getZ() > 512
				|| (long) size.getX() * size.getY() * size.getZ() > 1_000_000L
				|| palette.isEmpty() || palette.size() > 4096 || blocks.size() > 1_000_000
				|| blockEntities.size() > 100_000 || entities.size() > 100_000) {
			throw new IllegalArgumentException("Invalid map snapshot dimensions or counts");
		}
		palette = List.copyOf(palette);
		blocks = List.copyOf(blocks);
		blockEntities = blockEntities.stream().map(CompoundTag::copy).toList();
		entities = entities.stream().map(CompoundTag::copy).toList();
		if (blocks.stream().map(SnapshotBlock::pos).distinct().count() != blocks.size()) {
			throw new IllegalArgumentException("Duplicate map snapshot block position");
		}
	}

	public BoundingBox localBounds() {
		return new BoundingBox(0, 0, 0, size.getX() - 1, size.getY() - 1, size.getZ() - 1);
	}

	public record SnapshotBlock(BlockPos pos, int paletteIndex) {
		public SnapshotBlock {
			Objects.requireNonNull(pos, "pos");
		}
	}
}
```

模型构造后由 Codec 校验每个 `paletteIndex` 和局部坐标；实体递归拒绝 `minecraft:player`，深度上限 `64`。

- [ ] **Step 4: 实现 Codec**

写入：

```java
root.putInt("schema_version", 1);
root.put("size", intList(size));
ListTag palette = new ListTag();
statePalette.forEach(state -> palette.add(BlockState.FULL_CODEC.encodeStart(
		RegistryOps.create(NbtOps.INSTANCE, registries), state).result().orElseThrow()));
root.put("palette", palette);
```

`blocks` 写入 `pos` 和 `state`；`block_entities`、`entities` 原样复制 CompoundTag。读取时使用 `BlockState.FULL_CODEC.parse`，校验坐标位于 `localBounds`、索引有效、方块实体坐标匹配方块、实体 ID 非玩家。

- [ ] **Step 5: 运行测试确认通过**

Run: `.\gradlew.bat test --tests spectrum255.rogueengine.map.MapSnapshotCodecTest`

Expected: 往返和重复位置测试通过。

## Task 3：快照目录、世界路径与哈希

**Files:**
- Create: `src/main/java/spectrum255/rogueengine/map/MapSnapshotCatalog.java`
- Create: `src/test/java/spectrum255/rogueengine/map/MapSnapshotCatalogTest.java`

- [ ] **Step 1: 编写失败测试**

```java
@Test
void loadsWorldSnapshotAndComputesHash(@TempDir Path world) throws IOException {
	MapSnapshot snapshot = snapshot();
	Path file = world.resolve("data/rogueengine/map-snapshots/example/room.nbt");
	Files.createDirectories(file.getParent());
	NbtIo.writeCompressed(MapSnapshotCodec.write(snapshot, registries()), file);
	MapSnapshotCatalog catalog = new MapSnapshotCatalog(world, registries(), id -> {
		throw new AssertionError("Data pack should not be read when world file exists");
	});

	MapSnapshotCatalog.Loaded loaded = catalog.load(Identifier.parse("example:room")).orElseThrow();

	assertEquals(snapshot.size(), loaded.snapshot().size());
	assertEquals(64, loaded.hash().length());
}
```

`MapSnapshotCatalog` 构造器接受 `Path worldRoot`、`HolderLookup.Provider registries` 和 `Function<Identifier, Optional<byte[]>> dataPackReader`，便于测试；Runtime 包装服务器 ResourceManager。

- [ ] **Step 2: 运行测试确认失败**

Run: `.\gradlew.bat test --tests spectrum255.rogueengine.map.MapSnapshotCatalogTest`

Expected: 缺少目录类。

- [ ] **Step 3: 实现目录**

```java
public final class MapSnapshotCatalog {
	private static final int MAX_FILE_BYTES = 16 * 1024 * 1024;
	private static final int MAX_NBT_BYTES = 64 * 1024 * 1024;
	private final Path worldRoot;
	private final HolderLookup.Provider registries;
	private final Function<Identifier, Optional<byte[]>> dataPackReader;

	public Optional<Loaded> load(Identifier id) throws IOException {
		Path worldFile = worldRoot.resolve("data/rogueengine/map-snapshots")
				.resolve(id.getNamespace()).resolve(id.getPath() + ".nbt");
		AtomicFiles.verifyManagedPath(worldRoot, worldFile);
		byte[] bytes = Files.exists(worldFile, LinkOption.NOFOLLOW_LINKS)
				? readWorldFile(worldFile) : readDataPack(id);
		if (bytes == null) return Optional.empty();
		CompoundTag tag = NbtIo.readCompressed(new ByteArrayInputStream(bytes), NbtAccounter.create(MAX_NBT_BYTES));
		return Optional.of(new Loaded(MapSnapshotCodec.read(tag, registries), bytes, sha256(bytes)));
	}

	private byte[] readWorldFile(Path path) throws IOException {
		if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.size(path) > MAX_FILE_BYTES) {
			throw new IOException("Invalid map snapshot file: " + path);
		}
		return Files.readAllBytes(path);
	}

	private byte[] readDataPack(Identifier id) {
		return dataPackReader.apply(id).filter(bytes -> bytes.length > 0 && bytes.length <= MAX_FILE_BYTES)
				.orElse(null);
	}

	private static String sha256(byte[] bytes) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is unavailable", exception);
		}
	}

	public record Loaded(MapSnapshot snapshot, byte[] content, String hash) { }
}
```

数据包 ResourceManager 路径为 `rogueengine/map_snapshot/<path>.nbt`。读取前后检查压缩文件大小，拒绝符号链接和重解析点。

- [ ] **Step 4: 运行测试确认通过**

Run: `.\gradlew.bat test --tests spectrum255.rogueengine.map.MapSnapshotCatalogTest`

Expected: 世界优先与哈希测试通过。

## Task 4：快照坐标、方块和实体变换

**Files:**
- Create: `src/main/java/spectrum255/rogueengine/map/SnapshotTransform.java`
- Create: `src/test/java/spectrum255/rogueengine/map/SnapshotTransformTest.java`

- [ ] **Step 1: 编写失败测试**

```java
@Test
void rotatesBoundsAndFacingAroundAnchor() {
	MapSnapshot snapshot = new MapSnapshot(1, new Vec3i(2, 1, 3),
			List.of(Blocks.STONE.defaultBlockState()), List.of(), List.of(), List.of());
	BoundingBox box = SnapshotTransform.bounds(snapshot, Rotation.CLOCKWISE_90, Mirror.NONE, new BlockPos(10, 64, 20));
	assertEquals(new BoundingBox(10, 64, 20, 12, 64, 21), box);

	BlockState state = Blocks.PISTON.defaultBlockState().setValue(DirectionalBlock.FACING, Direction.NORTH);
	assertEquals(Direction.EAST, SnapshotTransform.transformState(state, Rotation.CLOCKWISE_90, Mirror.NONE)
			.getValue(DirectionalBlock.FACING));
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `.\gradlew.bat test --tests spectrum255.rogueengine.map.SnapshotTransformTest`

Expected: 缺少变换类。

- [ ] **Step 3: 实现变换**

```java
public static BlockPos transformPos(BlockPos local, Rotation rotation, Mirror mirror, BlockPos anchor) {
	return StructureTemplate.transform(local, mirror, rotation, BlockPos.ZERO).offset(anchor);
}

public static BoundingBox bounds(MapSnapshot snapshot, Rotation rotation, Mirror mirror, BlockPos anchor) {
	BoundingBox local = snapshot.localBounds();
	BoundingBox result = null;
	for (BlockPos corner : List.of(new BlockPos(local.minX(), local.minY(), local.minZ()),
			new BlockPos(local.maxX(), local.maxY(), local.maxZ()),
			new BlockPos(local.minX(), local.maxY(), local.minZ()),
			new BlockPos(local.maxX(), local.minY(), local.maxZ()),
			new BlockPos(local.minX(), local.minY(), local.maxZ()),
			new BlockPos(local.maxX(), local.maxY(), local.minZ()),
			new BlockPos(local.minX(), local.maxY(), local.maxZ()),
			new BlockPos(local.maxX(), local.minY(), local.minZ()))) {
		BlockPos world = transformPos(corner, rotation, mirror, anchor);
		result = result == null ? new BoundingBox(world) : result.encapsulate(new BoundingBox(world));
	}
	return result;
}

public static BlockState transformState(BlockState state, Rotation rotation, Mirror mirror) {
	if (!state.hasProperty(DirectionalBlock.FACING)) return state;
	Direction direction = mirror.mirror(state.getValue(DirectionalBlock.FACING));
	return state.setValue(DirectionalBlock.FACING, rotation.rotate(direction));
}
```

实体根 `Position` 使用 `transformPos`；`Rotation.y` 按镜像和旋转角度转换；乘客 NBT 递归移除 `UUID`，加载时统一生成新 UUID。

- [ ] **Step 4: 运行测试确认通过**

Run: `.\gradlew.bat test --tests spectrum255.rogueengine.map.SnapshotTransformTest`

Expected: 包围盒和朝向测试通过。

## Task 5：区域捕获服务与命令

**Files:**
- Create: `src/main/java/spectrum255/rogueengine/map/SnapshotCaptureService.java`
- Modify: `src/main/java/spectrum255/rogueengine/command/RogueEngineCommands.java`
- Create: `src/test/java/spectrum255/rogueengine/map/SnapshotCaptureServiceTest.java`

- [ ] **Step 1: 编写失败测试**

```java
@Test
void rejectsOversizedRegion() {
	assertThrows(IllegalArgumentException.class, () -> SnapshotCaptureService.validateRegion(
			new BlockPos(0, 0, 0), new BlockPos(512, 0, 0)));
}

@Test
void acceptsClosedRegion() {
	assertDoesNotThrow(() -> SnapshotCaptureService.validateRegion(
			new BlockPos(0, 0, 0), new BlockPos(4, 1, 4)));
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `.\gradlew.bat test --tests spectrum255.rogueengine.map.SnapshotCaptureServiceTest`

Expected: 缺少捕获服务。

- [ ] **Step 3: 实现捕获服务**

`validateRegion` 将角点归一化为闭区间，校验每轴 `<=512`、体积 `<=1_000_000`。

`capture(ServerLevel level, BlockPos from, BlockPos to, Identifier id)`：

1. 遍历区域，按 `BlockState` 去重形成调色板，空气跳过。
2. 非空气方块写入稀疏 `SnapshotBlock`，局部坐标为 `worldPos - min`。
3. 对每个非空气方块调用 `getBlockEntityNbtForSaving`，将 `x/y/z` 改写为局部坐标。
4. 使用 `EntityTypeTest.forClass(Entity.class)` 获取区域实体，过滤 `ServerPlayer` 和 `getVehicle()!=null`。
5. `TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries)`，调用 `entity.save(output)`，局部化 `Position` 并递归删除 `UUID`。
6. 构造 `MapSnapshot`，压缩写入世界受管路径，返回文件、哈希和计数。

- [ ] **Step 4: 注册命令**

在 `RogueEngineCommands` 的 admin 分支增加：

```java
Commands.literal("map")
	.then(Commands.literal("snapshot")
		.then(Commands.literal("capture")
			.then(Commands.argument("id", IdentifierArgument.id())
				.then(Commands.argument("from", net.minecraft.commands.arguments.coordinates.BlockPosArgument.blockPos())
					.then(Commands.argument("to", net.minecraft.commands.arguments.coordinates.BlockPosArgument.blockPos())
						.executes(RogueEngineCommands::captureSnapshot))))))
```

`captureSnapshot` 使用 `Permissions.COMMANDS_ADMIN`，从 `CommandSourceStack.getPlayerOrException()` 获取维度，调用服务并输出路径、哈希和数量。

- [ ] **Step 5: 运行测试确认通过**

Run: `.\gradlew.bat test --tests spectrum255.rogueengine.map.SnapshotCaptureServiceTest`

Expected: 区域校验测试通过。

## Task 6：逐区块快照放置服务

**Files:**
- Create: `src/main/java/spectrum255/rogueengine/map/SnapshotScenePlacer.java`
- Create: `src/main/java/spectrum255/rogueengine/map/SnapshotPlacementService.java`
- Create: `src/test/java/spectrum255/rogueengine/map/SnapshotPlacementServiceTest.java`

- [ ] **Step 1: 编写失败测试**

```java
@Test
void budgetedQueueCompletesAndPropagatesFailure() {
	SnapshotPlacementService service = new SnapshotPlacementService(false);
	CompletableFuture<String> ok = service.submit(() -> "ok", budget(1));
	service.tickForTest();
	assertEquals("ok", ok.join());

	CompletableFuture<String> failed = service.submit(() -> {
		throw new IllegalStateException("boom");
	}, budget(1));
	service.tickForTest();
	CompletionException error = assertThrows(CompletionException.class, failed::join);
	assertInstanceOf(IllegalStateException.class, error.getCause());
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `.\gradlew.bat test --tests spectrum255.rogueengine.map.SnapshotPlacementServiceTest`

Expected: 缺少快照放置服务。

- [ ] **Step 3: 实现单区块放置**

`SnapshotScenePlacer.placeChunk`：

- 获取 `LevelChunk`。
- 计算变换后包围盒与当前区块的交集，逐格置空气并移除区内实体。
- 将快照方块按变换后区块分组，写入 `transformState` 后的状态。
- 对关联方块实体改写局部 `x/y/z` 为世界坐标，调用 `BlockEntity.loadStatic` 和 `setBlockEntity`。

`spawnEntities` 在所有区块完成后执行：局部化根实体 NBT，调用 `EntityType.loadEntityRecursive(tag, level, new EntitySpawnRequest(EntitySpawnReason.LOAD, true), entity -> { entity.setUUID(UUID.randomUUID()); return entity; })`，然后 `level.addFreshEntity`。

- [ ] **Step 4: 实现队列**

`SnapshotPlacementService` 维护 `Queue<Job>`。`Job` 保存 `ServerLevel`、缓存路径、期望哈希、`MapScene`、`MapSnapshot`、预算、future 和区块游标。

`tick` 在 `maxTickMs` 内最多处理 `maxChunksPerTick` 个区块；全部区块完成后在实体预算内生成实体并完成 future。异常通过 `completeExceptionally` 返回。提供服务：

```java
public CompletableFuture<StructureScenePlacer.PlacementResult> place(ServerLevel level, Path source,
		String expectedHash, MapScene scene, BlockPos origin, MapPool.PlacementBudget budget) throws IOException
```

- [ ] **Step 5: 运行测试确认通过**

Run: `.\gradlew.bat test --tests spectrum255.rogueengine.map.SnapshotPlacementServiceTest`

Expected: 队列预算和异常测试通过。

## Task 7：创建、刷新与来源元数据集成

**Files:**
- Modify: `src/main/java/spectrum255/rogueengine/map/SceneSelectionSnapshot.java`
- Modify: `src/main/java/spectrum255/rogueengine/map/SceneSelectionSnapshotCodec.java`
- Modify: `src/main/java/spectrum255/rogueengine/instance/BoxCreationService.java`
- Modify: `src/main/java/spectrum255/rogueengine/map/SceneResetService.java`
- Modify: `src/main/java/spectrum255/rogueengine/runtime/ServerRuntime.java`
- Modify: `src/test/java/spectrum255/rogueengine/map/SceneSelectionSnapshotTest.java`

- [ ] **Step 1: 增加失败测试**

```java
@Test
void snapshotSourceIsPersistedInSelection() {
	MapScene scene = new MapScene(2, new MapScene.Source(MapScene.SourceKind.SNAPSHOT,
			Identifier.parse("example:room")), new MapScene.SceneBounds(BlockPos.ZERO, BlockPos.ZERO),
			BlockPos.ZERO, BlockPos.ZERO, Rotation.NONE, Mirror.NONE, List.of(),
			new MapScene.RefreshPolicy(List.of(MapScene.RefreshTrigger.MANUAL), MapScene.PlayerResetPolicy.NONE));
	SceneSelectionSnapshot snapshot = SceneSelectionSnapshot.capture(UUID.randomUUID(),
			new MapSelection(Identifier.withDefaultNamespace("pool"), "a".repeat(64),
					Identifier.withDefaultNamespace("scene"), 1, 42L, 0L),
			scene, new SceneCacheService.CachedScene("scenes/" + UUID.randomUUID() + "/source.nbt", "b".repeat(64)));
	assertEquals(MapScene.SourceKind.SNAPSHOT, snapshot.sourceKind());
	assertEquals(Identifier.parse("example:room"), snapshot.sourceId());
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `.\gradlew.bat test --tests spectrum255.rogueengine.map.SceneSelectionSnapshotTest`

Expected: 快照尚未保存来源信息。

- [ ] **Step 3: 扩展 HASHES 对象**

`SceneSelectionSnapshot.Hashes` 增加 `SourceKind sourceKind`、`Identifier sourceId`，保留两参数兼容构造器，默认 `STRUCTURE` 和 `minecraft:legacy`。`capture` 使用 `scene.source()`。Codec 增加可选 `source_kind`、`source_id`，旧 JSON 缺省仍按结构处理。

- [ ] **Step 4: 修改创建服务**

`prepareMap` 在读取 `MapScene` 后按来源分发：

```java
if (scene.source().kind() == MapScene.SourceKind.SNAPSHOT) {
	MapSnapshotCatalog.Loaded loaded = runtime().snapshots().load(scene.snapshotId()).orElseThrow();
	cached = runtime().sceneCache().cache(instanceId, scene.snapshotId(), loaded.content());
} else {
	// 保留现有 StructureTemplateManager.get + save 流程
}
```

`startMapPlacement` 对快照调用 `runtime.snapshotPlacement().place(...)`，结构继续调用 `scenePlacement().place(...)`。

- [ ] **Step 5: 修改刷新服务**

`SceneResetService` 构造 `MapScene` 时使用持久化的 `sourceKind/sourceId`，不再把 `sceneId` 当结构 ID。根据来源调用快照或结构放置服务；清理、WAL、出生点和 `reset_count` 流程不变。

- [ ] **Step 6: Runtime 接入**

`ServerRuntime` 新增 `MapSnapshotCatalog` 和 `SnapshotPlacementService` 字段及访问器。目录的数据包读取函数使用：

```java
id -> {
	Identifier resource = Identifier.fromNamespaceAndPath(id.getNamespace(),
			"rogueengine/map_snapshot/" + id.getPath() + ".nbt");
	try (InputStream input = server.getResourceManager().open(resource)) {
		return Optional.of(input.readAllBytes());
	} catch (IOException exception) {
		return Optional.empty();
	}
}
```

- [ ] **Step 7: 运行集成测试**

Run: `.\gradlew.bat test --tests 'spectrum255.rogueengine.map.*' --tests 'spectrum255.rogueengine.instance.*'`

Expected: 来源持久化、创建和刷新相关测试通过。

## Task 8：内置快照资源与服务端冒烟

**Files:**
- Create: `src/main/resources/data/rogueengine/rogueengine/map_snapshot/example_room.nbt`
- Create: `src/main/resources/data/rogueengine/rogueengine/map_scene/example_room_snapshot.json`
- Create: `src/main/resources/data/rogueengine/rogueengine/map_pool/example_snapshot_pool.json`
- Create: `src/main/resources/data/rogueengine/rogueengine/worldgen_template/map_pool_snapshot.json`
- Create: `src/main/resources/data/rogueengine/rogueengine/profile/map_pool_snapshot.json`
- Create: `verification/generate-example-snapshot.py`
- Modify: `verification/server-smoke.ps1`
- Create: `src/test/java/spectrum255/rogueengine/map/BuiltInSnapshotResourceTest.java`

- [ ] **Step 1: 编写失败资源测试**

检查五类 JSON/NBT 路径存在，MapScene schema v2 引用 `example_room` 快照，MapPool 引用场景，Profile 引用模板，NBT 可解析且尺寸为 `[5,1,5]`。

- [ ] **Step 2: 运行测试确认失败**

Run: `.\gradlew.bat test --tests spectrum255.rogueengine.map.BuiltInSnapshotResourceTest`

Expected: 内置快照资源缺失。

- [ ] **Step 3: 生成内置资源**

`generate-example-snapshot.py` 输出 gzip NBT：5×1×5 石头地板、一个箱子方块实体、一个物品实体。JSON 资源参照现有 `map_pool` 示例，场景 `source.kind=snapshot`。

- [ ] **Step 4: 扩展服务端冒烟**

在结构地图冒烟后增加快照地图创建，轮询 `ACTIVE`，检查 catalog 的 `hashes.source_kind=snapshot`、`source_id`，执行 reset，检查 `reset_count=1`，归档并恢复。

- [ ] **Step 5: 运行资源测试和冒烟**

Run: `.\gradlew.bat clean build test`
Run: `.\verification\server-smoke.ps1`

Expected: 单元测试、结构地图和快照地图冒烟全部通过。

## Task 9：文档与最终验证

**Files:**
- Modify: `docs/map-pools.md`
- Modify: `README.md`

- [ ] **Step 1: 更新作者指南**

增加快照路径、schema v2 `source`、NBT 格式、捕获命令、世界/数据包优先级、实体 UUID 重建、处理器限制和尺寸/数量上限。

- [ ] **Step 2: 更新 README**

将已知限制改为“支持结构 `.nbt` 和场景快照；完整存档/ZIP 导入尚未实现”。

- [ ] **Step 3: 最终验证**

Run: `.\gradlew.bat clean build test`
Run: `.\verification\server-smoke.ps1`

检查文档无 `TODO`、`TBD` 或与实现不一致的字段名。

## 完成标准

- 数据包和世界受管目录都能加载快照。
- 区域捕获命令可即时生成可用快照。
- 方块、方块实体、非玩家实体和乘客关系可恢复。
- 快照放置按真实区块预算执行。
- 创建、刷新、归档、恢复和销毁隔离保持现有语义。
- MapScene v1、旧 catalog 和旧箱庭兼容。
- 单元测试与服务端冒烟全部通过。
