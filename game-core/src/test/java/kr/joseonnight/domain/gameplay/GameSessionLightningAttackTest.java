package kr.joseonnight.domain.gameplay;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class GameSessionLightningAttackTest {

    @Test
    void thunderBellStrikesTheNearestEnemyWithoutCreatingAThunderProjectile() {
        GameSession session = GameSession.running(lightningRules(), 20260806L);
        session.equipItem(ItemType.THUNDER_BELL);
        session.spawnEnemy(120.0, 20.0, 120.0);

        session.tick(0.01);

        GameState state = session.state();
        assertThat(state.projectiles())
                .noneMatch(projectile -> ItemType.THUNDER_BELL.id().equals(projectile.kindId()));
        assertThat(state.lightningStrikes()).singleElement().satisfies(strike -> {
            assertThat(strike.kindId()).isEqualTo(ItemType.THUNDER_BELL.id());
            assertThat(strike.x()).isEqualTo(120.0);
            assertThat(strike.y()).isEqualTo(20.0);
            assertThat(strike.remainingSeconds()).isPositive();
        });
        assertThat(state.killCount()).isEqualTo(1);
        assertThat(state.enemies()).isEmpty();
        assertThat(state.soulFlames()).hasSize(1);
        assertThat(state.soundEvents())
                .extracting(SoundEvent::type)
                .contains(SoundCue.THUNDER_BELL_ATTACK);
    }

    @Test
    void lightningVisualExpiresWithoutChangingTheConfirmedKill() {
        GameSession session = GameSession.running(lightningRules(), 20260806L);
        session.equipItem(ItemType.THUNDER_BELL);
        session.spawnEnemy(120.0, 20.0, 120.0);
        session.tick(0.01);

        session.tick(0.25);

        assertThat(session.state().lightningStrikes()).isEmpty();
        assertThat(session.state().killCount()).isEqualTo(1);
    }

    @Test
    void upgradedThunderBellStrikesDistinctNearestEnemies() {
        GameSession session = GameSession.running(lightningRules(), 20260806L);
        equipToLevel(session, ItemType.THUNDER_BELL, 3);
        session.spawnEnemy(180.0, 0.0, 200.0);
        session.spawnEnemy(80.0, 0.0, 200.0);
        session.spawnEnemy(120.0, 0.0, 200.0);

        session.tick(0.01);

        GameState state = session.state();
        assertThat(state.lightningStrikes())
                .extracting(LightningStrikeState::x)
                .containsExactly(80.0, 120.0);
        assertThat(state.killCount()).isEqualTo(2);
        assertThat(state.enemies()).singleElement().satisfies(enemy ->
                assertThat(enemy.x()).isEqualTo(180.0));
    }

    @Test
    void equalDistanceTargetsAreOrderedByTheStableEnemyId() {
        GameSession session = GameSession.running(lightningRules(), 20260806L);
        session.equipItem(ItemType.THUNDER_BELL);
        session.spawnEnemy(100.0, 0.0, 1_000.0);
        session.spawnEnemy(-100.0, 0.0, 1_000.0);

        session.tick(0.01);

        assertThat(session.state().lightningStrikes()).singleElement().satisfies(strike -> {
            assertThat(strike.x()).isEqualTo(100.0);
            assertThat(strike.y()).isZero();
        });
        assertThat(session.state().killCount()).isZero();
    }

    @ParameterizedTest
    @EnumSource(value = EvolutionType.class, names = {
            "HEAVENLY_THUNDER_SEAL",
            "THUNDER_FLAME_DIVINE_ORB"
    })
    void lightningEvolutionsUseTheDirectStrikePath(EvolutionType evolution) {
        GameSession session = GameSession.running(lightningRules(), 20260806L);
        evolve(session, evolution);
        session.spawnEnemy(100.0, 0.0, 250.0);

        session.tick(0.01);

        GameState state = session.state();
        assertThat(state.projectiles())
                .noneMatch(projectile -> evolution.id().equals(projectile.kindId()));
        assertThat(state.lightningStrikes()).singleElement().satisfies(strike ->
                assertThat(strike.kindId()).isEqualTo(evolution.id()));
        assertThat(state.killCount()).isEqualTo(1);
        assertThat(state.soundEvents())
                .extracting(SoundEvent::type)
                .contains(SoundCue.forEvolution(evolution));
    }

    private static void evolve(GameSession session, EvolutionType evolution) {
        equipToLevel(session, evolution.firstMaterial(), ItemLoadout.MAX_ITEM_LEVEL);
        equipToLevel(session, evolution.secondMaterial(), ItemLoadout.MAX_ITEM_LEVEL);
        session.evolve(evolution);
    }

    private static void equipToLevel(GameSession session, ItemType item, int targetLevel) {
        while (itemLevel(session, item) < targetLevel) {
            session.equipItem(item);
        }
    }

    private static int itemLevel(GameSession session, ItemType item) {
        return session.state().items().stream()
                .filter(state -> state.itemId().equals(item.id()))
                .mapToInt(ItemState::level)
                .findFirst()
                .orElse(0);
    }

    private static GameRules lightningRules() {
        return new GameRules(
                240.0,
                18.0,
                200.0,
                300.0,
                0.0,
                120.0,
                17.0,
                100.0,
                100.0,
                1.0,
                20.0,
                5,
                10,
                20,
                20);
    }
}
