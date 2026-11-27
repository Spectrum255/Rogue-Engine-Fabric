package spectrum255.rogueengine.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.minecraft.resources.Identifier;

class MapPoolDrawServiceTest {
	@TempDir
	Path tempDirectory;

	@Test
	void sameInputsProduceSameSelection() {
		MapPool pool = pool(MapPool.RepeatPolicy.ALLOW);

		assertEquals(draw(pool, 42L, all(), SelectionRequest.all(), null).selection(),
				draw(pool, 42L, all(), SelectionRequest.all(), null).selection());
	}

	@Test
	void differentSeedsCanProduceDifferentSelections() {
		MapPool pool = pool(MapPool.RepeatPolicy.ALLOW);
		Set<Identifier> selections = new java.util.HashSet<>();
		for (long seed = 0; seed < 16; seed++) {
			selections.add(draw(pool, seed, all(), SelectionRequest.all(), null).selection().sceneId());
		}

		assertEquals(2, selections.size());
	}

	@Test
	void templateAndRequestRangesUseIntersection() {
		MapPool pool = pool(MapPool.RepeatPolicy.ALLOW);
		MapPool.SelectionRange templateRange = range("a");
		SelectionRequest request = new SelectionRequest(List.of(id("a"), id("b")), List.of());

		assertEquals(id("a"), draw(pool, 42L, templateRange, request, null).selection().sceneId());
	}

	@Test
	void poolDefaultRangeRestrictsCandidates() {
		MapPool pool = new MapPool(1,
				List.of(new MapPool.WeightedScene(id("a"), 1), new MapPool.WeightedScene(id("b"), 1)),
				range("a"), MapPool.RepeatPolicy.ALLOW, refresh(), budget());

		for (long seed = 0; seed < 16; seed++) {
			assertEquals(id("a"), draw(pool, seed, all(), SelectionRequest.all(), null).selection().sceneId());
		}
	}

	@Test
	void rejectsEmptyIntersection() {
		MapPool pool = pool(MapPool.RepeatPolicy.ALLOW);
		MapPool.SelectionRange templateRange = range("a");
		SelectionRequest request = new SelectionRequest(List.of(id("b")), List.of());

		assertThrows(IllegalArgumentException.class, () -> draw(pool, 42L, templateRange, request, null));
	}

	@Test
	void noRepeatCycleDoesNotRepeatUntilPoolIsExhausted() {
		MapPool pool = pool(MapPool.RepeatPolicy.CYCLE_NO_REPEAT);
		MapPoolDrawService.DrawResult firstResult = draw(pool, 1L, all(), SelectionRequest.all(), null);
		Identifier first = firstResult.selection().sceneId();
		MapPoolDrawService.DrawResult secondResult = draw(pool, 2L, all(), SelectionRequest.all(), firstResult.nextState());
		Identifier second = secondResult.selection().sceneId();

		assertNotEquals(first, second);
		assertEquals(1, firstResult.nextState().remaining().size());
		assertTrue(secondResult.nextState().remaining().isEmpty());
	}

	@Test
	void allowPolicyCanRepeatSingleEntry() {
		MapPool pool = new MapPool(1, List.of(new MapPool.WeightedScene(id("a"), 1)), all(),
				MapPool.RepeatPolicy.ALLOW, refresh(), budget());

		assertEquals(id("a"), draw(pool, 1L, all(), SelectionRequest.all(), null).selection().sceneId());
		assertEquals(id("a"), draw(pool, 2L, all(), SelectionRequest.all(), null).selection().sceneId());
	}

	@Test
	void drawStateSurvivesDiskRoundTrip() throws IOException {
		MapPool pool = pool(MapPool.RepeatPolicy.CYCLE_NO_REPEAT);
		MapPoolDrawState state = draw(pool, 1L, all(), SelectionRequest.all(), null).nextState();
		Path file = tempDirectory.resolve("pool.json");

		MapPoolDrawService.saveState(file, state);
		MapPoolDrawState loaded = MapPoolDrawService.loadState(file, id("pool")).orElseThrow();

		assertEquals(state, loaded);
	}

	@Test
	void missingStateStartsEmpty() throws IOException {
		assertTrue(MapPoolDrawService.loadState(tempDirectory.resolve("missing.json"), id("pool")).isEmpty());
	}

	private MapPoolDrawService.DrawResult draw(MapPool pool, long seed, MapPool.SelectionRange templateRange,
			SelectionRequest request, MapPoolDrawState state) {
		return MapPoolDrawService.draw(pool, "pool-hash", seed, id("pool"), templateRange, request, Map.of(), state);
	}

	private MapPool pool(MapPool.RepeatPolicy repeatPolicy) {
		return new MapPool(1,
				List.of(new MapPool.WeightedScene(id("a"), 1), new MapPool.WeightedScene(id("b"), 1)),
				all(), repeatPolicy, refresh(), budget());
	}

	private MapPool.SelectionRange all() {
		return new MapPool.SelectionRange(List.of(), List.of());
	}

	private MapPool.SelectionRange range(String scene) {
		return new MapPool.SelectionRange(List.of(id(scene)), List.of());
	}

	private MapScene.RefreshPolicy refresh() {
		return new MapScene.RefreshPolicy(List.of(MapScene.RefreshTrigger.MANUAL), MapScene.PlayerResetPolicy.NONE);
	}

	private MapPool.PlacementBudget budget() {
		return new MapPool.PlacementBudget(4, 5, 128, 256);
	}

	private Identifier id(String path) {
		return Identifier.fromNamespaceAndPath("test", path);
	}
}
