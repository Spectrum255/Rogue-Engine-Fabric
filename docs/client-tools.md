# 客户端配置与本地数据包工具

Rogue Engine 提供可选的 Mod Menu 配置界面。专用服务器无需安装 Mod Menu，也不需要安装客户端模组；只有需要在本机编辑数据包的玩家安装 Mod Menu 21.0.1。

## 打开界面

1. 安装 Minecraft 26.3 对应的 Mod Menu 21.0.1。
2. 打开 Mods 列表，选择 Rogue Engine Fabric。
3. 点击配置/Configure 按钮。

界面提供五个区域：

- **模板库**：加载六个内置模板，或从本地数据包选择模板。
- **模板表单**：编辑生成器类型、地形边界和中央浮岛参数。
- **维度表单**：编辑 seed、出生点和独立硬世界边界。
- **高级 JSON**：直接编辑完整模板与 Profile JSON。
- **导入/导出**：安全导入或导出 ZIP 数据包。
- **地图预览**：查看快照或结构的俯视高度图，并切换旋转和镜像。

## 本地数据包

界面保存到：

```text
config/rogue-engine-fabric/datapacks/<pack_name>/
```

每个数据包包含：

```text
pack.mcmeta
data/<namespace>/rogue-engine-fabric/worldgen_template/<template_id>.json
data/<namespace>/rogue-engine-fabric/profile/<profile_id>.json
```

模组的 `ProfileCatalog` 和 `WorldGenTemplateCatalog` 会在动态注册表未命中时读取本地目录。因此保存后无需重启即可使用：

```text
/rogue-engine profile list
/rogue-engine instance create <namespace>:<profile_id> [name]
```

动态注册表中的同 ID 条目优先，本地条目不会覆盖已加载的数据包。已有箱庭使用创建时保存的快照，不受之后编辑影响。

## 专用服务器

客户端不会直接写专用服务器文件。流程为：

1. 在客户端界面导出 ZIP。
2. 将 ZIP 上传到专用服务器工作目录。
3. 由控制台、RCON 或其他非玩家 owner 源执行：

```text
/rogue-engine admin datapack import <pack_name> <zip_path>
```

4. 使用 `/rogue-engine profile list` 检查本地 Profile，然后创建箱庭。

ZIP 只接受 `pack.mcmeta` 和以下两类 JSON：

```text
data/<namespace>/rogue-engine-fabric/worldgen_template/<path>.json
data/<namespace>/rogue-engine-fabric/profile/<path>.json
```

导入会拒绝路径穿越、盘符、反斜杠、NUL、`.`/`..`、符号链接、非法 JSON、非法 Codec 和已存在的 pack 名。暂存目录在成功或失败后都会清理。

## 编辑规则

- pack 名只能包含小写字母、数字、下划线和连字符。
- namespace 必须与模板/Profile ID 的命名空间一致。
- Profile 必须引用当前编辑的模板。
- 模板边界必须位于硬世界边界内；固定出生点必须位于模板水平边界内。
- `central_island` 必须使用 `void` 边界，并提供 `island` 配置。
- 自定义分层和矿石可通过高级 JSON 编辑；界面会保留从内置示例加载的分层配置。
- 所有文件通过原子写入保存；ZIP 导出不包含绝对路径。

## 地图预览

从配置主界面点击“地图预览”，输入快照或结构 ID：

```text
snapshot: rogue-engine-fabric:example_room
structure: rogue-engine-fabric:example_room
```

预览来源、旋转和镜像均可切换。预览规则：

- 快照优先读取单机世界受管目录 `data/rogue-engine-fabric/map-snapshots/`，然后读取数据包 `data/<namespace>/rogue-engine-fabric/map_snapshot/`。
- 结构读取数据包 `data/<namespace>/structure/`。
- 预览使用每个 X/Z 列最高非空气方块的 MapColor，不启动额外世界。
- 预览不显示实体、方块实体内容或结构处理器结果。
- 专用服务器客户端无法读取服务器世界文件，只能预览客户端已加载的数据包资源。

## 自动客户端验收

```powershell
$env:JAVA_HOME='<JDK 25 home>'
.\verification\client-acceptance.ps1
```

脚本会创建一次性专用服务端和客户端，自动验证：

- 玩家区域捕获生成世界受管快照。
- 快照箱庭恢复方块、方块实体和非玩家实体。
- `player_reset=full` 在手动刷新后恢复生命、饥饿、空气、经验和状态效果。

验收结果写入 `build/client-acceptance/client/acceptance-result.txt`，调试状态写入 `acceptance-debug.log`。脚本不截屏。

## 当前限制

- 本地目录只读取 `worldgen_template` 与 `profile`，不会把任意客户端文件注册为数据包。
- 专用服务器导入命令只允许非玩家 owner 源，避免玩家指定任意服务端路径。
- 地图预览是俯视 MapColor 高度图，不等同于游戏内 3D 渲染。
- DataFixer、整维度扫描和 ZIP 导出地图快照不属于本工具范围。
