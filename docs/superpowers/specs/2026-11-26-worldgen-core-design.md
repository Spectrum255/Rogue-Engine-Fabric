# 世界生成核心设计

## 目标

在现有服务端动态箱庭引擎中增加数据包驱动的世界生成模板、确定性随机参数和三类可序列化生成策略，同时保持 schema 1 profile、已有箱庭快照和专用服务端运行方式兼容。

## 非目标

本阶段不实现以下内容：

- 客户端 Mod Menu 配置界面。
- 模板地形边界与原版硬世界边界的双层策略。
- 中央浮岛生成器。
- 专用服务器文件直写或 ZIP 导入导出界面。

这些内容作为后续独立阶段交付。

## 架构

### Profile 层

- `BoxProfile` 的 `Generator` 密封接口新增 `TemplateGenerator`。
- `BoxProfileCodec` 接受 schema 1 和 2；schema 1 不允许模板生成器。
- `WorldGenTemplate` 保存模板类型、基础 stem、参数范围与可选预设。
- `WorldGenTemplateCodec` 负责结构解析、范围校验、预设校验和尺度上限校验。
- `WorldGenTemplateCatalog` 通过 Fabric `DynamicRegistries` 注册 `rogueengine:worldgen_template`。

### Worldgen 层

- `WorldGenParameterResolver` 使用实例 seed、模板哈希和模板 ID 创建确定性随机序列。
- `NoiseChaosGenerator` 从基础 `NoiseGeneratorSettings` 构建白名单噪声变体。
- `FullChaosGenerator` 在噪声变体上混合密度路由并调整气候输入。
- `PresetChaosGenerator` 使用解析器选中的预设参数构建生成器。
- 三类生成器都复用原版 `NoiseBasedChunkGenerator`，避免新增必须同步到客户端的生成器类型。

### 解析与快照层

- `ResolvedBoxProfile.resolve` 从静态 registry 读取 `DimensionType` 和 `LevelStem`，从 reloadable registry 读取世界生成模板。
- `materializeTemplate` 校验基础生成器类型与维度类型，解析参数并构建具体生成器。
- `ResolvedBoxProfile` 同时保留源 profile、物化 profile、具体生成器和可选参数快照。
- `ProfileSnapshot.capture` 将物化 profile 与具体 `LevelStem` 编码进箱庭定义。

## 数据流

1. 玩家通过现有命令指定 schema 2 profile。
2. `BoxCreationService` 先选择实例 seed。
3. `ResolvedBoxProfile.resolve` 从 reloadable registries 查找模板，从静态 registries 查找基础 stem。
4. 参数解析器根据模板哈希、seed 和覆盖参数生成 `WorldGenParameters`。
5. 对应生成器构建具体 `NoiseBasedChunkGenerator`。
6. `ProfileSnapshot` 保存物化 profile 与 `levelStemJson`。
7. `DimensionService` 从快照恢复 `LevelStem`，不再依赖模板注册表。

## 序列化约束

`LevelStem` 快照使用 `RegistryOps + JsonOps` 直接编码。旧实现通过 `NbtOps` 再转换为 JSON，会把直接内联的 `NoiseGeneratorSettings` 布尔字段写成数字 0/1，导致后续 JSON Codec 解析失败。新实现直接输出 JSON 布尔值。

生成器 settings 必须能够通过原版 Codec 完整序列化。模组生成的密度函数组合均由原版注册的密度函数类型构成。

## 确定性

随机输入包括：

- 实例 seed。
- 模板规范化 JSON 的 SHA-256 哈希。
- 模板 ID。

预设随机模式在不指定名称时按 seed 选择；指定名称时使用命名预设。覆盖参数在随机或预设选择之后应用，并再次按模板范围校验。

## 错误处理

- 未知模板、未知预设、未知覆盖字段、越界参数、非法尺度和非噪声基础生成器均在创建维度前拒绝。
- 创建失败沿用现有 `BoxCreationService` 路径，将箱庭标记为 `RECOVERY_REQUIRED`，不保留活动维度。
- 模板解析失败不会静默回退到原版生成器，避免产生与玩家配置不一致的世界。

## 性能约束

- 不在创建时预生成整个维度。
- 密度与气候尺度上限为 4.0，海平面偏移上限为 ±64。
- 浮岛外快速虚空等优化属于后续浮岛阶段。
- 全随机模板仍使用原版噪声生成管线，不引入无界搜索或生成线程阻塞。

## 兼容性

- schema 1 profile 继续使用原有 stem/inline 解析路径。
- 已有 catalog 和 level stem 快照不迁移、不重写。
- 专用服务端继续只加载 common 代码。
- 客户端无需安装模组。
- 模板注册表为服务端 reloadable registry，不新增客户端网络协议。

## 验证

- Codec 单元测试覆盖新旧 schema、范围、预设和未知字段。
- 参数解析测试覆盖确定性、覆盖参数、越界和预设选择。
- 生成器测试覆盖字段保留、非法参数和 settings 序列化。
- 快照测试覆盖模板物化和相同 seed 的生成器一致性。
- 内置资源测试覆盖三类模板与三类 profile 路径和解析。
- 服务端冒烟覆盖三类模板加载、创建、物化快照、归档和恢复。
