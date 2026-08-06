package kr.joseonnight.application.gameplay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.ArrayList;
import java.util.List;
import kr.joseonnight.application.gameplay.provided.GameSessionHandle;
import kr.joseonnight.application.gameplay.provided.GameSessionSubscription;
import kr.joseonnight.application.member.provided.MemberProgressionFinder;
import kr.joseonnight.application.playrecord.provided.PlayRecordInfo;
import kr.joseonnight.application.playrecord.provided.PlayRecorder;
import kr.joseonnight.domain.gameplay.CharacterType;
import kr.joseonnight.domain.gameplay.GamePhase;
import kr.joseonnight.domain.gameplay.GameRules;
import kr.joseonnight.domain.gameplay.InputState;
import kr.joseonnight.domain.playrecord.PlayOutcome;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class GameServiceServerLoopTest {

    private static final double TOLERANCE = 1.0e-9;

    @Test
    void isolatesMembersAndRejectsStaleInputCommands() {
        GameService service = new GameService(quietRules(), 21L);
        GameSessionHandle first = service.startNewGame("member-1", CharacterType.DOKKAEBI_HUNTER);
        GameSessionHandle second = service.startNewGame("member-2", CharacterType.GALE_SHAMAN);

        assertNotEquals(first.sessionId(), second.sessionId());
        assertThrows(IllegalArgumentException.class,
                () -> service.snapshot("member-2", first.sessionId()));
        assertTrue(service.setInput(
                "member-1",
                first.sessionId(),
                first.connectionId(),
                1L,
                new InputState(false, false, false, true)));
        assertFalse(service.setInput(
                "member-1",
                first.sessionId(),
                first.connectionId(),
                1L,
                new InputState(false, false, true, false)));

        for (int frame = 0; frame < 60; frame++) {
            service.runServerFrame();
        }

        assertEquals(240.0,
                service.snapshot("member-1", first.sessionId()).player().x(),
                TOLERANCE);
        assertEquals(0.0,
                service.snapshot("member-2", second.sessionId()).player().x(),
                TOLERANCE);
        service.close();
    }

    @Test
    void calculatesAtSixtyHertzAndPublishesSnapshotsAtFifteenHertz() {
        GameService service = new GameService(quietRules(), 22L);
        GameSessionHandle handle = service.startNewGame("member-1", CharacterType.DOKKAEBI_HUNTER);
        AtomicInteger updates = new AtomicInteger();
        GameSessionSubscription subscription = service.subscribe(
                "member-1",
                handle.sessionId(),
                handle.connectionId(),
                update -> updates.incrementAndGet());

        for (int frame = 0; frame < 60; frame++) {
            service.runServerFrame();
        }

        assertEquals(1.0,
                service.snapshot("member-1", handle.sessionId()).elapsedSeconds(),
                TOLERANCE);
        assertEquals(15, updates.get());
        subscription.close();
        service.close();
    }

    @Test
    void pausesWhileDisconnectedReconnectsWithinThirtySecondsAndThenAbandons() {
        AtomicLong time = new AtomicLong();
        GameService service = new GameService(quietRules(), 23L, time::get);
        GameSessionHandle original = service.startNewGame(
                "member-1",
                CharacterType.DOKKAEBI_HUNTER);
        service.runServerFrame();
        double elapsedBeforeDisconnect = service.snapshot(
                "member-1",
                original.sessionId()).elapsedSeconds();
        service.disconnect(
                "member-1",
                original.sessionId(),
                original.connectionId());

        time.set(Duration.ofSeconds(29).toNanos());
        service.runServerFrame();
        assertEquals(elapsedBeforeDisconnect,
                service.snapshot("member-1", original.sessionId()).elapsedSeconds(),
                TOLERANCE);

        GameSessionHandle reconnected = service.reconnect("member-1", original.sessionId());
        assertEquals(GamePhase.RUNNING, reconnected.snapshot().phase());
        service.disconnect(
                "member-1",
                reconnected.sessionId(),
                reconnected.connectionId());

        time.addAndGet(Duration.ofSeconds(30).toNanos());
        service.runServerFrame();

        assertEquals(GamePhase.ABANDONED,
                service.snapshot("member-1", original.sessionId()).phase());
        service.close();
    }

    @Test
    void explicitPauseSurvivesReconnectAndClearsMovementInput() {
        GameService service = new GameService(quietRules(), 230L);
        GameSessionHandle original = service.startNewGame(
                "member-1",
                CharacterType.DOKKAEBI_HUNTER,
                960,
                540);
        service.setInput(
                "member-1",
                original.sessionId(),
                original.connectionId(),
                1L,
                new InputState(false, false, false, true));
        service.setPaused(
                "member-1",
                original.sessionId(),
                original.connectionId(),
                true);

        for (int frame = 0; frame < 60; frame++) {
            service.runServerFrame();
        }

        assertTrue(service.snapshot("member-1", original.sessionId()).paused());
        assertEquals(0.0,
                service.snapshot("member-1", original.sessionId()).elapsedSeconds(),
                TOLERANCE);
        assertEquals(0.0,
                service.snapshot("member-1", original.sessionId()).player().x(),
                TOLERANCE);

        service.disconnect("member-1", original.sessionId(), original.connectionId());
        GameSessionHandle reconnected = service.reconnect("member-1", original.sessionId());
        assertTrue(reconnected.snapshot().paused());
        service.setPaused(
                "member-1",
                reconnected.sessionId(),
                reconnected.connectionId(),
                false);
        for (int frame = 0; frame < 60; frame++) {
            service.runServerFrame();
        }

        assertEquals(1.0,
                service.snapshot("member-1", original.sessionId()).elapsedSeconds(),
                TOLERANCE);
        assertEquals(0.0,
                service.snapshot("member-1", original.sessionId()).player().x(),
                TOLERANCE);
        service.close();
    }

    @Test
    void validatesViewportAtStartAndWhenItChanges() {
        GameService service = new GameService(quietRules(), 231L);

        assertThrows(IllegalArgumentException.class, () -> service.startNewGame(
                "member-1",
                CharacterType.DOKKAEBI_HUNTER,
                639,
                720));
        GameSessionHandle handle = service.startNewGame(
                "member-1",
                CharacterType.DOKKAEBI_HUNTER,
                640,
                360);
        assertThrows(IllegalArgumentException.class, () -> service.setViewport(
                "member-1",
                handle.sessionId(),
                handle.connectionId(),
                3_840,
                2_161));

        service.setViewport(
                "member-1",
                handle.sessionId(),
                handle.connectionId(),
                3_840,
                2_160);
        service.close();
    }

    @Test
    void handsADefeatResultToTheRecorderExactlyOnce() {
        List<PlayRecordInfo> recorded = new ArrayList<>();
        PlayRecorder recorder = info -> {
            recorded.add(info);
            return null;
        };
        GameService service = new GameService(
                quickDefeatRules(),
                24L,
                System::nanoTime,
                recorder,
                Runnable::run
        );
        service.startNewGame("42", CharacterType.GALE_SHAMAN);

        for (int frame = 0; frame < 20; frame++) {
            service.runServerFrame();
        }

        assertEquals(1, recorded.size());
        PlayRecordInfo result = recorded.getFirst();
        assertEquals(PlayOutcome.DEFEAT, result.outcome());
        assertTrue(result.rankingEligible());
        assertEquals(result.durationMillis() + result.killCount() * 100L, result.score());
        assertTrue(result.finalBuild().containsKey("items"));
        assertTrue(result.finalBuild().containsKey("evolutions"));
        service.close();
    }

    @Test
    void notifiesTheClientOnceAndRetriesTheSameResultUntilPersistenceSucceeds() {
        AtomicInteger attempts = new AtomicInteger();
        List<PlayRecordInfo> recorded = new ArrayList<>();
        PlayRecorder recorder = info -> {
            if (attempts.incrementAndGet() == 1) {
                throw new IllegalStateException("temporary database outage");
            }
            recorded.add(info);
            return null;
        };
        GameService service = new GameService(
                quickDefeatRules(),
                27L,
                System::nanoTime,
                recorder,
                Runnable::run
        );
        GameSessionHandle handle = service.startNewGame("45", CharacterType.GALE_SHAMAN);
        AtomicInteger resultNotifications = new AtomicInteger();
        service.subscribe(
                "45",
                handle.sessionId(),
                handle.connectionId(),
                update -> {
                    if (update.result()) {
                        resultNotifications.incrementAndGet();
                    }
                }
        );

        for (int frame = 0; frame < 20; frame++) {
            service.runServerFrame();
        }

        assertEquals(2, attempts.get());
        assertEquals(1, recorded.size());
        assertEquals(handle.sessionId(), recorded.getFirst().gameSession().toString());
        assertEquals(1, resultNotifications.get());
        service.close();
    }

    @Test
    void normalShutdownWaitsForAnInFlightResultWriteToDrain() throws Exception {
        CountDownLatch writeStarted = new CountDownLatch(1);
        CountDownLatch allowWrite = new CountDownLatch(1);
        CountDownLatch writeCompleted = new CountDownLatch(1);
        PlayRecorder recorder = info -> {
            writeStarted.countDown();
            try {
                allowWrite.await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("result write interrupted", exception);
            }
            writeCompleted.countDown();
            return null;
        };
        GameService service = new GameService(
                quickDefeatRules(),
                28L,
                System::nanoTime,
                recorder,
                Executors.newSingleThreadExecutor()
        );
        service.startNewGame("46", CharacterType.GALE_SHAMAN);
        for (int frame = 0; frame < 20; frame++) {
            service.runServerFrame();
        }
        assertTrue(writeStarted.await(1, TimeUnit.SECONDS));
        CompletableFuture<Void> closing = CompletableFuture.runAsync(service::close);

        try {
            assertThrows(TimeoutException.class, () -> closing.get(100, TimeUnit.MILLISECONDS));
        } finally {
            allowWrite.countDown();
        }

        closing.get(2, TimeUnit.SECONDS);
        assertEquals(0L, writeCompleted.getCount());
    }

    @Test
    void retiresReplacedAndExpiredTerminalSessions() {
        AtomicLong time = new AtomicLong();
        GameService service = new GameService(quickDefeatRules(), 29L, time::get);
        service.startNewGame("member-1", CharacterType.GALE_SHAMAN);

        service.startNewGame("member-1", CharacterType.GALE_SHAMAN);

        assertEquals(1, service.managedSessionCount());
        for (int frame = 0; frame < 20; frame++) {
            service.runServerFrame();
        }
        assertEquals(1, service.managedSessionCount());

        time.set(GameService.RECONNECT_GRACE_PERIOD.toNanos());
        service.runServerFrame();

        assertEquals(0, service.managedSessionCount());
        service.close();
    }

    @Test
    void oneBrokenSessionDoesNotPreventAnotherMemberFrame() {
        GameService service = new GameService(quietRules(), 30L);
        GameSessionHandle first = service.startNewGame("member-1", CharacterType.DOKKAEBI_HUNTER);
        service.startNewGame("member-2", CharacterType.DOKKAEBI_HUNTER);
        @SuppressWarnings("unchecked")
        Map<String, Object> sessions = java.util.Objects.requireNonNull(
                (Map<String, Object>) ReflectionTestUtils.getField(service, "managedSessions")
        );
        List<String> iterationOrder = new ArrayList<>(sessions.keySet());
        String brokenId = iterationOrder.getFirst();
        String healthyId = iterationOrder.get(1);
        ReflectionTestUtils.setField(sessions.get(brokenId), "session", null);
        String healthyMember = healthyId.equals(first.sessionId()) ? "member-1" : "member-2";

        service.runServerFrame();

        assertEquals(
                GameService.SERVER_TICK_SECONDS,
                service.snapshot(healthyMember, healthyId).elapsedSeconds(),
                TOLERANCE
        );
        service.close();
    }

    @Test
    void recordsAbandonedResultOnceButExcludesItFromRankings() {
        AtomicLong time = new AtomicLong();
        List<PlayRecordInfo> recorded = new ArrayList<>();
        PlayRecorder recorder = info -> {
            recorded.add(info);
            return null;
        };
        GameService service = new GameService(
                quietRules(),
                25L,
                time::get,
                recorder,
                Runnable::run
        );
        GameSessionHandle handle = service.startNewGame("43", CharacterType.DOKKAEBI_HUNTER);
        service.disconnect("43", handle.sessionId(), handle.connectionId());

        time.set(Duration.ofSeconds(30).toNanos());
        service.runServerFrame();
        service.runServerFrame();

        assertEquals(1, recorded.size());
        assertEquals(PlayOutcome.ABANDONED, recorded.getFirst().outcome());
        assertFalse(recorded.getFirst().rankingEligible());
        service.close();
    }

    @Test
    void rejectsACharacterThatTheAuthenticatedMemberHasNotUnlocked() {
        MemberProgressionFinder progression = ignored -> List.of(
                CharacterType.DOKKAEBI_HUNTER.id()
        );
        GameService service = new GameService(
                quietRules(),
                26L,
                System::nanoTime,
                ignored -> null,
                Runnable::run,
                progression
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> service.startNewGame("44", CharacterType.GALE_SHAMAN)
        );
        service.close();
    }

    @Test
    void rejectsCommandsFromTheConnectionThatWasReplacedByReconnect() {
        GameService service = new GameService(quietRules(), 31L);
        GameSessionHandle original = service.startNewGame(
                "member-1",
                CharacterType.DOKKAEBI_HUNTER
        );
        service.disconnect("member-1", original.sessionId(), original.connectionId());
        GameSessionHandle reconnected = service.reconnect("member-1", original.sessionId());

        assertThrows(IllegalStateException.class, () -> service.setInput(
                "member-1",
                original.sessionId(),
                original.connectionId(),
                1L,
                new InputState(false, false, false, true)
        ));
        assertTrue(service.setInput(
                "member-1",
                reconnected.sessionId(),
                reconnected.connectionId(),
                1L,
                new InputState(false, false, false, true)
        ));
        service.close();
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

    private static GameRules quickDefeatRules() {
        GameRules quiet = quietRules();
        return new GameRules(
                quiet.playerSpeed(),
                quiet.playerRadius(),
                30.0,
                0.01,
                quiet.enemySpeed(),
                quiet.enemyHealth(),
                quiet.enemyRadius(),
                quiet.projectileSpeed(),
                quiet.projectileDamage(),
                quiet.attackCooldownSeconds(),
                quiet.soulMagnetRadius(),
                quiet.initialExperienceToNextLevel(),
                quiet.maxEnemies(),
                quiet.maxProjectiles(),
                quiet.maxSoulFlames()
        );
    }
}
