package kr.vamsur.application.gameplay.provided;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import kr.vamsur.application.gameplay.GameService;
import kr.vamsur.domain.gameplay.GamePhase;
import kr.vamsur.domain.gameplay.GameRules;
import kr.vamsur.domain.gameplay.InputState;
import kr.vamsur.domain.gameplay.UpgradeType;
import org.junit.jupiter.api.Test;

class GameServiceTest {

    private static final double TOLERANCE = 1.0e-9;

    @Test
    void startsInLobbyAndStartsANewRunningGame() {
        GameService game = new GameService(quietRules(), 7L);

        assertEquals(GamePhase.LOBBY, game.snapshot().phase());

        game.startNewGame();

        GameSnapshot snapshot = game.snapshot();
        assertEquals(GamePhase.RUNNING, snapshot.phase());
        assertEquals(300.0, snapshot.remainingSeconds(), TOLERANCE);
        assertEquals(1, snapshot.level());
        assertTrue(snapshot.enemies().isEmpty());
    }

    @Test
    void movesWithWasdInput() {
        GameService game = runningGame(quietRules(), 11L);
        game.setInput(new InputState(false, false, false, true));

        game.tick(1.0);

        assertEquals(240.0, game.snapshot().player().x(), TOLERANCE);
        assertEquals(0.0, game.snapshot().player().y(), TOLERANCE);
    }

    @Test
    void normalizesDiagonalMovement() {
        GameService game = runningGame(quietRules(), 11L);
        game.setInput(new InputState(true, false, false, true));

        game.tick(1.0);

        EntitySnapshot player = game.snapshot().player();
        assertEquals(240.0, Math.hypot(player.x(), player.y()), TOLERANCE);
        assertEquals(player.x(), -player.y(), TOLERANCE);
    }

    @Test
    void automaticallyAimsATalismanAtTheNearestEnemy() {
        GameService game = runningGame(combatRules(200.0, 1.0, 100.0, 20.0, 1.0, 10), 31L);

        game.tick(1.0);

        GameSnapshot snapshot = game.snapshot();
        assertEquals(1, snapshot.enemies().size());
        assertEquals(1, snapshot.projectiles().size());
        EntitySnapshot enemy = snapshot.enemies().getFirst();
        EntitySnapshot projectile = snapshot.projectiles().getFirst();
        double expectedRotation = Math.toDegrees(Math.atan2(
                enemy.y() - snapshot.player().y(),
                enemy.x() - snapshot.player().x()
        ));
        assertEquals(expectedRotation, projectile.rotationDegrees(), TOLERANCE);
    }

    @Test
    void projectileKillsEnemyAndDropsSoulFlame() {
        GameService game = runningGame(combatRules(100.0, 0.1, 2_000.0, 100.0, 1.0, 10), 41L);

        tickUntil(game, snapshot -> snapshot.killCount() == 1, 20);

        GameSnapshot snapshot = game.snapshot();
        assertEquals(1, snapshot.killCount());
        assertEquals(1, snapshot.soulFlames().size());
    }

    @Test
    void collectedSoulFlameStartsPausedLevelUpWithThreeUniqueChoices() {
        GameService game = runningGame(combatRules(100.0, 0.1, 2_000.0, 100.0, 200.0, 1), 43L);

        tickUntil(game, snapshot -> snapshot.phase() == GamePhase.LEVEL_UP, 30);

        GameSnapshot paused = game.snapshot();
        assertEquals(1, paused.experience());
        assertEquals(3, paused.upgradeChoices().size());
        assertEquals(3, new HashSet<>(paused.upgradeChoices()).size());

        game.setInput(new InputState(false, false, false, true));
        game.tick(10.0);
        assertEquals(paused, game.snapshot());

        game.chooseUpgrade(paused.upgradeChoices().getFirst());
        assertEquals(GamePhase.RUNNING, game.snapshot().phase());
        assertEquals(2, game.snapshot().level());
        assertEquals(0, game.snapshot().experience());
        assertTrue(game.snapshot().upgradeChoices().isEmpty());
    }

    @Test
    void rejectsAnUpgradeThatWasNotOffered() {
        GameService game = runningGame(quietRules(), 5L);

        assertThrows(IllegalStateException.class,
                () -> game.chooseUpgrade(UpgradeType.MOVEMENT_SPEED));
    }

    @Test
    void touchingAnEnemyImmediatelyDefeatsThePlayer() {
        GameRules rules = combatRules(30.0, 0.01, 100.0, 20.0, 1.0, 10);
        GameService game = runningGame(rules, 47L);

        game.tick(0.01);

        assertEquals(GamePhase.DEFEAT, game.snapshot().phase());
    }

    @Test
    void survivingTheConfiguredDurationWins() {
        GameRules rules = new GameRules(
                0.05,
                240.0,
                18.0,
                760.0,
                10.0,
                55.0,
                20.0,
                17.0,
                520.0,
                20.0,
                0.65,
                100.0,
                5,
                10,
                10,
                10
        );
        GameService game = runningGame(rules, 53L);

        game.tick(0.05);

        assertEquals(GamePhase.VICTORY, game.snapshot().phase());
        assertEquals(0.0, game.snapshot().remainingSeconds(), TOLERANCE);
    }

    @Test
    void standardFiveMinutesWinsAfterExactlyEighteenThousandFixedTicks() {
        GameService game = runningGame(quietRules(), 53L);

        for (int tick = 0; tick < 18_000; tick++) {
            game.tick(1.0 / 60.0);
        }

        assertEquals(GamePhase.VICTORY, game.snapshot().phase());
        assertEquals(300.0, game.snapshot().elapsedSeconds(), TOLERANCE);
        assertEquals(0.0, game.snapshot().remainingSeconds(), TOLERANCE);
    }

    @Test
    void collisionOnTheFinalTickDefeatsBeforeVictory() {
        GameRules rules = new GameRules(
                1.0,
                240.0,
                18.0,
                30.0,
                1.0,
                0.0,
                20.0,
                17.0,
                520.0,
                20.0,
                0.65,
                100.0,
                5,
                10,
                10,
                10
        );
        GameService game = runningGame(rules, 57L);

        game.tick(0.5);
        game.tick(0.5);

        assertEquals(GamePhase.DEFEAT, game.snapshot().phase());
        assertEquals(1.0, game.snapshot().elapsedSeconds(), TOLERANCE);
    }

    @Test
    void restartClearsInputEntitiesCountersAndRandomState() {
        GameRules rules = combatRules(30.0, 0.01, 100.0, 20.0, 1.0, 10);
        GameService game = runningGame(rules, 59L);
        game.tick(0.01);
        EntitySnapshot firstEnemy = game.snapshot().enemies().getFirst();
        assertEquals(GamePhase.DEFEAT, game.snapshot().phase());

        game.setInput(new InputState(false, false, false, true));
        game.startNewGame();

        GameSnapshot restarted = game.snapshot();
        assertEquals(GamePhase.RUNNING, restarted.phase());
        assertEquals(0.0, restarted.elapsedSeconds(), TOLERANCE);
        assertEquals(0.0, restarted.player().x(), TOLERANCE);
        assertTrue(restarted.enemies().isEmpty());
        assertTrue(restarted.projectiles().isEmpty());
        assertTrue(restarted.soulFlames().isEmpty());
        assertEquals(0, restarted.killCount());

        game.tick(0.01);
        EntitySnapshot restartedEnemy = game.snapshot().enemies().getFirst();
        assertEquals(firstEnemy.id(), restartedEnemy.id());
        assertEquals(firstEnemy.x(), restartedEnemy.x(), TOLERANCE);
        assertEquals(firstEnemy.y(), restartedEnemy.y(), TOLERANCE);
        assertEquals(0.0, game.snapshot().player().x(), TOLERANCE);
    }

    @Test
    void snapshotsExposeImmutableCollections() {
        GameService game = runningGame(quietRules(), 61L);

        assertThrows(UnsupportedOperationException.class,
                () -> game.snapshot().enemies().add(game.snapshot().player()));
    }

    @Test
    void equalSeedAndInputProduceEqualSnapshots() {
        GameService first = runningGame(combatRules(200.0, 0.5, 500.0, 20.0, 1.0, 10), 67L);
        GameService second = runningGame(combatRules(200.0, 0.5, 500.0, 20.0, 1.0, 10), 67L);

        for (int tick = 0; tick < 120; tick++) {
            InputState input = tick < 60
                    ? new InputState(false, false, false, true)
                    : new InputState(false, true, true, false);
            first.setInput(input);
            second.setInput(input);
            first.tick(1.0 / 60.0);
            second.tick(1.0 / 60.0);
        }

        assertEquals(first.snapshot(), second.snapshot());
        GameRules slowProjectileRules = combatRules(200.0, 0.5, 1.0, 20.0, 1.0, 10);
        GameService seed67 = runningGame(slowProjectileRules, 67L);
        GameService seed68 = runningGame(slowProjectileRules, 68L);
        seed67.tick(0.5);
        seed68.tick(0.5);
        assertNotEquals(seed67.snapshot().enemies(), seed68.snapshot().enemies());
    }

    private static GameService runningGame(GameRules rules, long seed) {
        GameService game = new GameService(rules, seed);
        game.startNewGame();
        return game;
    }

    private static GameRules quietRules() {
        return new GameRules(
                300.0,
                240.0,
                18.0,
                760.0,
                100.0,
                0.0,
                20.0,
                17.0,
                520.0,
                20.0,
                0.65,
                100.0,
                5,
                10,
                10,
                10
        );
    }

    private static GameRules combatRules(
            double spawnRadius,
            double spawnInterval,
            double projectileSpeed,
            double projectileDamage,
            double magnetRadius,
            int experienceToNextLevel
    ) {
        return new GameRules(
                300.0,
                240.0,
                18.0,
                spawnRadius,
                spawnInterval,
                0.0,
                20.0,
                17.0,
                projectileSpeed,
                projectileDamage,
                0.1,
                magnetRadius,
                experienceToNextLevel,
                1,
                20,
                20
        );
    }

    private static void tickUntil(
            GameService game,
            java.util.function.Predicate<GameSnapshot> condition,
            int maximumTicks
    ) {
        for (int tick = 0; tick < maximumTicks && !condition.test(game.snapshot()); tick++) {
            game.tick(0.1);
        }
        assertTrue(condition.test(game.snapshot()), "condition was not reached within " + maximumTicks + " ticks");
    }
}
