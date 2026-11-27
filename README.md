# Rogue Engine Fabric

[![Minecraft](https://img.shields.io/badge/Minecraft-26.3-3C8527)](https://www.minecraft.net/)
[![Fabric](https://img.shields.io/badge/Fabric-Loader%200.20.1-1976D2)](https://fabricmc.net/)
[![Java](https://img.shields.io/badge/Java-25-orange)](https://adoptium.net/)
[![License](https://img.shields.io/badge/License-CC0--1.0-lightgrey)](LICENSE)

**一个运行在 Minecraft 26.3 Fabric 服务端上的动态箱庭维度引擎。**

在同一份存档中创建、传送、刷新、归档和销毁独立箱庭。地形、边界、地图集、玩家背包与场景快照均由数据包驱动，核心玩法不需要玩家安装客户端模组。

---

## 功能一览

- **动态箱庭维度**：创建、传送、疏散、归档、恢复和永久销毁，全部保存在同一存档中。
- **数据包驱动**：Profile、世界生成模板、地图场景、地图集和快照均可由数据包定义。
- **世界生成工具**：噪声随机化、圆形/椭圆边界、虚空/基岩边界、中央浮岛、自定义分层与确定性矿石。
- **随机地图集**：创建时从固定场景中确定性抽取，支持手动刷新、离开后刷新和每玩家状态重置。
- **场景快照**：保存方块、方块实体、非玩家实体和乘客关系，支持区域捕获与逐区块放置。
- **存档导入**：从 Minecraft 26.3 存档文件夹或 ZIP 安全导入指定维度区域。
- **玩家物品隔离**：按玩家和箱庭独立保存背包、装备与末影箱。
- **可选客户端工具**：Mod Menu 配置界面、本地数据包编辑、ZIP 导入导出和地图俯视预览。
- **服务端安全机制**：原子写入、catalog/WAL、崩溃恢复、隔离区和销毁二次确认。

---

## 环境要求

| 项目 | 要求 |
|---|---|
| Minecraft | **26.3** |
| 服务端 | Fabric Loader **0.20.1+** |
| Fabric API | **0.167.0+26.3** |
| Java | **JDK 25+** |
| 客户端 | 原版客户端即可使用核心功能 |
| 可选客户端 | Mod Menu **21.0.1**，用于配置和数据包编辑 |

> 当前只保证 Minecraft 26.3。26.2、26.4 及其他版本需要重新移植，不能直接替换 JAR。

---

## 快速开始

### 1. 安装服务端

将构建产物放入服务端 `mods` 目录：

```text
mods/
├─ rogueengine-1.0.0.jar
└─ fabric-api-0.167.0+26.3.jar
```

使用 Java 25 启动 Fabric 服务端。

### 2. 创建第一个箱庭

进入游戏或使用 RCON 执行：

```mcfunction
/rogueengine profile list
/rogueengine instance create rogueengine:central_island 我的浮岛
/rogueengine instance list
```

记录命令返回的箱庭 UUID，然后传送：

```mcfunction
/rogueengine instance teleport <uuid>
```

离开并归档：

```mcfunction
/rogueengine instance evacuate <uuid>
/rogueengine instance archive <uuid>
```

之后可随时恢复：

```mcfunction
/rogueengine instance restore <uuid>
```

### 3. 可选：打开配置界面

客户端安装 Mod Menu 21.0.1 后，在模组列表中找到 **Rogue Engine Fabric**：

- 编辑模板与 Profile。
- 直接编辑 JSON。
- 保存本地数据包。
- 导入/导出 ZIP。
- 预览快照或结构地图。

本地数据包目录：

```text
config/rogueengine/datapacks/
```

---

## 工作原理

```text
数据包 / 本地工具
        │
        ▼
Profile + WorldGenTemplate + MapPool
        │
        ▼
创建时解析并保存自包含实例快照
        │
        ▼
动态 LevelStem + ChunkGenerator
        │
        ▼
同一存档中的独立箱庭维度
        │
        ▼
玩家传送 / 场景放置 / 背包隔离 / 刷新恢复
```

每个箱庭创建时都会固定自己的生成器、种子、地图选择和刷新状态。之后修改原模板，不会影响已经创建的箱庭。

---

## 核心概念

| 概念 | 作用 |
|---|---|
| **Profile** | 定义维度类型、生成器、种子、出生点、硬世界边界和背包策略 |
| **WorldGenTemplate** | 定义噪声随机化、地形边界、浮岛或地图集生成参数 |
| **MapScene** | 定义一个固定场景，可使用结构模板或场景快照 |
| **MapPool** | 定义创建箱庭时可抽取的场景集合 |
| **MapSnapshot** | 单文件 gzip NBT，保存区域方块、方块实体和非玩家实体 |
| **Instance** | 已创建的动态箱庭维度及其持久化状态 |
| **Catalog / WAL** | 服务端实例目录与崩溃恢复日志 |

---

## 内置内容

模组内置以下 Profile 与同名模板：

| ID | 用途 |
|---|---|
| `rogueengine:noise_chaos` | 确定性噪声参数随机化 |
| `rogueengine:full_chaos` | 更强烈的密度与气候扭曲 |
| `rogueengine:preset_chaos` | 从预设参数中随机选择 |
| `rogueengine:bounded_noise` | 圆形/椭圆地形边界 |
| `rogueengine:central_island` | 单岛中央浮岛 |
| `rogueengine:central_island_layers` | 自定义表层、土层、岩石和矿石 |
| `rogueengine:map_pool` | 结构地图随机地图集 |
| `rogueengine:map_pool_snapshot` | 快照地图随机地图集 |

---

## 命令

命令根节点为 `/rogueengine`。

### 查询

```mcfunction
/rogueengine profile list
/rogueengine instance list
/rogueengine instance info <uuid>
```

### 箱庭生命周期

```mcfunction
/rogueengine instance create <profile> [name]
/rogueengine instance teleport <uuid>
/rogueengine instance evacuate <uuid>
/rogueengine instance archive <uuid>
/rogueengine instance restore <uuid>
/rogueengine instance reset <uuid>
```

### 地图集

```mcfunction
/rogueengine instance create <profile> create-map <scene1,scene2>
```

### 场景快照

```mcfunction
/rogueengine map snapshot capture <snapshot_id> <from> <to>
/rogueengine map snapshot import <snapshot_id> <source> <dimension> <from> <to> <include_entities> <include_block_entities>
```

### 管理

```mcfunction
/rogueengine admin datapack import <pack_name> <zip_path>
/rogueengine admin destroy prepare <uuid>
/rogueengine admin destroy confirm <uuid> <token>
/rogueengine admin destroy cancel <uuid>
/rogueengine admin recover
/rogueengine admin stats
```

### 权限说明

- 查询：moderator。
- 创建、传送、归档、恢复：admin。
- 永久销毁：owner，且必须由同一在线玩家完成二次确认。
- 存档导入：admin，仅允许控制台、RCON 等非玩家命令源。
- 本地数据包 ZIP 导入：owner，仅允许非玩家命令源。

---

## 数据包

### 目录结构

```text
data/<pack_namespace>/rogueengine/
├─ profile/<profile_id>.json
├─ worldgen_template/<template_id>.json
├─ map_scene/<scene_id>.json
├─ map_pool/<pool_id>.json
└─ map_snapshot/<snapshot_id>.nbt
```

原版结构模板使用：

```text
data/<pack_namespace>/structure/<structure_id>.nbt
```

### Profile 示例

```json
{
  "schema_version": 2,
  "dimension_type": "minecraft:overworld",
  "generator": {
    "kind": "template",
    "id": "rogueengine:central_island"
  },
  "seed": {
    "mode": "fixed",
    "value": 123456789
  },
  "spawn": {
    "mode": "fixed",
    "x": 0.5,
    "y": 129.0,
    "z": 0.5,
    "yaw": 0.0
  },
  "border": {
    "center_x": 0.0,
    "center_z": 0.0,
    "initial_size": 512.0,
    "warning_blocks": 16.0
  },
  "inventory": {
    "bring_on_enter": true,
    "take_on_exit": true
  }
}
```

### 生成器类型

| `generator.kind` | 说明 |
|---|---|
| `stem` | 引用原版或数据包中的 `LevelStem` |
| `inline` | 直接使用 Minecraft 26.3 `ChunkGenerator.CODEC`，可表达超平坦、固定生物群系和噪声生成器 |
| `template` | 使用本模组模板，支持随机地形、边界、浮岛和地图集 |

`template` 地形生成器当前要求 `NoiseBasedChunkGenerator`。如果需要超平坦或固定生物群系，请使用 `inline` 或自定义 `stem`。

### 自定义维度参数

Profile 通过 `dimension_type` 引用维度类型。数据包可以自定义：

- 高度、最低 Y、坐标缩放。
- 天空盒、雾、天空光和环境光。
- 太阳/月亮角度、月相、星星亮度、云层高度。
- 固定时间、时间线和默认时钟。
- 怪物生成规则等。

降雨和雷暴目前由原版天气系统管理，Profile 中没有直接配置项。

完整说明见：

- [世界生成模板数据包指南](docs/worldgen-templates.md)
- [随机地图集与固定场景](docs/map-pools.md)
- [客户端配置与本地数据包工具](docs/client-tools.md)

---

## 客户端工具

安装 Mod Menu 后，可打开 **Rogue Engine Fabric** 配置界面：

- 六个内置地形模板快捷加载。
- 模板与 Profile 高层表单。
- 完整 JSON 编辑器。
- 本地原子保存。
- 数据包 ZIP 安全导入/导出。
- 场景快照和结构 NBT 的俯视预览。
- 旋转与镜像切换。

专用服务器也可通过 RCON 导入本地数据包：

```mcfunction
/rogueengine admin datapack import my_pack /path/to/my_pack.zip
```

---

## 开发

### 构建与测试

Windows：

```powershell
$env:JAVA_HOME='<JDK 25 home>'
.\gradlew.bat build test
```

Linux / macOS：

```bash
export JAVA_HOME=/path/to/jdk-25
./gradlew build test
```

### 开发服务器

```powershell
.\gradlew.bat runServer
```

### 验证脚本

```powershell
.\verification\server-smoke.ps1
.\verification\save-import-smoke.ps1
.\verification\client-acceptance.ps1
```

- `server-smoke.ps1`：Profile、世界生成、地图集、快照、归档恢复和本地数据包。
- `save-import-smoke.ps1`：存档文件夹/ZIP 导入、哈希和完整生命周期。运行前需要本地 Minecraft 26.3 `server.jar`，或通过 `ROGUE_SERVER_JAR` 环境变量指定服务端 JAR。
- `client-acceptance.ps1`：自动启动客户端，验证快照实体恢复与 FULL 玩家状态重置，不截屏。

### 构建产物

```text
build/libs/
├─ rogueengine-1.0.0.jar
└─ rogueengine-1.0.0-sources.jar
```

- `rogueengine-1.0.0.jar`：运行所需发布模组。
- `-sources.jar`：可选源码包，仅用于开发或发布附属工件，不是运行依赖。

---

## 项目结构

```text
src/main/java/       服务端与通用逻辑
src/client/java/     可选 Mod Menu 客户端工具
src/main/resources/  fabric.mod.json、Mixin 配置和内置数据包
src/test/java/       单元测试
verification/        服务端、存档导入和客户端验收脚本
docs/                使用指南、设计和实施文档
```

---

## 兼容性与限制

- 仅保证 Minecraft **26.3**；升级前必须重新验证维度协议、Mixin、世界生成和客户端 API。
- 核心玩法不需要客户端安装本模组；Mod Menu 仅用于配置和数据包编辑。
- 移除模组前必须先疏散并归档所有箱庭。
- `/execute in` 不保证识别运行时维度键，请使用本模组传送命令。
- 存档导入只接受当前 26.3 `DataVersion`，不执行 DataFixer。
- 暂不支持整维度扫描和地图快照 ZIP 导出。
- 任务进度、成就和统计仍属于整个存档共享。
- 当前命令在服务端线程同步串行执行，不维护异步操作队列。

---

## 文档

| 文档 | 内容 |
|---|---|
| [世界生成模板](docs/worldgen-templates.md) | 模板格式、边界、浮岛和参数约束 |
| [随机地图集](docs/map-pools.md) | 场景、地图池、刷新、快照和玩家状态 |
| [客户端工具](docs/client-tools.md) | Mod Menu、本地数据包、ZIP 和地图预览 |

---

## 许可证

本项目使用 [CC0-1.0](LICENSE) 许可证。
