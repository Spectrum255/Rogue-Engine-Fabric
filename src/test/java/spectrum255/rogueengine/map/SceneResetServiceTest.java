package spectrum255.rogueengine.map;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

class SceneResetServiceTest {
	@Test
	void manualResetRejectsOnlinePlayers() {
		assertEquals(SceneResetService.Decision.REJECT, SceneResetService.decide(policy(MapScene.RefreshTrigger.MANUAL),
				MapScene.RefreshTrigger.MANUAL, true));
	}

	@Test
	void onEmptyResetRejectsOnlinePlayers() {
		assertEquals(SceneResetService.Decision.REJECT, SceneResetService.decide(policy(MapScene.RefreshTrigger.ON_EMPTY),
				MapScene.RefreshTrigger.ON_EMPTY, true));
	}

	@Test
	void deathAndCompletionBecomePendingWhilePlayersAreOnline() {
		assertEquals(SceneResetService.Decision.PENDING, SceneResetService.decide(policy(MapScene.RefreshTrigger.ON_DEATH),
				MapScene.RefreshTrigger.ON_DEATH, true));
		assertEquals(SceneResetService.Decision.PENDING, SceneResetService.decide(policy(MapScene.RefreshTrigger.ON_COMPLETED),
				MapScene.RefreshTrigger.ON_COMPLETED, true));
	}

	@Test
	void unconfiguredTriggerIsRejected() {
		assertEquals(SceneResetService.Decision.REJECT, SceneResetService.decide(policy(MapScene.RefreshTrigger.MANUAL),
				MapScene.RefreshTrigger.ON_EMPTY, false));
	}

	@Test
	void configuredTriggerRunsWhenEmpty() {
		assertEquals(SceneResetService.Decision.RESET, SceneResetService.decide(policy(MapScene.RefreshTrigger.ON_EMPTY),
				MapScene.RefreshTrigger.ON_EMPTY, false));
	}

	private MapScene.RefreshPolicy policy(MapScene.RefreshTrigger trigger) {
		return new MapScene.RefreshPolicy(List.of(trigger), MapScene.PlayerResetPolicy.NONE);
	}
}
