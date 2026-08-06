package kr.joseonnight.domain.gameplay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class GameSessionCharacterTest {

    private static final double TOLERANCE = 1.0e-9;

    @Test
    void hunterConsumesOneBarrierThenHasTwoSecondsOfInvulnerability() {
        GameSession session = GameSession.running(contactRules(), 11L, CharacterType.DOKKAEBI_HUNTER);

        session.tick(0.01);

        GameState protectedState = session.state();
        assertEquals(GamePhase.RUNNING, protectedState.phase());
        assertFalse(protectedState.barrierAvailable());
        assertEquals(2.0, protectedState.invulnerabilityRemainingSeconds(), TOLERANCE);

        session.tick(1.99);
        assertEquals(GamePhase.RUNNING, session.state().phase());
        session.tick(0.02);
        assertEquals(GamePhase.DEFEAT, session.state().phase());
    }

    @Test
    void galeShamanMovesTwentyFivePercentFasterAndHasNoBarrier() {
        GameSession hunter = GameSession.running(quietRules(), 12L, CharacterType.DOKKAEBI_HUNTER);
        GameSession shaman = GameSession.running(quietRules(), 12L, CharacterType.GALE_SHAMAN);
        InputState right = new InputState(false, false, false, true);
        hunter.setInput(right);
        shaman.setInput(right);

        hunter.tick(1.0);
        shaman.tick(1.0);

        assertEquals(240.0, hunter.state().player().x(), TOLERANCE);
        assertEquals(300.0, shaman.state().player().x(), TOLERANCE);
        assertFalse(shaman.state().barrierAvailable());
    }

    @Test
    void galeShamanLosesOnTheFirstEnemyContact() {
        GameSession session = GameSession.running(contactRules(), 13L, CharacterType.GALE_SHAMAN);

        session.tick(0.01);

        assertEquals(GamePhase.DEFEAT, session.state().phase());
        assertEquals(new SoundEvent(1L, SoundCue.DEFEAT), session.state().soundEvents().getFirst());
    }

    @Test
    void soundEventIdsRemainStableAcrossSnapshotsAndResetForANewRun() {
        GameSession first = GameSession.running(contactRules(), 15L);
        first.tick(0.01);

        var firstSnapshotEvents = first.state().soundEvents();
        assertEquals(new SoundEvent(1L, SoundCue.GUARD), firstSnapshotEvents.getFirst());
        assertEquals(firstSnapshotEvents, first.state().soundEvents());
        first.tick(2.0);
        assertEquals(new SoundEvent(3L, SoundCue.DEFEAT), first.state().soundEvents().getLast());

        GameSession restarted = GameSession.running(contactRules(), 15L);
        restarted.tick(0.01);
        assertEquals(new SoundEvent(1L, SoundCue.GUARD), restarted.state().soundEvents().getFirst());
    }

    @Test
    void aMultiProjectileVolleyEmitsExactlyOneAttackCue() {
        GameSession session = GameSession.running(firingRules(), 17L, CharacterType.GALE_SHAMAN);

        session.tick(0.01);

        GameState state = session.state();
        assertEquals(2, state.projectiles().size());
        assertEquals(
                List.of(new SoundEvent(1L, SoundCue.FLAME_FAN_ATTACK)),
                state.soundEvents());
    }

    @Test
    void frequentAttackCuesDoNotGrowTheSnapshotWithoutBound() {
        GameSession session = GameSession.running(firingRules(), 19L, CharacterType.GALE_SHAMAN);

        for (int tick = 0; tick < 1_000; tick++) {
            session.tick(0.01);
        }

        List<SoundEvent> events = session.state().soundEvents();
        assertEquals(64, events.size());
        assertTrue(events.getFirst().id() > 1L);
        assertTrue(events.getLast().id() > events.getFirst().id());
    }

    @Test
    void seedsThreeYellowAndTwoPurpleChestsWithinTheFixedDistanceRange() {
        GameSession session = GameSession.running(quietRules(), 14L);

        GameState state = session.state();

        assertEquals(5, state.chests().size());
        assertEquals(3, state.chests().stream()
                .filter(chest -> chest.type() == ChestType.YELLOW)
                .count());
        assertEquals(2, state.chests().stream()
                .filter(chest -> chest.type() == ChestType.PURPLE)
                .count());
        assertTrue(state.chests().stream().allMatch(chest -> {
            double distance = Math.hypot(chest.x(), chest.y());
            return distance >= 600.0 && distance <= 2_200.0;
        }));
        assertTrue(state.chests().stream().allMatch(chest -> {
            boolean visible = Math.abs(chest.x()) <= 640.0 + chest.radius()
                    && Math.abs(chest.y()) <= 360.0 + chest.radius();
            boolean hasIndicator = state.chestIndicators().stream()
                    .anyMatch(indicator -> indicator.chestId() == chest.id());
            return hasIndicator != visible;
        }));
        assertEquals(0.0, state.player().rotationDegrees(), TOLERANCE);
    }

    private static GameRules contactRules() {
        return new GameRules(
                240.0,
                18.0,
                30.0,
                0.01,
                0.0,
                10_000.0,
                17.0,
                100.0,
                1.0,
                100.0,
                100.0,
                5,
                10,
                10,
                10);
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
                10,
                10,
                10);
    }

    private static GameRules firingRules() {
        return new GameRules(
                240.0,
                18.0,
                200.0,
                0.01,
                0.0,
                10_000.0,
                17.0,
                100.0,
                1.0,
                0.12,
                100.0,
                5,
                1,
                10_000,
                10);
    }
}
