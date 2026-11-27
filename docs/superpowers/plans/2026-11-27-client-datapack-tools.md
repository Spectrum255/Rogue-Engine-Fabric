# 客户端配置与本地数据包工具 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 增加可选 Mod Menu 配置界面、高层模板/Profile 表单、高级 JSON 编辑、本地数据包原子生成、安全 ZIP 导入与导出，并让本地数据包立即可被模组命令读取。

**Architecture:** common 层的 `DatapackAuthoringService` 负责受管目录、JSON 校验、原子写入和 ZIP 安全；`ProfileCatalog`/`WorldGenTemplateCatalog` 在动态注册表未命中时回退读取本地数据包。client 层只提供 Mod Menu Screen 和表单，不直接访问专用服务器文件，也不新增网络封包。

**Tech Stack:** Java 25、Minecraft 26.3、Fabric Loader 0.20.1、Fabric API、Mod Menu 21.0.1（可选）、JUnit 5、Gradle 9.8.2。

**设计依据：** 已批准的世界生成设计第 3 节。

**工作区说明：** 无 Git 元数据，不安排 commit。

---

## 已核对的 API 与约束

- Mod Menu 通过 `fabric.mod.json` 的 `entrypoints.modmenu` 加载，未安装时不会加载该入口。
- Mod Menu 21.0.1 对应 Minecraft 26.3，使用 `modCompileOnly`/`modLocalRuntime`，不进入发布 JAR。
- 26.3 Screen 使用 `extractRenderState(GuiGraphicsExtractor, int, int, float)`。
- `EditBox(Font, int, int, int, int, Component)` 与 `Button.builder(...).bounds(...).build()` 可用。
- Fabric Loader 配置目录在客户端/服务端工作目录下为 `config/`。
- 本地数据包根目录固定为 `config/rogueengine/datapacks/`。
- ZIP 导入复用 `SafeZipExtractor`，只允许 `pack.mcmeta` 与 `data/<namespace>/rogueengine/{worldgen_template,profile}/<path>.json`。

## Task 1：客户端入口与 Mod Menu API 锁定

**Files:**
- Modify: `gradle.properties`
- Modify: `build.gradle`
- Modify: `src/main/resources/fabric.mod.json`

- [ ] **Step 1: 添加可选 Mod Menu 依赖**

`gradle.properties`：

```properties
modmenu_version=21.0.1
```

`build.gradle`：

```groovy
repositories {
    maven {
        name = 'Terraformers'
        url = uri('https://maven.terraformersmc.com/releases')
    }
}

dependencies {
    clientCompileOnly "com.terraformersmc:modmenu:${project.modmenu_version}"
    clientRuntimeOnly "com.terraformersmc:modmenu:${project.modmenu_version}"
}
```

`loom` 中先调用 `splitEnvironmentSourceSets()`，再声明 `sourceSets.main` 与 `sourceSets.client`。

- [ ] **Step 2: 扩展元数据**

`fabric.mod.json` 的 `environment` 改为 `"*"`，入口增加：

```json
"modmenu": [
  "spectrum255.rogueengine.client.RogueEngineModMenu"
]
```

`depends` 不增加 Mod Menu。

- [ ] **Step 3: 解析 API 并记录签名**

Run: `.\gradlew.bat dependencies --configuration clientCompileClasspath`

Expected: 解析 `com.terraformersmc:modmenu:21.0.1`。

已核对签名：`ModMenuApi.getModConfigScreenFactory()` 返回 `ConfigScreenFactory<?>`；`ConfigScreenFactory<S extends Screen>.create(Screen)` 返回 `S`。

## Task 2：本地数据包编写、导入与导出服务

**Files:**
- Create: `src/main/java/spectrum255/rogueengine/tool/DatapackAuthoringService.java`
- Create: `src/test/java/spectrum255/rogueengine/tool/DatapackAuthoringServiceTest.java`

- [ ] **Step 1: 写保存与往返失败测试**

```java
@Test
void savesValidatedTemplateAndProfileAtomically() throws IOException {
    DatapackAuthoringService service = new DatapackAuthoringService(temp.resolve("datapacks"));
    ConfigDraft draft = ConfigDraft.defaultDraft();
    Path pack = service.save(draft);

    assertTrue(Files.isRegularFile(pack.resolve("pack.mcmeta")));
    assertTrue(Files.isRegularFile(pack.resolve(
            "data/example/rogueengine/worldgen_template/my_world.json")));
    assertTrue(Files.isRegularFile(pack.resolve(
            "data/example/rogueengine/profile/my_world.json")));
    assertEquals(draft.template(), service.templates().get(draft.templateId()).orElseThrow());
    assertEquals(draft.profile(), service.profiles().get(draft.profileId()).orElseThrow());
}
```

- [ ] **Step 2: 写 ZIP 导入导出与非法内容测试**

覆盖：

1. 导出 ZIP 后重新导入到新 pack 名，模板与 Profile 等价。
2. ZIP 中的 `../../escape.txt` 被拒绝。
3. 非法模板 JSON 被拒绝，目标 pack 不保留半成品。
4. 已存在的 pack 不会被导入覆盖。
5. pack 名、命名空间和 ID 路径穿越被拒绝。

- [ ] **Step 3: 运行测试确认服务缺失**

Run: `.\gradlew.bat test --tests spectrum255.rogueengine.tool.DatapackAuthoringServiceTest`

Expected: 编译失败。

- [ ] **Step 4: 实现 `ConfigDraft`**

创建 `src/main/java/spectrum255/rogueengine/tool/ConfigDraft.java`：

```java
public record ConfigDraft(
        String packName, String namespace, Identifier templateId, Identifier profileId,
        WorldGenTemplate template, BoxProfile profile) {
    public DataResult<ConfigDraft> validate() {
        if (!packName.matches("[a-z0-9_-]+") || namespace.isBlank()) {
            return DataResult.error(() -> "Invalid pack name or namespace");
        }
        if (!namespace.equals(templateId.getNamespace()) || !namespace.equals(profileId.getNamespace())
                || !(profile.generator() instanceof BoxProfile.TemplateGenerator generator)
                || !generator.id().equals(templateId)) {
            return DataResult.error(() -> "Profile must reference the drafted template in the same namespace");
        }
        return template.validate().flatMap(ignored -> profile.validate().map(ignored2 -> this));
    }

    public static ConfigDraft defaultDraft() {
        Identifier id = Identifier.fromNamespaceAndPath("example", "my_world");
        WorldGenTemplate template = new WorldGenTemplate(1, "noise_chaos",
                Identifier.withDefaultNamespace("overworld"), WorldGenTemplate.neutralRanges(), List.of());
        BoxProfile profile = new BoxProfile(2, Identifier.withDefaultNamespace("overworld"),
                new BoxProfile.TemplateGenerator("template", id, Map.of(), Optional.empty()),
                new BoxProfile.Seed("random", Optional.empty()), new BoxProfile.SearchSpawn("search", 32),
                new BoxProfile.Border(0, 0, 30_000_000, 16), BoxProfile.InventoryPolicy.DEFAULT);
        return new ConfigDraft("my_pack", "example", id, id, template, profile);
    }
}
```

`validate()` 要求：

- pack 名匹配 `[a-z0-9_-]+`。
- namespace 与两个 ID 的 namespace 一致。
- template/profile ID 非空且路径合法。
- Profile 的 `generator` 为引用该模板的 `TemplateGenerator`。
- 两个 Codec 校验均通过。

- [ ] **Step 5: 实现 `DatapackAuthoringService`

核心 API：

```java
public static Path configRoot()
public Path save(ConfigDraft draft) throws IOException
public Path exportZip(String packName, Path target) throws IOException
public void importZip(Path zip, String packName) throws IOException
public Map<Identifier, WorldGenTemplate> templates()
public Map<Identifier, BoxProfile> profiles()
```

`save`：

- 调用 `draft.validate()`。
- 目标为 `configRoot/<packName>/`。
- 写 `pack.mcmeta`，`pack_format=121`。
- 使用 `JsonOps` 编码模板与 Profile，路径分别为 `data/<ns>/rogueengine/worldgen_template/<path>.json` 和 `profile/<path>.json`。
- 每个文件使用 `AtomicFiles.writeAtomic`，失败不留下半成品 JSON。

`exportZip`：

- 只遍历目标 pack 普通文件，拒绝符号链接和路径逃逸。
- 使用 `ZipArchiveOutputStream`，条目按相对路径排序。
- 不把绝对路径写入 ZIP。

`importZip`：

- 在 `configRoot/.import-<uuid>/` 解压。
- 用中央目录计算 allowlist，只提取 `pack.mcmeta` 和两类 JSON。
- 校验 `pack.mcmeta`、所有模板/Profile Codec 和 namespace/path。
- 目标 pack 已存在时拒绝；成功后 `Files.move` 暂存目录到最终目录。
- `finally` 递归清理暂存目录。

`templates/profiles` 不缓存，按 pack 名排序扫描并解析，重复 ID 使用先出现者。

- [ ] **Step 6: 运行服务测试**

Run: `.\gradlew.bat test --tests spectrum255.rogueengine.tool.DatapackAuthoringServiceTest`

Expected: 保存、往返、ZIP 安全和失败清理全部通过。

## Task 3：本地数据包 catalog 回退

**Files:**
- Modify: `src/main/java/spectrum255/rogueengine/profile/WorldGenTemplateCatalog.java`
- Modify: `src/main/java/spectrum255/rogueengine/profile/ProfileCatalog.java`
- Create: `src/test/java/spectrum255/rogueengine/profile/LocalDatapackCatalogTest.java`

- [ ] **Step 1: 写回退失败测试**

```java
@Test
void localPackIdsAreVisibleWhenRegistryMisses() throws IOException {
    DatapackAuthoringService service = new DatapackAuthoringService(temp.resolve("datapacks"));
    ConfigDraft draft = ConfigDraft.defaultDraft();
    service.save(draft);
    MappedRegistry<WorldGenTemplate> registry = new MappedRegistry<>(
            WorldGenTemplateCatalog.REGISTRY_KEY, Lifecycle.stable()).freeze();
    WorldGenTemplateCatalog catalog = new WorldGenTemplateCatalog(registry, service);

    assertTrue(catalog.ids().contains(draft.templateId()));
    assertEquals(draft.template(), catalog.get(draft.templateId()).orElseThrow());
}
```

- [ ] **Step 2: 运行测试确认回退缺失**

Run: `.\gradlew.bat test --tests spectrum255.rogueengine.profile.LocalDatapackCatalogTest`

Expected: 本地 ID 不可见或类构造器缺失。

- [ ] **Step 3: 实现回退**

两个 catalog 保留现有单参数构造器以兼容测试，新增包级构造器接收 `DatapackAuthoringService`。`from(HolderLookup.Provider)` 使用默认 `configRoot()` 创建服务。

`get(id)` 先查动态注册表，未命中再查本地服务。`ids()` 返回注册表 ID 与本地 ID 的有序并集。注册表条目优先，本地条目不覆盖已加载数据包。

- [ ] **Step 4: 运行 catalog 测试**

Run: `.\gradlew.bat test --tests 'spectrum255.rogueengine.profile.*' --tests 'spectrum255.rogueengine.tool.*'`

Expected: 全部通过。

## Task 4：Mod Menu 主界面与模板库

**Files:**
- Create: `src/client/java/spectrum255/rogueengine/client/RogueEngineModMenu.java`
- Create: `src/client/java/spectrum255/rogueengine/client/RogueEngineConfigScreen.java`
- Create: `src/client/java/spectrum255/rogueengine/client/screen/TemplateFormScreen.java`
- Create: `src/client/java/spectrum255/rogueengine/client/screen/ProfileFormScreen.java`
- Create: `src/client/java/spectrum255/rogueengine/client/screen/JsonEditorScreen.java`
- Create: `src/client/java/spectrum255/rogueengine/client/screen/TransferScreen.java`

- [ ] **Step 1: 实现 Mod Menu 入口**

`RogueEngineModMenu implements ModMenuApi`，按 Task 1 的 `javap` 签名返回 `RogueEngineConfigScreen` 工厂。

- [ ] **Step 2: 实现主界面**

主界面显示：

- 当前 pack 名、namespace、模板 ID、Profile ID。
- 本地模板 ID 列表（从 `DatapackAuthoringService.templates()` 读取）。
- 内置模板快捷加载：`noise_chaos`、`full_chaos`、`preset_chaos`、`bounded_noise`、`central_island`、`central_island_layers`。
- 按钮：编辑模板、编辑维度参数、高级 JSON、导入/导出、保存。

主界面持有共享 `ConfigDraft`，子屏幕修改后返回同一草稿。

- [ ] **Step 3: 实现模板表单**

字段：

- pack 名、namespace、模板 ID、Profile ID。
- 生成器类型按钮循环选择。
- 边界开关、中心 X/Z、半径 X/Z、形状、边界外模式。
- 浮岛开关、中心 Y、厚度、收尖、起伏、边缘噪声、地表模式。

“应用”时解析数值并构造 `TerrainBoundary`/`CentralIslandSettings`，调用 `draft.validate()`，错误显示在状态栏，不修改服务文件。

- [ ] **Step 4: 实现 Profile 表单**

字段：

- seed 模式与固定 seed。
- spawn 模式、半径或 X/Y/Z/yaw。
- 硬边界中心 X/Z、初始大小、警告距离。

默认硬边界建议 `30_000_000`，保存前通过 `BoxProfile.validate()`。

- [ ] **Step 5: 实现高级 JSON 编辑器**

两个多行 `EditBox` 分别显示模板与 Profile JSON。应用时使用 `WorldGenTemplateCodec.CODEC`/`BoxProfileCodec.CODEC` 解析，重建草稿并显示错误。

- [ ] **Step 6: 实现导入/导出界面**

字段：

- pack 名。
- ZIP 路径（相对路径基于游戏目录）。
- 导入、导出、刷新本地列表按钮。

导入/导出调用 `DatapackAuthoringService`，异常显示在状态栏。专用服务器提示“导出 ZIP 后上传到服务端 config 目录”，不尝试直接写服务器文件。

- [ ] **Step 7: 编译客户端**

Run: `.\gradlew.bat compileClientJava`

Expected: Mod Menu 入口与所有 Screen 编译通过。

## Task 5：本地化、文档与验证

**Files:**
- Create: `src/client/resources/assets/rogueengine/lang/en_us.json`
- Create: `src/client/resources/assets/rogueengine/lang/zh_cn.json`
- Create: `src/client/resources/assets/rogueengine/lang/zh_tw.json`
- Create: `docs/client-tools.md`
- Modify: `README.md`

- [ ] **Step 1: 添加三种语言**

所有按钮、标签和状态消息使用 `Component.translatable`，键名统一为 `rogueengine.config.*`。三种语言占位符与顺序一致。

- [ ] **Step 2: 更新文档**

记录 Mod Menu 可选安装、本地数据包目录、高层表单、JSON 模式、ZIP 导入导出、专用服务器上传流程、安全限制和“已有实例不受影响”。

- [ ] **Step 3: 全量验证**

Run: `.\gradlew.bat clean build test`

Run: `.\verification\server-smoke.ps1`

Expected: common 测试、客户端编译、服务端冒烟全部通过，专用服务器不依赖 Mod Menu。

## 完成标准

- 未安装 Mod Menu 时服务端和客户端原样运行。
- 安装 Mod Menu 后可从模组列表打开配置界面。
- 高层表单与高级 JSON 均可生成合法模板/Profile。
- 本地数据包原子写入，模组命令无需重启即可看到本地 ID。
- ZIP 导入只接受白名单内容，路径穿越、非法 JSON 和半成品写入均被拒绝。
- 导出 ZIP 可作为独立数据包上传到专用服务器。
- 已有实例、动态注册表数据包和旧存档保持兼容。
- 单元测试、客户端编译和现有服务端冒烟全部通过。
