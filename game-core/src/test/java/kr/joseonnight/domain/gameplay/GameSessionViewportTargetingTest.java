package kr.joseonnight.domain.gameplay;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class GameSessionViewportTargetingTest {

    @Test
    void changingTheViewportImmediatelyChangesWhichEnemyCanBeInitiallyTargeted() {
        GameSession session = GameSession.running(
                targetingRules(),
                99L,
                CharacterType.DOKKAEBI_HUNTER,
                new GameViewport(640, 360));
        session.spawnEnemy(0.0, 210.0, 1_000.0);

        session.tick(0.01);

        assertThat(session.state().projectiles())
                .noneMatch(projectile ->
                        ItemType.SEAL_TALISMAN.id().equals(projectile.kindId()));

        session.setViewport(new GameViewport(1_280, 720));
        session.tick(0.01);

        assertThat(session.state().projectiles())
                .anyMatch(projectile ->
                        ItemType.SEAL_TALISMAN.id().equals(projectile.kindId()));
    }

    @ParameterizedTest
    @EnumSource(ItemType.class)
    void everyItemInitiallyTargetsOnlyAnEnemyIntersectingTheViewport(ItemType item) {
        GameSession session = GameSession.running(
                targetingRules(),
                100L + item.ordinal(),
                characterFor(item),
                new GameViewport(640, 360));
        session.equipItem(item);
        session.spawnEnemy(0.0, 210.0, 1_000.0);
        session.spawnEnemy(300.0, 0.0, 1_000.0);

        session.tick(0.01);

        assertAttackPointsRight(session.state(), item.id(), item.attackMode());
    }

    @ParameterizedTest
    @EnumSource(EvolutionType.class)
    void everyEvolutionInitiallyTargetsOnlyAnEnemyIntersectingTheViewport(
            EvolutionType evolution
    ) {
        GameSession session = GameSession.running(
                targetingRules(),
                200L + evolution.ordinal(),
                characterFor(evolution),
                new GameViewport(640, 360));
        evolve(session, evolution);
        session.spawnEnemy(0.0, 210.0, 1_000.0);
        session.spawnEnemy(300.0, 0.0, 1_000.0);

        session.tick(0.01);

        assertAttackPointsRight(session.state(), evolution.id(), evolution.attackMode());
    }

    @ParameterizedTest
    @EnumSource(ItemType.class)
    void itemsDoNotFireWhenEveryEnemyIsOutsideTheViewport(ItemType item) {
        GameSession session = GameSession.running(
                targetingRules(),
                300L + item.ordinal(),
                characterFor(item),
                new GameViewport(640, 360));
        session.equipItem(item);
        session.spawnEnemy(0.0, 210.0, 1_000.0);

        session.tick(0.01);

        assertThat(session.state().projectiles())
                .noneMatch(projectile -> item.id().equals(projectile.kindId()));
        assertThat(session.state().lightningStrikes())
                .noneMatch(strike -> item.id().equals(strike.kindId()));
        assertThat(session.state().soundEvents())
                .extracting(SoundEvent::type)
                .doesNotContain(SoundCue.forItem(item));
    }

    @ParameterizedTest
    @EnumSource(EvolutionType.class)
    void evolutionsDoNotFireWhenEveryEnemyIsOutsideTheViewport(EvolutionType evolution) {
        GameSession session = GameSession.running(
                targetingRules(),
                400L + evolution.ordinal(),
                characterFor(evolution),
                new GameViewport(640, 360));
        evolve(session, evolution);
        session.spawnEnemy(0.0, 210.0, 1_000.0);

        session.tick(0.01);

        assertThat(session.state().projectiles())
                .noneMatch(projectile -> evolution.id().equals(projectile.kindId()));
        assertThat(session.state().lightningStrikes())
                .noneMatch(strike -> evolution.id().equals(strike.kindId()));
        assertThat(session.state().soundEvents())
                .extracting(SoundEvent::type)
                .doesNotContain(SoundCue.forEvolution(evolution));
    }

    @Test
    void anEnemyCirclePartlyOverlappingTheViewportIsVisible() {
        GameSession session = GameSession.running(
                targetingRules(),
                500L,
                CharacterType.DOKKAEBI_HUNTER,
                new GameViewport(640, 360));
        session.spawnEnemy(336.0, 0.0, 1_000.0);

        session.tick(0.01);

        assertThat(session.state().projectiles())
                .anyMatch(projectile -> ItemType.SEAL_TALISMAN.id().equals(projectile.kindId()));
    }

    @Test
    void anAlreadyFiredProjectileCanHitAfterItsTargetLeavesTheViewport() {
        GameSession session = GameSession.running(
                targetingRules(),
                600L,
                CharacterType.DOKKAEBI_HUNTER,
                new GameViewport(640, 360));
        session.spawnEnemy(300.0, 0.0, 5.0);
        session.tick(0.01);
        assertThat(session.state().projectiles()).isNotEmpty();

        session.movePlayerTo(0.0, -5_000.0);
        assertThat(session.state().enemies()).singleElement().satisfies(enemy ->
                assertThat(enemy.y() - session.state().player().y()).isGreaterThan(180.0));
        session.tick(2.9);

        assertThat(session.state().killCount()).isEqualTo(1);
        assertThat(session.state().enemies()).isEmpty();
    }

    private static void assertAttackPointsRight(
            GameState state,
            String kindId,
            AttackMode attackMode
    ) {
        if (attackMode == AttackMode.LIGHTNING) {
            assertThat(state.lightningStrikes())
                    .filteredOn(strike -> kindId.equals(strike.kindId()))
                    .isNotEmpty()
                    .allSatisfy(strike -> {
                        assertThat(strike.x()).isEqualTo(300.0);
                        assertThat(strike.y()).isZero();
                    });
            return;
        }
        assertThat(state.projectiles())
                .filteredOn(projectile -> kindId.equals(projectile.kindId()))
                .isNotEmpty()
                .allSatisfy(projectile ->
                        assertThat(Math.abs(projectile.rotationDegrees())).isLessThan(45.0));
    }

    private static void evolve(GameSession session, EvolutionType evolution) {
        equipToLevel(session, evolution.firstMaterial(), ItemLoadout.MAX_ITEM_LEVEL);
        equipToLevel(session, evolution.secondMaterial(), ItemLoadout.MAX_ITEM_LEVEL);
        session.evolve(evolution);
    }

    private static CharacterType characterFor(ItemType item) {
        return item == ItemType.FLAME_FAN
                ? CharacterType.GALE_SHAMAN
                : CharacterType.DOKKAEBI_HUNTER;
    }

    private static CharacterType characterFor(EvolutionType evolution) {
        return evolution.firstMaterial() == ItemType.FLAME_FAN
                        || evolution.secondMaterial() == ItemType.FLAME_FAN
                ? CharacterType.GALE_SHAMAN
                : CharacterType.DOKKAEBI_HUNTER;
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

    private static GameRules targetingRules() {
        return new GameRules(
                500.0,
                18.0,
                2_000.0,
                1_000.0,
                0.0,
                1_000.0,
                17.0,
                100.0,
                10.0,
                0.65,
                100.0,
                5,
                20,
                100,
                100);
    }
}
