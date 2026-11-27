# 玩家状态完整重置实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现地图箱庭 `player_reset=full` 的每玩家生存状态基线、刷新水位恢复、事务回滚和持久化。

**Architecture:** 新增不可变玩家状态模型、版本化 NBT 存储和可测试的水位事务服务；原版 `ServerPlayer` 通过独立适配器接入。状态准备挂在现有库存进入流程，水位提交挂在传送返回事件，刷新服务只继续增加箱庭 `reset_count`。

**Tech Stack:** Java 25、Minecraft 26.3 mapped API、Fabric Loader 0.20.1、Fabric API、JUnit 5、Gradle。

**设计依据：** `docs/superpowers/specs/2026-11-26-map-player-state-reset-design.md`

**工作区说明：** 当前工作区没有 Git 元数据，本计划不包含 commit 步骤。

---

## 已核对的 26.3 API

- `LivingEntity.getHealth/setHealth`、`Entity.getAirSupply/setAirSupply`。
- `Player.experienceLevel`、`experienceProgress`、`totalExperience` 为 public 字段。
- `ServerPlayer.setExperienceLevels(int)`、`setExperiencePoints(int)` 会标记经验同步。
- `FoodData` 没有耗尽值 getter；使用 `addAdditionalSaveData(ValueOutput)` 和 `readAdditionalSaveData(ValueInput)` 完整保存饥饿数据。
- `TagValueOutput.createWithoutContext(ProblemReporter)`、`TagValueInput.create(ProblemReporter, HolderLookup.Provider, CompoundTag)`。
- `MobEffectInstance.CODEC` 可配合 `RegistryOps.create(NbtOps.INSTANCE, registries)` 编解码状态效果。
- `ServerGamePacketListenerImpl.disconnect(Component)` 可用于重连恢复失败。
- `RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY)` 可供单元测试构造 registries。

## Task 1：饥饿状态模型

**Files:**
- Create: `src/main/java/spectrum255/rogueengine/map/PlayerFoodState.java`
- Create: `src/test/java/spectrum255/rogueengine/map/PlayerFoodStateTest.java`

- [ ] **Step 1: 编写失败测试**

```java
package spectrum255.rogueengine.map;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;

class PlayerFoodStateTest {
	@BeforeAll
	static void bootstrap() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}
	@Test
	void capturesAndRestoresAllVanillaFoodFields() {
		FoodData food = new FoodData();
		food.setFoodLevel(17);
		food.setSaturation(6.5F);
		food.addExhaustion(3.25F);
		PlayerFoodState state = PlayerFoodState.capture(food);
		FoodData restored = new FoodData();
		restored.setFoodLevel(2);
		restored.setSaturation(0.0F);
		state.apply(restored, registries());
		assertEquals(state, PlayerFoodState.capture(restored));
	}
	@Test
	void rejectsInvalidFoodValues() {
		org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
				() -> new PlayerFoodState(21, 0.0F, 0.0F, 0));
	}
	private RegistryAccess registries() {
		return RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
	}
}
```

- [ ] **Step 2: 运行测试确认失败**
Run: `.\gradlew.bat test --tests spectrum255.rogueengine.map.PlayerFoodStateTest`
Expected: 编译失败，缺少 `PlayerFoodState`。

- [ ] **Step 3: 实现模型**

```java
package spectrum255.rogueengine.map;

import net.minecraft.core.HolderLookup;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
public record PlayerFoodState(int foodLevel, float saturation, float exhaustion, int tickTimer) {
	public PlayerFoodState {
		if (foodLevel < 0 || foodLevel > 20 || !Float.isFinite(saturation) || saturation < 0.0F
				|| !Float.isFinite(exhaustion) || exhaustion < 0.0F || tickTimer < 0) {
			throw new IllegalArgumentException("Invalid player food state");
		}
	}
	public static PlayerFoodState capture(FoodData food) {
		TagValueOutput output = TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);
		food.addAdditionalSaveData(output);
		var tag = output.buildResult();
		return new PlayerFoodState(tag.getIntOr("foodLevel", 0), tag.getFloatOr("foodSaturationLevel", 0.0F),
				tag.getFloatOr("foodExhaustionLevel", 0.0F), tag.getIntOr("foodTickTimer", 0));
	}
	public void apply(FoodData food, HolderLookup.Provider registries) {
		TagValueOutput output = TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);
		output.putInt("foodLevel", foodLevel);
		output.putInt("foodTickTimer", tickTimer);
		output.putFloat("foodSaturationLevel", saturation);
		output.putFloat("foodExhaustionLevel", exhaustion);
		food.readAdditionalSaveData(TagValueInput.create(ProblemReporter.DISCARDING, registries, output.buildResult()));
	}
}
```

- [ ] **Step 4: 运行测试确认通过**
Run: `.\gradlew.bat test --tests spectrum255.rogueengine.map.PlayerFoodStateTest`
Expected: 2 个测试通过。

## Task 2：玩家状态与 NBT Codec

**Files:**
- Create: `src/main/java/spectrum255/rogueengine/map/PlayerSceneState.java`
- Create: `src/main/java/spectrum255/rogueengine/map/PlayerSceneStateCodec.java`
- Create: `src/test/java/spectrum255/rogueengine/map/PlayerSceneStateCodecTest.java`

- [ ] **Step 1: 编写失败测试**

```java
package spectrum255.rogueengine.map;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

import net.minecraft.resources.RegistryOps;

class PlayerSceneStateCodecTest {
	@BeforeAll
	static void bootstrap() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}
	@Test
	void roundTripsStateAndEffects() {
		RegistryAccess registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
		RegistryOps<net.minecraft.nbt.Tag> ops = RegistryOps.create(NbtOps.INSTANCE, registries);
		CompoundTag effect = (CompoundTag) MobEffectInstance.CODEC.encodeStart(ops,
				new MobEffectInstance(MobEffects.REGENERATION, 200, 1)).result().orElseThrow();
		PlayerSceneState state = new PlayerSceneState(UUID.randomUUID(), UUID.randomUUID(), 18.5F,
				new PlayerFoodState(17, 6.5F, 3.25F, 0), 300, 7, 0.35F, 120, List.of(effect), 4L);
		CompoundTag tag = PlayerSceneStateCodec.write(state, registries);
		PlayerSceneState restored = PlayerSceneStateCodec.read(tag, registries);
		assertEquals(state, restored);
	}
	@Test
	void rejectsWrongSchemaVersion() {
		RegistryAccess registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
		CompoundTag tag = new CompoundTag();
		tag.putInt("schema_version", 2);
		org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
				() -> PlayerSceneStateCodec.read(tag, registries));
	}
}
```

- [ ] **Step 2: 运行测试确认失败**
Run: `.\gradlew.bat test --tests spectrum255.rogueengine.map.PlayerSceneStateCodecTest`
Expected: 编译失败，缺少状态模型和 Codec。

- [ ] **Step 3: 实现状态模型**

```java
package spectrum255.rogueengine.map;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import net.minecraft.nbt.CompoundTag;
public record PlayerSceneState(UUID playerId, UUID instanceId, float health, PlayerFoodState food,
		int airSupply, int experienceLevel, float experienceProgress, int experienceTotal,
		List<CompoundTag> effects, long appliedResetCount) {
	private static final int MAX_EFFECTS = 100;
	public PlayerSceneState {
		Objects.requireNonNull(playerId, "playerId");
		Objects.requireNonNull(instanceId, "instanceId");
		Objects.requireNonNull(food, "food");
		if (!Float.isFinite(health) || health <= 0.0F || health > 1_000_000.0F || airSupply < 0
				|| experienceLevel < 0 || !Float.isFinite(experienceProgress)
				|| experienceProgress < 0.0F || experienceProgress > 1.0F || experienceTotal < 0
				|| appliedResetCount < 0L || effects.size() > MAX_EFFECTS) {
			throw new IllegalArgumentException("Invalid player scene state");
		}
		effects = effects.stream().map(CompoundTag::copy).toList();
	}
	public PlayerSceneState withAppliedResetCount(long count) {
		return new PlayerSceneState(playerId, instanceId, health, food, airSupply, experienceLevel,
				experienceProgress, experienceTotal, effects, count);
	}
}
```

- [ ] **Step 4: 实现 Codec**

```java
package spectrum255.rogueengine.map;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import net.minecraft.resources.RegistryOps;
public final class PlayerSceneStateCodec {
	private PlayerSceneStateCodec() {
	}
	public static CompoundTag write(PlayerSceneState state, HolderLookup.Provider registries) {
		CompoundTag root = new CompoundTag();
		root.putInt("schema_version", 1);
		root.put("player_uuid", StringTag.valueOf(state.playerId().toString()));
		root.put("instance_uuid", StringTag.valueOf(state.instanceId().toString()));
		root.putFloat("health", state.health());
		writeFood(root, state.food());
		root.putInt("air_supply", state.airSupply());
		root.putInt("experience_level", state.experienceLevel());
		root.putFloat("experience_progress", state.experienceProgress());
		root.putInt("experience_total", state.experienceTotal());
		ListTag effects = new ListTag();
		state.effects().forEach(effect -> effects.add(effect.copy()));
		root.put("effects", effects);
		root.putLong("applied_reset_count", state.appliedResetCount());
		return root;
	}
	public static PlayerSceneState read(CompoundTag root, HolderLookup.Provider registries) {
		if (root.getIntOr("schema_version", 0) != 1) {
			throw new IllegalArgumentException("Unsupported player scene state schema");
		}
		List<CompoundTag> effects = new ArrayList<>();
		for (Tag element : root.getListOrEmpty("effects")) {
			if (!(element instanceof CompoundTag effect)) {
				throw new IllegalArgumentException("Invalid player effect entry");
			}
			var ops = RegistryOps.create(NbtOps.INSTANCE, registries);
			net.minecraft.world.effect.MobEffectInstance.CODEC.parse(ops, effect).result().orElseThrow();
			effects.add(effect.copy());
		}
		return new PlayerSceneState(uuid(root, "player_uuid"), uuid(root, "instance_uuid"),
				root.getFloatOr("health", 0.0F), readFood(root), root.getIntOr("air_supply", -1),
				root.getIntOr("experience_level", -1), root.getFloatOr("experience_progress", -1.0F),
				root.getIntOr("experience_total", -1), List.copyOf(effects),
				root.getLongOr("applied_reset_count", -1L));
	}
	private static void writeFood(CompoundTag root, PlayerFoodState food) {
		CompoundTag tag = new CompoundTag();
		tag.putInt("foodLevel", food.foodLevel());
		tag.putInt("foodTickTimer", food.tickTimer());
		tag.putFloat("foodSaturationLevel", food.saturation());
		tag.putFloat("foodExhaustionLevel", food.exhaustion());
		root.put("food", tag);
	}
	private static PlayerFoodState readFood(CompoundTag root) {
		if (!(root.get("food") instanceof CompoundTag tag)) {
			throw new IllegalArgumentException("Missing player food state");
		}
		return new PlayerFoodState(tag.getIntOr("foodLevel", -1), tag.getFloatOr("foodSaturationLevel", -1.0F),
				tag.getFloatOr("foodExhaustionLevel", -1.0F), tag.getIntOr("foodTickTimer", -1));
	}
	private static UUID uuid(CompoundTag root, String key) {
		try {
			return UUID.fromString(root.getStringOr(key, ""));
		} catch (IllegalArgumentException exception) {
			throw new IllegalArgumentException("Invalid " + key, exception);
		}
	}
}
```

- [ ] **Step 5: 运行测试确认通过**
Run: `.\gradlew.bat test --tests spectrum255.rogueengine.map.PlayerSceneStateCodecTest`
Expected: 2 个测试通过。

## Task 3：原子状态存储

**Files:**
- Create: `src/main/java/spectrum255/rogueengine/map/PlayerSceneStateStore.java`
- Create: `src/test/java/spectrum255/rogueengine/map/PlayerSceneStateStoreTest.java`

- [ ] **Step 1: 编写失败测试**

```java
package spectrum255.rogueengine.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;

class PlayerSceneStateStoreTest {
	@TempDir
	Path temp;
	@BeforeAll
	static void bootstrap() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}
	@Test
	void writesReadsAndDeletesState() throws IOException {
		PlayerSceneStateStore store = store();
		PlayerSceneState state = state();
		store.write(state);
		assertEquals(state, store.read(state.instanceId(), state.playerId()).orElseThrow());
		store.delete(state.instanceId(), state.playerId());
		assertTrue(store.read(state.instanceId(), state.playerId()).isEmpty());
	}
	@Test
	void rejectsOversizedFile() throws IOException {
		PlayerSceneStateStore store = store();
		PlayerSceneState state = state();
		Files.createDirectories(store.pathFor(state.instanceId(), state.playerId()).getParent());
		Files.write(store.pathFor(state.instanceId(), state.playerId()), new byte[65 * 1024]);
		assertThrows(IOException.class, () -> store.read(state.instanceId(), state.playerId()));
	}
	private PlayerSceneStateStore store() {
		return new PlayerSceneStateStore(temp, RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
	}
	private PlayerSceneState state() {
		return new PlayerSceneState(UUID.randomUUID(), UUID.randomUUID(), 20.0F,
				new PlayerFoodState(20, 5.0F, 0.0F, 0), 300, 0, 0.0F, 0, List.of(), 0L);
	}
}
```

- [ ] **Step 2: 运行测试确认失败**
Run: `.\gradlew.bat test --tests spectrum255.rogueengine.map.PlayerSceneStateStoreTest`
Expected: 编译失败，缺少 `PlayerSceneStateStore`。

- [ ] **Step 3: 实现存储**

```java
package spectrum255.rogueengine.map;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;

import spectrum255.rogueengine.persistence.AtomicFiles;
public final class PlayerSceneStateStore {
	private static final int MAX_FILE_BYTES = 64 * 1024;
	private final Path root;
	private final HolderLookup.Provider registries;
	public PlayerSceneStateStore(Path root, HolderLookup.Provider registries) {
		this.root = root.toAbsolutePath().normalize();
		this.registries = registries;
	}
	public Path pathFor(UUID instanceId, UUID playerId) throws IOException {
		Path path = root.resolve("scenes").resolve(instanceId.toString()).resolve("players")
				.resolve(playerId + ".dat");
		AtomicFiles.verifyManagedPath(root, path);
		return path;
	}
	public Optional<PlayerSceneState> read(UUID instanceId, UUID playerId) throws IOException {
		Path path = pathFor(instanceId, playerId);
		if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
			return Optional.empty();
		}
		if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.size(path) > MAX_FILE_BYTES) {
			throw new IOException("Invalid player scene state file: " + path);
		}
		try (InputStream input = Files.newInputStream(path)) {
			CompoundTag tag = NbtIo.readCompressed(input, NbtAccounter.create(MAX_FILE_BYTES));
			PlayerSceneState state = PlayerSceneStateCodec.read(tag, registries);
			if (!state.instanceId().equals(instanceId) || !state.playerId().equals(playerId)) {
				throw new IOException("Player scene state identity mismatch");
			}
			return Optional.of(state);
		}
	}
	public void write(PlayerSceneState state) throws IOException {
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		NbtIo.writeCompressed(PlayerSceneStateCodec.write(state, registries), output);
		if (output.size() > MAX_FILE_BYTES) {
			throw new IOException("Player scene state exceeds " + MAX_FILE_BYTES + " bytes");
		}
		AtomicFiles.writeAtomic(pathFor(state.instanceId(), state.playerId()), output.toByteArray());
	}
	public void delete(UUID instanceId, UUID playerId) throws IOException {
		Files.deleteIfExists(pathFor(instanceId, playerId));
	}
}
```

- [ ] **Step 4: 运行测试确认通过**
Run: `.\gradlew.bat test --tests spectrum255.rogueengine.map.PlayerSceneStateStoreTest`
Expected: 2 个测试通过。

## Task 4：水位决策与快照路径

**Files:**
- Create: `src/main/java/spectrum255/rogueengine/map/PlayerSceneStatePolicy.java`
- Create: `src/test/java/spectrum255/rogueengine/map/PlayerSceneStatePolicyTest.java`
- Modify: `src/main/java/spectrum255/rogueengine/map/SceneSelectionSnapshot.java`
- Modify: `src/test/java/spectrum255/rogueengine/map/SceneSelectionSnapshotTest.java`

- [ ] **Step 1: 编写失败测试**

```java
package spectrum255.rogueengine.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class PlayerSceneStatePolicyTest {
	@Test
	void decidesCaptureKeepAndRestore() {
		assertEquals(PlayerSceneStatePolicy.Action.CAPTURE, PlayerSceneStatePolicy.decide(false, 0L, 0L));
		assertEquals(PlayerSceneStatePolicy.Action.KEEP, PlayerSceneStatePolicy.decide(true, 2L, 2L));
		assertEquals(PlayerSceneStatePolicy.Action.RESTORE, PlayerSceneStatePolicy.decide(true, 2L, 3L));
	}
	@Test
	void rejectsFutureWatermark() {
		assertThrows(IllegalArgumentException.class, () -> PlayerSceneStatePolicy.decide(true, 4L, 3L));
	}
}
```

同时在 `SceneSelectionSnapshotTest` 增加：

```java
@Test
void fullResetCapturesPlayerStateDirectory() {
	MapScene scene = new MapScene(1, Identifier.withDefaultNamespace("structure"),
			new MapScene.SceneBounds(BlockPos.ZERO, BlockPos.ZERO), BlockPos.ZERO, BlockPos.ZERO,
			net.minecraft.world.level.block.Rotation.NONE, net.minecraft.world.level.block.Mirror.NONE, List.of(),
			new MapScene.RefreshPolicy(List.of(MapScene.RefreshTrigger.MANUAL), MapScene.PlayerResetPolicy.FULL));
	SceneSelectionSnapshot snapshot = SceneSelectionSnapshot.capture(instanceId,
			new MapSelection(Identifier.withDefaultNamespace("pool"), "a".repeat(64),
					Identifier.withDefaultNamespace("scene"), 1, 42L, 0L),
			scene, new SceneCacheService.CachedScene("scenes/" + instanceId + "/source.nbt", "b".repeat(64)));
	assertEquals(Optional.of("scenes/" + instanceId + "/players"), snapshot.playerStatePath());
}
```

- [ ] **Step 2: 运行测试确认失败**
Run: `.\gradlew.bat test --tests spectrum255.rogueengine.map.PlayerSceneStatePolicyTest --tests spectrum255.rogueengine.map.SceneSelectionSnapshotTest`
Expected: 缺少策略类，FULL 快照路径为空导致断言失败。

- [ ] **Step 3: 实现策略**

```java
package spectrum255.rogueengine.map;
public final class PlayerSceneStatePolicy {
	private PlayerSceneStatePolicy() {
	}
	public static Action decide(boolean stateExists, long appliedResetCount, long currentResetCount) {
		if (!stateExists) {
			return Action.CAPTURE;
		}
		if (appliedResetCount > currentResetCount) {
			throw new IllegalArgumentException("Player state watermark is ahead of the box reset count");
		}
		return appliedResetCount < currentResetCount ? Action.RESTORE : Action.KEEP;
	}
	public enum Action {
		CAPTURE,
		KEEP,
		RESTORE
	}
}
```

- [ ] **Step 4: 修改快照捕获**

将 `SceneSelectionSnapshot.capture` 末尾参数中的 `Optional.empty()` 替换为：

```java
scene.refresh().playerReset() == MapScene.PlayerResetPolicy.FULL
		? Optional.of("scenes/" + instanceId + "/players")
		: Optional.empty()
```

- [ ] **Step 5: 运行测试确认通过**
Run: `.\gradlew.bat test --tests spectrum255.rogueengine.map.PlayerSceneStatePolicyTest --tests spectrum255.rogueengine.map.SceneSelectionSnapshotTest`
Expected: 全部通过。

## Task 5：可测试的玩家状态事务服务

**Files:**
- Create: `src/main/java/spectrum255/rogueengine/map/PlayerSceneStateAccess.java`
- Create: `src/main/java/spectrum255/rogueengine/map/PlayerSceneStateService.java`
- Create: `src/test/java/spectrum255/rogueengine/map/PlayerSceneStateServiceTest.java`

- [ ] **Step 1: 编写失败测试**

```java
package spectrum255.rogueengine.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

class PlayerSceneStateServiceTest {
	@TempDir
	Path temp;
	private PlayerSceneStateStore store;
	private final UUID instanceId = UUID.randomUUID();
	@BeforeAll
	static void bootstrap() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}
	@Test
	void capturesOnceAndRestoresOnlyAfterReset() throws IOException {
		PlayerSceneStateService service = service();
		FakeAccess access = new FakeAccess(UUID.randomUUID(), 20.0F, 0L);
		SceneSelectionSnapshot snapshot = snapshot(access.playerId(), 0L);
		service.prepareEntry(access, snapshot);
		service.commitEntry(access);
		assertEquals(20.0F, access.current.health());
		access.setHealth(7.0F);
		service.prepareEntry(access, snapshot(access.playerId(), 1L));
		assertEquals(20.0F, access.current.health());
		service.rollbackEntry(access);
		assertEquals(7.0F, access.current.health());
		assertEquals(0L, store.read(snapshot.instanceId(), access.playerId()).orElseThrow().appliedResetCount());
		service.prepareEntry(access, snapshot(access.playerId(), 1L));
		service.commitEntry(access);
		assertEquals(1L, store.read(snapshot.instanceId(), access.playerId()).orElseThrow().appliedResetCount());
	}
	@Test
	void failedFirstEntryDoesNotLeaveBaseline() throws IOException {
		PlayerSceneStateService service = service();
		FakeAccess access = new FakeAccess(UUID.randomUUID(), 20.0F, 0L);
		SceneSelectionSnapshot snapshot = snapshot(access.playerId(), 0L);
		service.prepareEntry(access, snapshot);
		service.rollbackEntry(access);
		assertEquals(Optional.empty(), store.read(snapshot.instanceId(), access.playerId()));
	}
	@Test
	void corruptStateRejectsEntry() throws IOException {
		PlayerSceneStateService service = service();
		FakeAccess access = new FakeAccess(UUID.randomUUID(), 20.0F, 0L);
		SceneSelectionSnapshot snapshot = snapshot(access.playerId(), 0L);
		service.prepareEntry(access, snapshot);
		service.commitEntry(access);
		Files.writeString(store.pathFor(snapshot.instanceId(), access.playerId()), "broken");
		assertThrows(IOException.class, () -> service.prepareEntry(access, snapshot(access.playerId(), 1L)));
	}
	private PlayerSceneStateService service() {
		store = new PlayerSceneStateStore(temp, RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
		return new PlayerSceneStateService(store);
	}
	private SceneSelectionSnapshot snapshot(UUID playerId, long resetCount) {
		return new SceneSelectionSnapshot(instanceId, Identifier.withDefaultNamespace("pool"),
				Identifier.withDefaultNamespace("scene"), new SceneSelectionSnapshot.Hashes("a".repeat(64), "b".repeat(64)),
				42L, 0L, BlockPos.ZERO, BlockPos.ZERO, Rotation.NONE, Mirror.NONE,
				new MapScene.SceneBounds(BlockPos.ZERO, BlockPos.ZERO), List.of(),
				"scenes/" + instanceId + "/source.nbt",
				new MapScene.RefreshPolicy(List.of(MapScene.RefreshTrigger.MANUAL), MapScene.PlayerResetPolicy.FULL),
				resetCount, Optional.of("scenes/" + instanceId + "/players"));
	}
	private static final class FakeAccess implements PlayerSceneStateAccess {
		private final UUID playerId;
		private PlayerSceneState current;
		private FakeAccess(UUID playerId, float health, long watermark) {
			this.playerId = playerId;
			this.current = new PlayerSceneState(playerId, UUID.randomUUID(), health,
					new PlayerFoodState(20, 5.0F, 0.0F, 0), 300, 0, 0.0F, 0, List.of(), watermark);
		}
		@Override
		public UUID playerId() {
			return playerId;
		}
		@Override
		public PlayerSceneState capture(UUID instanceId, long watermark) {
			current = new PlayerSceneState(playerId, instanceId, current.health(), current.food(), current.airSupply(),
					current.experienceLevel(), current.experienceProgress(), current.experienceTotal(),
					current.effects(), watermark);
			return current;
		}
		private void setHealth(float health) {
			current = new PlayerSceneState(playerId, current.instanceId(), health, current.food(), current.airSupply(),
					current.experienceLevel(), current.experienceProgress(), current.experienceTotal(), current.effects(),
					current.appliedResetCount());
		}

		@Override
		public void apply(PlayerSceneState state) {
			current = state;
		}
	}
}
```

- [ ] **Step 2: 运行测试确认失败**
Run: `.\gradlew.bat test --tests spectrum255.rogueengine.map.PlayerSceneStateServiceTest`
Expected: 编译失败，缺少接口和服务。

- [ ] **Step 3: 实现访问接口**

```java
package spectrum255.rogueengine.map;

import java.util.UUID;
public interface PlayerSceneStateAccess {
	UUID playerId();
	PlayerSceneState capture(UUID instanceId, long appliedResetCount);
	void apply(PlayerSceneState state);
}
```

- [ ] **Step 4: 实现事务服务**

```java
package spectrum255.rogueengine.map;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import spectrum255.rogueengine.RogueEngineFabric;
public final class PlayerSceneStateService {
	private final PlayerSceneStateStore store;
	private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();
	public PlayerSceneStateService(PlayerSceneStateStore store) {
		this.store = store;
	}
	public void prepareEntry(PlayerSceneStateAccess access, SceneSelectionSnapshot snapshot) throws IOException {
		if (snapshot == null || snapshot.refresh().playerReset() != MapScene.PlayerResetPolicy.FULL) {
			return;
		}
		var existing = store.read(snapshot.instanceId(), access.playerId());
		var action = PlayerSceneStatePolicy.decide(existing.isPresent(),
				existing.map(PlayerSceneState::appliedResetCount).orElse(0L), snapshot.resetCount());
		switch (action) {
			case CAPTURE -> {
				PlayerSceneState baseline = access.capture(snapshot.instanceId(), snapshot.resetCount());
				store.write(baseline);
				pending.put(access.playerId(), new Pending(baseline, snapshot.resetCount(), null, true));
			}
			case KEEP -> pending.remove(access.playerId());
			case RESTORE -> {
				PlayerSceneState baseline = existing.orElseThrow();
				PlayerSceneState rollback = access.capture(snapshot.instanceId(), baseline.appliedResetCount());
				try {
					access.apply(baseline);
				} catch (RuntimeException exception) {
					access.apply(rollback);
					throw exception;
				}
				pending.put(access.playerId(), new Pending(baseline, snapshot.resetCount(), rollback, false));
			}
		}
	}
	public void commitEntry(PlayerSceneStateAccess access) {
		Pending transition = pending.remove(access.playerId());
		if (transition == null || transition.createdBaseline()) {
			return;
		}
		try {
			store.write(transition.baseline().withAppliedResetCount(transition.targetResetCount()));
		} catch (IOException exception) {
			RogueEngineFabric.LOGGER.error("Could not commit player scene state for {}", access.playerId(), exception);
		}
	}
	public void rollbackEntry(PlayerSceneStateAccess access) {
		Pending transition = pending.remove(access.playerId());
		if (transition == null) {
			return;
		}
		try {
			if (transition.createdBaseline()) {
				store.delete(transition.baseline().instanceId(), access.playerId());
			} else if (transition.rollback() != null) {
				access.apply(transition.rollback());
			}
		} catch (Exception exception) {
			RogueEngineFabric.LOGGER.error("Could not roll back player scene state for {}", access.playerId(), exception);
		}
	}
	public void recoverOnJoin(PlayerSceneStateAccess access, SceneSelectionSnapshot snapshot) throws IOException {
		if (snapshot == null || snapshot.refresh().playerReset() != MapScene.PlayerResetPolicy.FULL) {
			return;
		}
		var existing = store.read(snapshot.instanceId(), access.playerId());
		var action = PlayerSceneStatePolicy.decide(existing.isPresent(),
				existing.map(PlayerSceneState::appliedResetCount).orElse(0L), snapshot.resetCount());
		if (action == PlayerSceneStatePolicy.Action.CAPTURE) {
			store.write(access.capture(snapshot.instanceId(), snapshot.resetCount()));
		} else if (action == PlayerSceneStatePolicy.Action.RESTORE) {
			PlayerSceneState baseline = existing.orElseThrow();
			access.apply(baseline);
			store.write(baseline.withAppliedResetCount(snapshot.resetCount()));
		}
	}
	private record Pending(PlayerSceneState baseline, long targetResetCount,
			PlayerSceneState rollback, boolean createdBaseline) {
	}
}
```

- [ ] **Step 5: 运行测试确认通过**
Run: `.\gradlew.bat test --tests spectrum255.rogueengine.map.PlayerSceneStateServiceTest`
Expected: 3 个测试通过。

## Task 6：原版玩家适配器与 Runtime 接入

**Files:**
- Create: `src/main/java/spectrum255/rogueengine/map/VanillaPlayerSceneStateAccess.java`
- Modify: `src/main/java/spectrum255/rogueengine/runtime/ServerRuntime.java`

- [ ] **Step 1: 实现原版适配器**

```java
package spectrum255.rogueengine.map;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;

import net.minecraft.resources.RegistryOps;
public final class VanillaPlayerSceneStateAccess implements PlayerSceneStateAccess {
	private final ServerPlayer player;
	public VanillaPlayerSceneStateAccess(ServerPlayer player) {
		this.player = player;
	}
	@Override
	public UUID playerId() {
		return player.getUUID();
	}
	@Override
	public PlayerSceneState capture(UUID instanceId, long appliedResetCount) {
		var ops = RegistryOps.create(NbtOps.INSTANCE, player.level().registryAccess());
		List<CompoundTag> effects = new ArrayList<>();
		for (MobEffectInstance effect : player.getActiveEffects()) {
			effects.add((CompoundTag) MobEffectInstance.CODEC.encodeStart(ops, effect).result().orElseThrow());
		}
		return new PlayerSceneState(player.getUUID(), instanceId, player.getHealth(),
				PlayerFoodState.capture(player.getFoodData()), player.getAirSupply(), player.experienceLevel,
				player.experienceProgress, player.totalExperience, effects, appliedResetCount);
	}
	@Override
	public void apply(PlayerSceneState state) {
		player.setHealth(state.health());
		player.setAirSupply(state.airSupply());
		state.food().apply(player.getFoodData(), player.level().registryAccess());
		player.setExperienceLevels(state.experienceLevel());
		player.setExperiencePoints(state.experienceTotal());
		player.experienceProgress = state.experienceProgress();
		player.totalExperience = state.experienceTotal();
		var ops = RegistryOps.create(NbtOps.INSTANCE, player.level().registryAccess());
		new ArrayList<>(player.getActiveEffectsMap().keySet()).forEach(player::removeEffect);
		for (CompoundTag tag : state.effects()) {
			MobEffectInstance effect = MobEffectInstance.CODEC.parse(ops, tag).result().orElseThrow();
			player.addEffect(new MobEffectInstance(effect));
		}
	}
}
```

- [ ] **Step 2: 修改 Runtime**

在 `ServerRuntime` 增加字段、构造和访问器：

```java
private final PlayerSceneStateService playerStates;

// 构造函数中，sceneCache 初始化之后
this.playerStates = new PlayerSceneStateService(
		new PlayerSceneStateStore(this.sceneCache.root(), server.registryAccess()));
public PlayerSceneStateService playerStates() {
	return playerStates;
}
```

补充 import：

```java
import spectrum255.rogueengine.map.PlayerSceneStateService;
import spectrum255.rogueengine.map.PlayerSceneStateStore;
```

- [ ] **Step 3: 修正场景缓存根目录并运行测试**
将 `SceneCacheService` 根目录改为引擎数据目录，使缓存路径保持 `scenes/<instance_uuid>/`；销毁隔离传入其 `scenes` 子目录。
为旧版双层 `scenes/scenes/<instance_uuid>/` 缓存增加读取与隔离回退测试。
Run: `.\gradlew.bat test --tests 'spectrum255.rogueengine.map.*' --tests 'spectrum255.rogueengine.persistence.QuarantineServiceTest'`
Expected: 地图与隔离测试全部通过。

## Task 7：库存、传送与重连事务集成

**Files:**
- Modify: `src/main/java/spectrum255/rogueengine/inventory/PlayerInventoryService.java`
- Modify: `src/main/java/spectrum255/rogueengine/mixin/ServerPlayerTeleportMixin.java`
- Modify: `src/main/java/spectrum255/rogueengine/lifecycle/ServerLifecycleHooks.java`

- [ ] **Step 1: 在库存进入成功后准备状态**

在 `PlayerInventoryService.enter` 的 `store.write(...Phase.INSIDE...)` 之后加入：

```java
runtime.playerStates().prepareEntry(
		new VanillaPlayerSceneStateAccess(player), definition.sceneSnapshot());
```

在现有 `catch (Exception exception)` 中、恢复 `outside` 之前加入：

```java
runtime.playerStates().rollbackEntry(new VanillaPlayerSceneStateAccess(player));
```

补充 import：

```java
import spectrum255.rogueengine.map.VanillaPlayerSceneStateAccess;
```

- [ ] **Step 2: 库存回滚同时回滚状态**

在 `PlayerInventoryService.rollback` 的 `try` 块最前面加入：

```java
runtime.playerStates().rollbackEntry(new VanillaPlayerSceneStateAccess(player));
```

- [ ] **Step 3: 传送成功后提交水位**

修改 `ServerPlayerTeleportMixin` 的 RETURN 注入：

```java
ServerPlayer player = (ServerPlayer) (Object) this;
if (callbackInfo.getReturnValue() != null) {
	ServerLifecycleHooks.current(player.level().getServer()).playerStates()
			.commitEntry(new spectrum255.rogueengine.map.VanillaPlayerSceneStateAccess(player));
	return;
}
```

保留现有 `rollback(player)` 分支。

- [ ] **Step 4: 重连时检查水位**

将 `ServerLifecycleHooks.register` 的 JOIN 注册替换为：

```java
ServerPlayerEvents.JOIN.register(ServerLifecycleHooks::onJoin);
```

新增方法：

```java
private static void onJoin(net.minecraft.server.level.ServerPlayer player) {
	ServerRuntime runtime = current(player.level().getServer());
	runtime.inventories().recoverOnJoin(player);
	try {
		var definition = runtime.inventories().definitionFor(player.level().dimension());
		runtime.playerStates().recoverOnJoin(
				new spectrum255.rogueengine.map.VanillaPlayerSceneStateAccess(player),
				definition == null ? null : definition.sceneSnapshot());
	} catch (Exception exception) {
		RogueEngineFabric.LOGGER.error("Could not restore player scene state for {}", player.getUUID(), exception);
		player.connection.disconnect(net.minecraft.network.chat.Component.literal("Could not restore box player state"));
	}
}
```

- [ ] **Step 5: 运行库存、地图和生命周期测试**
Run: `.\gradlew.bat test --tests 'spectrum255.rogueengine.inventory.*' --tests 'spectrum255.rogueengine.map.*' --tests 'spectrum255.rogueengine.persistence.*'`
Expected: 全部通过。

## Task 8：文档与完整验证

**Files:**
- Modify: `docs/map-pools.md`
- Modify: `README.md`

- [ ] **Step 1: 更新地图集文档**

将 `player_reset=full` 从“尚未实现”改为：

- 首次进入建立每玩家生存状态基线。
- 刷新后下次进入恢复生命、饥饿、饱和、耗尽、氧气、经验和状态效果。
- 背包、末影箱、位置不变。
- 状态文件位于 `<world>/data/rogue-engine-fabric/scenes/<instance_uuid>/players/<player_uuid>.dat`。
- 状态文件损坏时取消进入；箱庭内重连恢复失败时断开连接。

删除“当前只实现 none”和“full 尚未实现”的限制描述。

- [ ] **Step 2: 更新 README**

在随机地图集功能中补充 `FULL` 状态重置；从已知限制中删除 `player_reset=full` 未实现，保留场景快照和导入未实现。

- [ ] **Step 3: 完整构建测试**
Run: `.\gradlew.bat clean build test`
Expected: 全部测试通过，无编译错误。

- [ ] **Step 4: 服务端冒烟**
Run: `.\verification\server-smoke.ps1`
Expected: 地图创建、刷新、归档、恢复通过，服务端无新增错误。

- [ ] **Step 5: 客户端在线验收**

1. 将一个测试场景的 `refresh.player_reset` 设为 `full` 并重载数据包。
2. 创建地图箱庭，首次进入并记录生命、饥饿、氧气、经验和状态效果。
3. 修改这些状态后离开箱庭。
4. 箱庭无玩家时执行 `/rogue-engine instance reset <uuid>`。
5. 再次进入，确认恢复首次基线，背包和位置仍按原策略处理。
6. 重复刷新并重连，确认基线不变化、无重复恢复错误。
不要求截图；记录服务器日志和验收结果。
## 完成标准

- `FULL` 状态文件按玩家独立持久化。
- 首次进入只捕获一次基线。
- 刷新后下次进入按水位恢复一次。
- 库存、生存状态和传送失败均可回滚。
- 重连、归档、销毁和崩溃恢复不破坏状态语义。
- `NONE` 与旧箱庭行为不变；单元测试、服务端冒烟和客户端在线验收完成。
