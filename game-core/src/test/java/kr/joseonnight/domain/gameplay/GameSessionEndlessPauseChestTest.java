package kr.joseonnight.domain.gameplay;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class GameSessionEndlessPauseChestTest {

    @Test
    void keepsRunningAndAccumulatingTimeBeyondFiveMinutes() {
        GameSession session = GameSession.running(quietRules(), 1L);

        session.tick(301.0);

        assertThat(session.state().phase()).isEqualTo(GamePhase.RUNNING);
        assertThat(session.state().elapsedSeconds()).isEqualTo(301.0);
        assertThat(session.state().soundEvents())
                .extracting(SoundEvent::type)
                .doesNotContain(SoundCue.DEFEAT);
    }

    @Test
    void enemyContactStillCausesDefeatAfterFiveMinutes() {
        GameSession session = GameSession.running(
                quietRules(),
                101L,
                CharacterType.GALE_SHAMAN);
        session.tick(301.0);
        assertThat(session.state().phase()).isEqualTo(GamePhase.RUNNING);

        session.spawnEnemy(0.0, 0.0, 1_000.0);
        session.tick(0.01);

        assertThat(session.state().phase()).isEqualTo(GamePhase.DEFEAT);
        assertThat(session.state().elapsedSeconds()).isGreaterThan(300.0);
        assertThat(session.state().soundEvents())
                .extracting(SoundEvent::type)
                .contains(SoundCue.DEFEAT);
    }

    @Test
    void pauseFreezesSimulationAndChestTimerAndClearsMovementInput() {
        GameSession session = GameSession.running(quietRules(), 2L);
        session.setInput(new InputState(false, false, false, true));
        session.setPaused(true);
        GameState paused = session.state();

        session.tick(120.0);

        assertThat(session.state()).isEqualTo(paused);
        session.setPaused(false);
        session.tick(59.0);
        assertThat(session.state().player().x()).isZero();
        assertThat(session.state().chests()).hasSize(5);
        session.tick(1.0);
        assertThat(session.state().chests()).hasSize(6);
    }

    @Test
    void pauseDoesNotReplaceAnActiveLevelUpChoice() {
        GameSession session = GameSession.running(levelUpRules(), 3L);
        for (int tick = 0; tick < 30 && session.state().phase() != GamePhase.LEVEL_UP; tick++) {
            session.tick(0.1);
        }
        assertThat(session.state().phase()).isEqualTo(GamePhase.LEVEL_UP);
        List<RewardOption> offered = session.state().levelUpOptions();

        session.setPaused(true);
        session.tick(100.0);
        session.setPaused(false);

        assertThat(session.state().phase()).isEqualTo(GamePhase.LEVEL_UP);
        assertThat(session.state().levelUpOptions()).isEqualTo(offered);
        session.chooseLevelUp(offered.getFirst().optionId());
        assertThat(session.state().phase()).isEqualTo(GamePhase.RUNNING);
    }

    @Test
    void spawnsOneDeterministicChestEveryMinuteWithinDistanceAndSpacingRules() {
        GameSession first = GameSession.running(quietRules(), 4L);
        GameSession second = GameSession.running(quietRules(), 4L);
        List<ChestState> initial = new ArrayList<>(first.state().chests());

        first.tick(59.0);
        assertThat(first.state().chests()).hasSize(5);
        first.tick(1.0);
        second.tick(60.0);

        assertThat(first.state().chests()).isEqualTo(second.state().chests());
        assertThat(first.state().chests()).hasSize(6);
        ChestState spawned = first.state().chests().getLast();
        assertThat(Math.hypot(spawned.x(), spawned.y())).isBetween(600.0, 2_200.0);
        assertThat(initial).allSatisfy(chest ->
                assertThat(Math.hypot(spawned.x() - chest.x(), spawned.y() - chest.y()))
                        .isGreaterThanOrEqualTo(96.0));
    }

    @Test
    void capsActiveChestsAtEightAndRefillsOnTheNextRunningTick() {
        GameSession session = GameSession.running(quietRules(), 5L);
        session.tick(300.0);
        assertThat(session.state().chests()).hasSize(8);
        ChestState yellowChest = session.state().chests().stream()
                .filter(chest -> chest.type() == ChestType.YELLOW)
                .findFirst()
                .orElseThrow();

        session.movePlayerTo(yellowChest.x(), yellowChest.y());
        session.tick(0.01);
        assertThat(session.state().chests()).hasSize(7);
        if (session.state().phase() == GamePhase.CHEST_REWARD) {
            List<RewardOption> offered = session.state().chestRewardOptions();
            session.setPaused(true);
            session.tick(100.0);
            session.setPaused(false);
            assertThat(session.state().phase()).isEqualTo(GamePhase.CHEST_REWARD);
            assertThat(session.state().chestRewardOptions()).isEqualTo(offered);
            session.chooseChestReward(offered.getFirst().optionId());
        }
        session.tick(0.01);

        assertThat(session.state().chests()).hasSize(8);
    }

    @Test
    void periodicChestTypesFollowTheConfiguredSixtyFortyChoice() {
        long yellow = 0L;
        int sessions = 200;
        for (int seed = 0; seed < sessions; seed++) {
            GameSession session = GameSession.running(quietRules(), seed);
            session.tick(60.0);
            if (session.state().chests().getLast().type() == ChestType.YELLOW) {
                yellow++;
            }
        }

        assertThat(yellow).isBetween(100L, 140L);
    }

    @Test
    void standardEnemyIntervalAndCapAreThirtyPercentHigherAndStillAccelerateEachMinute() {
        assertThat(GameRules.standard().enemySpawnIntervalSeconds())
                .isEqualTo(0.5 / 1.3);
        assertThat(GameRules.standard().maxEnemies()).isEqualTo(286);
        GameSession session = GameSession.running(spawnRules(), 6L);
        for (int tick = 0; tick < 3_600; tick++) {
            session.tick(1.0 / 60.0);
        }
        int afterFirstMinute = session.state().enemies().size();
        for (int tick = 0; tick < 600; tick++) {
            session.tick(1.0 / 60.0);
        }

        assertThat(afterFirstMinute).isBetween(155, 157);
        assertThat(session.state().enemies().size() - afterFirstMinute).isBetween(31, 32);
    }

    @Test
    void acceleratedEnemyIntervalNeverDropsBelowThirtyPercentFasterMinimum() {
        GameSession session = GameSession.running(spawnRules(4_000), 7L);
        session.tick(420.0);
        int beforeMeasurement = session.state().enemies().size();

        session.tick(15.0);

        assertThat(session.state().enemies().size() - beforeMeasurement)
                .isBetween(130, 131);
    }

    @Test
    void enemySpawningNeverExceedsTheTwoHundredEightySixEnemyCap() {
        GameSession session = GameSession.running(spawnRules(), 8L);

        session.tick(420.0);

        assertThat(session.state().enemies()).hasSize(286);
    }

    private static GameRules quietRules() {
        return new GameRules(
                240.0,
                18.0,
                760.0,
                1_000.0,
                0.0,
                20.0,
                17.0,
                520.0,
                20.0,
                0.65,
                100.0,
                5,
                220,
                160,
                300);
    }

    private static GameRules levelUpRules() {
        return new GameRules(
                240.0,
                18.0,
                100.0,
                0.1,
                0.0,
                20.0,
                17.0,
                2_000.0,
                100.0,
                0.1,
                200.0,
                1,
                1,
                20,
                20);
    }

    private static GameRules spawnRules() {
        return spawnRules(286);
    }

    private static GameRules spawnRules(int maxEnemies) {
        return new GameRules(
                240.0,
                18.0,
                2_000.0,
                0.5 / 1.3,
                0.0,
                10_000.0,
                17.0,
                100.0,
                1.0,
                100.0,
                100.0,
                5,
                maxEnemies,
                10,
                10);
    }
}
