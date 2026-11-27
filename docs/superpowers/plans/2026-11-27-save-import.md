# Minecraft 存档区域导入实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 从 26.3 存档文件夹或 ZIP 只读提取指定维度区域，生成世界受管场景快照，并通过 RCON 命令验证。

**Architecture:** `SafeZipExtractor` 只提取选中维度的区域文件；`SavedWorldReader` 校验版本、路径和 Anvil 区块；`SaveImportService` 将区块、方块实体和实体转换为现有 `MapSnapshot` 并原子写入。命令层仅允许非玩家 admin 源。

**Tech Stack:** Java 25、Minecraft 26.3、Apache Commons Compress、Fabric、JUnit 5、Gradle。

**设计依据：** `docs/superpowers/specs/2026-11-27-save-import-design.md`

**工作区说明：** 无 Git 元数据，不安排 commit。

---

## 已核对 API

- `RegionFileStorage(RegionStorageInfo, Path, boolean)`、`read(ChunkPos)`。
- `RegionStorageInfo(String, ResourceKey<Level>, String)`。
- `PalettedContainerFactory.create(RegistryAccess)`、`blockStatesContainerCodec()`。
- `ChunkStatus.CODEC`、`ChunkStatus.FULL`。
- `BlockEntity.getPosFromTag(ChunkPos, CompoundTag)`。
- `ChunkPos.CODEC`、实体区域 `Entities`/`Position`。
- `SharedConstants.getCurrentVersion().dataVersion().version()`。
- Commons Compress `ZipArchiveInputStream`、`ZipArchiveEntry.getUnixMode()`。

## Task 1：安全 ZIP 选择性提取

**Files:**
- Create: `src/main/java/spectrum255/rogueengine/map/SafeZipExtractor.java`
- Create: `src/test/java/spectrum255/rogueengine/map/SafeZipExtractorTest.java`

- [ ] **Step 1: 编写失败测试**

```java
@Test
void rejectsPathTraversal() throws IOException {
	Path zip = temp.resolve("bad.zip");
	try (ZipArchiveOutputStream out = new ZipArchiveOutputStream(zip.toFile())) {
		out.putArchiveEntry(new ZipArchiveEntry("../escape.txt"));
		out.write("x".getBytes(StandardCharsets.UTF_8));
		out.closeArchiveEntry();
	}
	assertThrows(IOException.class, () -> SafeZipExtractor.extract(zip, temp.resolve("out"),
			new HashSet<>(Set.of("level.dat"))));
}

@Test
void extractsOnlyAllowedEntriesAndCleansTemp() throws IOException {
	Path zip = temp.resolve("good.zip");
	try (ZipArchiveOutputStream out = new ZipArchiveOutputStream(zip.toFile())) {
		entry(out, "level.dat", "data");
		entry(out, "region/r.0.0.mca", "region");
		entry(out, "playerdata/ignored.dat", "ignored");
	}
	Path target = temp.resolve("out");
	SafeZipExtractor.extract(zip, target, new HashSet<>(Set.of("level.dat", "region/r.0.0.mca")));
	assertEquals("data", Files.readString(target.resolve("level.dat")));
	assertEquals("region", Files.readString(target.resolve("region/r.0.0.mca")));
	assertFalse(Files.exists(target.resolve("playerdata/ignored.dat")));
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `.\gradlew.bat test --tests spectrum255.rogueengine.map.SafeZipExtractorTest`

Expected: 缺少 `SafeZipExtractor`。

- [ ] **Step 3: 实现提取器**

核心签名：

```java
public static void extract(Path zip, Path targetRoot, Set<String> allowedFiles) throws IOException
```

实现要求：

- 常量：`MAX_ENTRIES=20_000`、`MAX_DECLARED_BYTES=2GiB`、`MAX_ENTRY_BYTES=256MiB`、`MAX_RATIO=256`、`MAX_EXTRACTED_BYTES=512MiB`。
- 使用 `ZipArchiveInputStream` 逐条目读取。
- 名称规范化：拒绝空名称、绝对路径、`\`、NUL、盘符、`.`/`..` 段；Unix mode `(mode & 0170000) == 0120000` 拒绝符号链接。
- 所有条目计数并累计声明大小；未知大小或超限拒绝。
- 仅 `allowedFiles` 写入目标；写入前 `AtomicFiles.verifyManagedPath(targetRoot, target)`。
- 流式复制时同时校验单文件实际大小和总提取大小。
- 失败时删除已写入临时文件，不删除调用方既有目录。

- [ ] **Step 4: 运行测试确认通过**

Run: `.\gradlew.bat test --tests spectrum255.rogueengine.map.SafeZipExtractorTest`

Expected: 路径穿越和选择性提取测试通过。

## Task 2：存档路径、版本与区域文件读取

**Files:**
- Create: `src/main/java/spectrum255/rogueengine/map/SavedWorldPaths.java`
- Create: `src/main/java/spectrum255/rogueengine/map/SavedWorldReader.java`
- Create: `src/test/java/spectrum255/rogueengine/map/SavedWorldPathsTest.java`
- Create: `src/test/java/spectrum255/rogueengine/map/SavedWorldReaderTest.java`

- [ ] **Step 1: 编写失败测试**

```java
@Test
void resolvesVanillaAndCustomDimensionPaths() {
	Path world = Path.of("world");
	assertEquals(Path.of("world/region"), SavedWorldPaths.dimensionFolder(world,
			ResourceKey.create(Registries.DIMENSION, Identifier.withDefaultNamespace("overworld"))));
	assertEquals(Path.of("world/DIM-1/region"), SavedWorldPaths.dimensionFolder(world,
			ResourceKey.create(Registries.DIMENSION, Identifier.withDefaultNamespace("the_nether"))));
	assertEquals(Path.of("world/dimensions/example/dungeon/region"), SavedWorldPaths.dimensionFolder(world,
			ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath("example", "dungeon"))));
}

@Test
void rejectsLockedWorldAndWrongDataVersion(@TempDir Path world) throws IOException {
	CompoundTag root = new CompoundTag();
	CompoundTag data = new CompoundTag();
	data.putInt("DataVersion", SharedConstants.getCurrentVersion().dataVersion().version() - 1);
	root.put("Data", data);
	NbtIo.writeCompressed(root, world.resolve("level.dat"));
	assertThrows(IOException.class, () -> SavedWorldReader.validateWorld(world));
}
```

`SavedWorldPaths.dimensionFolder` 返回 `region` 目录；对应 `entities` 目录通过 `dimensionFolder.resolveSibling("entities")` 获取。

- [ ] **Step 2: 运行测试确认失败**

Run: `.\gradlew.bat test --tests spectrum255.rogueengine.map.SavedWorldPathsTest --tests spectrum255.rogueengine.map.SavedWorldReaderTest`

Expected: 缺少路径和读取类。

- [ ] **Step 3: 实现路径和版本校验**

```java
public static Path dimensionFolder(Path worldRoot, ResourceKey<Level> dimension)
```

规则与设计文档一致。`SavedWorldReader.validateWorld(Path)`：

- `level.dat` 必须普通文件且路径受管。
- `session.lock` 存在则拒绝。
- 读取 `Data.DataVersion`，必须等于当前版本。
- 检查选中维度 `region` 目录存在且至少一个 `.mca` 普通文件。

`SavedWorldReader` 提供：

```java
public CompoundTag readChunk(Path worldRoot, ResourceKey<Level> dimension, ChunkPos pos) throws IOException
public List<CompoundTag> readEntities(Path worldRoot, ResourceKey<Level> dimension, ChunkPos pos) throws IOException
public void close()
```

内部对 `region` 和 `entities` 各使用一个只读 `RegionFileStorage`。`readEntities` 校验 `Position`，返回 `Entities` 列表。

- [ ] **Step 4: 运行测试确认通过**

Run: `.\gradlew.bat test --tests spectrum255.rogueengine.map.SavedWorldPathsTest --tests spectrum255.rogueengine.map.SavedWorldReaderTest`

Expected: 路径和版本测试通过。

## Task 3：Anvil 区块解码

**Files:**
- Create: `src/main/java/spectrum255/rogueengine/map/SavedChunkDecoder.java`
- Create: `src/test/java/spectrum255/rogueengine/map/SavedChunkDecoderTest.java`

- [ ] **Step 1: 编写失败测试**

```java
@Test
void decodesSectionBlocksAndLocalizesBlockEntities() {
	RegistryAccess registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
	CompoundTag chunk = chunkWithStoneSection(registries, 0, 0);
	CompoundTag chest = new CompoundTag();
	chest.putString("id", "minecraft:chest");
	chest.putInt("x", 2);
	chest.putInt("y", 128);
	chest.putInt("z", 3);
	ListTag blockEntities = new ListTag();
	blockEntities.add(chest);
		chunk.put("block_entities", blockEntities);

	SavedChunkDecoder.Decoded decoded = SavedChunkDecoder.decode(chunk, new ChunkPos(0, 0),
			new BoundingBox(0, 128, 0, 4, 128, 4), registries, true);

	assertEquals(25, decoded.blocks().size());
	assertEquals(1, decoded.blockEntities().size());
	assertEquals(2, decoded.blockEntities().getFirst().getInt("x"));
	assertEquals(0, decoded.blockEntities().getFirst().getInt("y"));
}
```

`chunkWithStoneSection` 通过 `PalettedContainerFactory.create(registries).createForBlockStates()` 生成全石头 section，写入 `Y=8`、`block_states`、`Status=minecraft:full`、`xPos/zPos`。

- [ ] **Step 2: 运行测试确认失败**

Run: `.\gradlew.bat test --tests spectrum255.rogueengine.map.SavedChunkDecoderTest`

Expected: 缺少解码器。

- [ ] **Step 3: 实现解码器**

```java
public static Decoded decode(CompoundTag chunk, ChunkPos expectedPos, BoundingBox region,
		HolderLookup.Provider registries, boolean includeBlockEntities)
```

- 校验 `xPos/zPos` 和 `Status`。
- `PalettedContainerFactory factory = PalettedContainerFactory.create(registries)`。
- 对每个 `sections` Compound：读取 `Y`，解析 `block_states`，遍历 16³，只保留区域内非空气方块。
- 返回局部 `SnapshotBlock` 和全局共享调色板映射。
- 方块实体使用 `BlockEntity.getPosFromTag(expectedPos, tag)`，过滤区域并局部化。
- `Decoded` 包含 `List<SnapshotBlock>`、`List<CompoundTag> blockEntities`、`Map<BlockState,Integer> palette`。

- [ ] **Step 4: 运行测试确认通过**

Run: `.\gradlew.bat test --tests spectrum255.rogueengine.map.SavedChunkDecoderTest`

Expected: section 和方块实体测试通过。

## Task 4：实体去重、局部化与导入服务

**Files:**
- Create: `src/main/java/spectrum255/rogueengine/map/SaveImportService.java`
- Create: `src/test/java/spectrum255/rogueengine/map/SaveImportServiceTest.java`

- [ ] **Step 1: 编写失败测试**

```java
@Test
void importsFolderAndZipEquivalently(@TempDir Path temp) throws IOException {
	Path source = createFixture(temp.resolve("source"));
	RegistryAccess registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
	SaveImportService service = new SaveImportService(temp.resolve("world"), registries);
	ImportOptions options = new ImportOptions(true, true);
	ResourceKey<Level> overworld = ResourceKey.create(Registries.DIMENSION, Identifier.withDefaultNamespace("overworld"));

	SaveImportService.Imported folder = service.importSnapshot(Identifier.parse("example:folder"),
			source, overworld, new BlockPos(0, 128, 0), new BlockPos(4, 128, 4), options);
	Path zip = zipFixture(temp.resolve("source.zip"), source);
	SaveImportService.Imported zipped = service.importSnapshot(Identifier.parse("example:zip"),
			zip, overworld, new BlockPos(0, 128, 0), new BlockPos(4, 128, 4), options);

	assertEquals(folder.hash(), zipped.hash());
	assertEquals(25, folder.blocks());
	assertEquals(1, folder.blockEntities());
	assertEquals(1, folder.entities());
}
```

fixture 包含当前 `DataVersion` 的 `level.dat`、一个石头 section、一个箱子和一个物品实体。

- [ ] **Step 2: 运行测试确认失败**

Run: `.\gradlew.bat test --tests spectrum255.rogueengine.map.SaveImportServiceTest`

Expected: 缺少导入服务。

- [ ] **Step 3: 实现导入服务**

```java
public record ImportOptions(boolean includeEntities, boolean includeBlockEntities) { }
public Imported importSnapshot(Identifier id, Path source, ResourceKey<Level> dimension,
		BlockPos from, BlockPos to, ImportOptions options) throws IOException
public record Imported(Path file, String hash, int blocks, int blockEntities, int entities) { }
```

流程：

1. `SnapshotCaptureService.validateRegion`。
2. ZIP 源创建 `<world>/data/rogue-engine-fabric/imports/<uuid>/`，调用 `SafeZipExtractor`，`finally` 删除临时目录。
3. 文件夹源直接只读打开。
4. `SavedWorldReader.validateWorld`。
5. 遍历区域相交区块，最多 4096；`readChunk` 为 null 则跳过。
6. 合并 `SavedChunkDecoder.Decoded` 的调色板和方块。
7. 实体读取合并所有 chunk 与 legacy `entities`；收集乘客 UUID，去重顶层实体，拒绝玩家，局部化并移除 UUID。
8. 构造 `MapSnapshot`，压缩写入 `<world>/data/rogue-engine-fabric/map-snapshots/<ns>/<path>.nbt`。
9. 返回哈希和计数。

实体辅助方法：

```java
private static List<CompoundTag> normalizeEntities(List<CompoundTag> source, BoundingBox region)
private static void localizeEntity(CompoundTag entity, BoundingBox region)
private static void stripUuidAndRejectPlayer(CompoundTag entity, int depth)
private static void collectPassengerUuids(CompoundTag entity, Set<UUID> passengers)
```

- [ ] **Step 4: 运行测试确认通过**

Run: `.\gradlew.bat test --tests spectrum255.rogueengine.map.SaveImportServiceTest`

Expected: 文件夹/ZIP 等价测试通过。

## Task 5：导入命令

**Files:**
- Modify: `src/main/java/spectrum255/rogueengine/command/RogueEngineCommands.java`

- [ ] **Step 1: 增加命令注册**

在 `map/snapshot` 下增加：

```java
.then(Commands.literal("import")
	.requires(source -> source.getEntity() == null
			&& source.permissions().hasPermission(Permissions.COMMANDS_ADMIN))
	.then(Commands.argument("id", IdentifierArgument.id())
		.then(Commands.argument("source", StringArgumentType.string())
			.then(Commands.argument("dimension", IdentifierArgument.id())
				.then(Commands.argument("from", BlockPosArgument.blockPos())
					.then(Commands.argument("to", BlockPosArgument.blockPos())
						.then(Commands.argument("entities", BoolArgumentType.bool())
							.then(Commands.argument("block_entities", BoolArgumentType.bool())
								.executes(RogueEngineCommands::importSnapshot)))))))))
```

- [ ] **Step 2: 实现执行方法**

```java
private static int importSnapshot(CommandContext<CommandSourceStack> context) throws CommandSyntaxException
```

- 解析 ID、源路径、维度 `ResourceKey<Level>`、坐标和布尔项。
- 相对路径基于 `server.getServerDirectory()`。
- 使用 `runtime.mutate` 调用 `SaveImportService`。
- 输出文件、SHA-256 和计数；异常通过 `fail` 返回。

- [ ] **Step 3: 编译验证**

Run: `.\gradlew.bat compileJava`

Expected: 命令注册编译通过。

## Task 6：导入 fixture 与服务端冒烟

**Files:**
- Create: `verification/generate-save-import-fixture.py`
- Create: `verification/save-import-smoke.ps1`
- Create: `src/test/java/spectrum255/rogueengine/map/SaveImportFixtureTest.java`

- [ ] **Step 1: 编写失败 fixture 测试**

检查生成器输出目录包含 `level.dat`、`region/r.0.0.mca`、`entities/r.0.0.mca`，并通过 `SavedWorldReader` 读取一个完整区块和一个实体。

- [ ] **Step 2: 运行测试确认失败**

Run: `.\gradlew.bat test --tests spectrum255.rogueengine.map.SaveImportFixtureTest`

Expected: fixture 不存在。

- [ ] **Step 3: 实现 Python fixture**

`generate-save-import-fixture.py`：

- 接收输出目录参数。
- 从 `versions/26.3/server-26.3.jar` 的 `version.json` 读取 `world_version`，写入 gzip `level.dat` 的 `DataVersion`。
- 写 5×1×5 区域对应的 16×16 section，`Y=8`，全石头。
- 写一个箱子方块实体和一个物品实体。
- 实现最小 Anvil region writer：4096 字节 header、location table、zlib chunk payload、sector padding。
- 同时输出 ZIP。

- [ ] **Step 4: 实现导入冒烟脚本**

`save-import-smoke.ps1`：

1. 调用 Python 生成 fixture。
2. 启动专用服务端并等待 RCON。
3. 执行文件夹导入和 ZIP 导入。
4. 检查两个快照 SHA-256 相同。
5. 通过测试数据包 MapScene/Profile 引用 `example:imported` 创建箱庭。
6. 验证 `ACTIVE`、刷新、归档和恢复。
7. 停止服务端并输出日志路径。

- [ ] **Step 5: 运行 fixture 测试和冒烟**

Run: `.\gradlew.bat clean build test`
Run: `.\verification\save-import-smoke.ps1`

Expected: 单元测试和导入冒烟全部通过。

## Task 7：文档与最终验证

**Files:**
- Modify: `docs/map-pools.md`
- Modify: `README.md`

- [ ] **Step 1: 更新文档**

记录导入命令、非玩家限制、维度路径、DataVersion、内容选项、ZIP 安全、输出路径、限制和未实现项。

- [ ] **Step 2: 更新 README**

增加存档导入功能和命令，修改已知限制为“完整存档导入已支持显式区域；整维度扫描、DataFixer 升级和 ZIP 导出未实现”。

- [ ] **Step 3: 最终验证**

Run: `.\gradlew.bat clean build test`
Run: `.\verification\server-smoke.ps1`
Run: `.\verification\save-import-smoke.ps1`

检查文档无 `TODO`、`TBD` 或与实现不一致的字段名。

## 完成标准

- 文件夹和 ZIP 都能安全导入 26.3 存档区域。
- 方块、方块实体、非玩家实体和乘客关系正确转换。
- 同版本校验、路径安全、大小限制和原子写入生效。
- 导入产物立即可被现有快照 MapScene 使用。
- 单元测试、现有服务端冒烟和导入冒烟全部通过。
- 文档准确记录限制与未实现项。
