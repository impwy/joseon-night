package kr.joseonnight.application.gameplay;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import kr.joseonnight.application.gameplay.provided.EntitySnapshot;
import kr.joseonnight.application.gameplay.provided.GameSessionHandle;
import kr.joseonnight.application.gameplay.provided.GameSessionListener;
import kr.joseonnight.application.gameplay.provided.GameSessionManager;
import kr.joseonnight.application.gameplay.provided.GameSessionSubscription;
import kr.joseonnight.application.gameplay.provided.GameSessionUpdate;
import kr.joseonnight.application.gameplay.provided.GameSnapshot;
import kr.joseonnight.application.gameplay.provided.LightningStrikeSnapshot;
import kr.joseonnight.application.gameplay.provided.GameRunner;
import kr.joseonnight.application.member.provided.MemberProgressionFinder;
import kr.joseonnight.application.playrecord.provided.PlayRecordInfo;
import kr.joseonnight.application.playrecord.provided.PlayRecorder;
import kr.joseonnight.domain.gameplay.CharacterType;
import kr.joseonnight.domain.gameplay.EntityState;
import kr.joseonnight.domain.gameplay.GamePhase;
import kr.joseonnight.domain.gameplay.GameRules;
import kr.joseonnight.domain.gameplay.GameSession;
import kr.joseonnight.domain.gameplay.GameState;
import kr.joseonnight.domain.gameplay.InputState;
import kr.joseonnight.domain.gameplay.UpgradeType;
import kr.joseonnight.domain.playrecord.PlayOutcome;
import kr.joseonnight.support.stereotype.ValidatedApplicationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;

/**
 * Thread-safe application boundary around the mutable session model.
 */
@ValidatedApplicationService
public final class GameService implements GameRunner, GameSessionManager {

    private static final long DEFAULT_SEED = 2_026_08_05L;
    private static final PlayRecorder NO_OP_RECORDER = ignored -> null;
    private static final MemberProgressionFinder ALLOW_ALL_CHARACTERS = ignored -> List.of(
            CharacterType.DOKKAEBI_HUNTER.id(),
            CharacterType.GALE_SHAMAN.id()
    );
    static final double SERVER_TICK_SECONDS = 1.0 / 60.0;
    static final int SNAPSHOT_EVERY_SERVER_TICKS = 4;
    static final Duration RECONNECT_GRACE_PERIOD = Duration.ofSeconds(30);
    private static final long RESULT_RETRY_INITIAL_MILLIS = 25L;
    private static final long RESULT_RETRY_MAX_MILLIS = 1_000L;
    private static final System.Logger LOGGER = System.getLogger(GameService.class.getName());

    private final GameRules rules;
    private final long seed;
    private final Object sessionLock = new Object();
    private final Object registryLock = new Object();
    private final Map<String, ManagedGameSession> managedSessions = new ConcurrentHashMap<>();
    private final Map<String, String> currentSessionByMember = new ConcurrentHashMap<>();
    private final AtomicLong managedSessionSeeds = new AtomicLong();
    private final AtomicBoolean serverLoopStarted = new AtomicBoolean();
    private final ScheduledExecutorService serverLoop;
    private final LongSupplier nanoTime;
    private final PlayRecorder playRecorder;
    private final MemberProgressionFinder progressionFinder;
    private final Executor resultExecutor;
    private final ExecutorService ownedResultExecutor;
    private final boolean resultRecordingEnabled;
    private GameSession session;

    public GameService() {
        this(GameRules.standard(), DEFAULT_SEED);
    }

    @Autowired
    public GameService(
            ObjectProvider<PlayRecorder> playRecorderProvider,
            ObjectProvider<MemberProgressionFinder> progressionFinderProvider
    ) {
        this(
                GameRules.standard(),
                DEFAULT_SEED,
                System::nanoTime,
                Objects.requireNonNull(playRecorderProvider, "playRecorderProvider")
                        .getIfAvailable(() -> NO_OP_RECORDER),
                newResultExecutor(),
                Objects.requireNonNull(progressionFinderProvider, "progressionFinderProvider")
                        .getIfAvailable(() -> ALLOW_ALL_CHARACTERS)
        );
    }

    public GameService(GameRules rules, long seed) {
        this(rules, seed, System::nanoTime);
    }

    GameService(GameRules rules, long seed, LongSupplier nanoTime) {
        this(rules, seed, nanoTime, NO_OP_RECORDER, Runnable::run, ALLOW_ALL_CHARACTERS);
    }

    GameService(
            GameRules rules,
            long seed,
            LongSupplier nanoTime,
            PlayRecorder playRecorder,
            Executor resultExecutor
    ) {
        this(rules, seed, nanoTime, playRecorder, resultExecutor, ALLOW_ALL_CHARACTERS);
    }

    GameService(
            GameRules rules,
            long seed,
            LongSupplier nanoTime,
            PlayRecorder playRecorder,
            Executor resultExecutor,
            MemberProgressionFinder progressionFinder
    ) {
        this.rules = Objects.requireNonNull(rules, "rules");
        this.seed = seed;
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
        this.playRecorder = Objects.requireNonNull(playRecorder, "playRecorder");
        this.progressionFinder = Objects.requireNonNull(progressionFinder, "progressionFinder");
        this.resultExecutor = Objects.requireNonNull(resultExecutor, "resultExecutor");
        ownedResultExecutor = resultExecutor instanceof ExecutorService executorService
                ? executorService
                : null;
        resultRecordingEnabled = playRecorder != NO_OP_RECORDER;
        session = GameSession.lobby(rules, seed);
        serverLoop = Executors.newSingleThreadScheduledExecutor(task -> Thread.ofPlatform()
                .daemon()
                .name("authoritative-game-loop")
                .unstarted(task));
    }

    public static GameService defaultGame() {
        return new GameService();
    }

    @Override
    public void startNewGame() {
        synchronized (sessionLock) {
            session = GameSession.running(rules, seed);
        }
    }

    @Override
    public void setInput(InputState input) {
        synchronized (sessionLock) {
            session.setInput(Objects.requireNonNull(input, "input"));
        }
    }

    @Override
    public void tick(double deltaSeconds) {
        synchronized (sessionLock) {
            session.tick(deltaSeconds);
        }
    }

    @Override
    public void chooseUpgrade(UpgradeType upgradeType) {
        synchronized (sessionLock) {
            session.chooseUpgrade(Objects.requireNonNull(upgradeType, "upgradeType"));
        }
    }

    @Override
    public GameSnapshot snapshot() {
        synchronized (sessionLock) {
            return toSnapshot(session.state());
        }
    }

    @Override
    public GameSessionHandle startNewGame(String memberId, CharacterType character) {
        String owner = requireText(memberId, "memberId");
        CharacterType selectedCharacter = Objects.requireNonNull(character, "character");
        ensureCharacterUnlocked(owner, selectedCharacter);
        ManagedGameSession managed = new ManagedGameSession(
                owner,
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                GameSession.running(rules, nextManagedSeed(), selectedCharacter));
        ManagedGameSession previous = null;
        synchronized (registryLock) {
            String previousSessionId = currentSessionByMember.put(owner, managed.sessionId);
            managedSessions.put(managed.sessionId, managed);
            if (previousSessionId != null) {
                previous = managedSessions.get(previousSessionId);
            }
        }
        if (previous != null && previous != managed) {
            process(previous.abandon(nanoTime.getAsLong()));
            retire(previous);
        }
        return managed.handle();
    }

    @Override
    public GameSessionHandle reconnect(String memberId, String sessionId) {
        ManagedGameSession managed = requireSession(memberId, sessionId);
        synchronized (managed.monitor) {
            managed.expireIfNeeded(nanoTime.getAsLong());
            managed.connectionId = UUID.randomUUID().toString();
            managed.connected = true;
            managed.disconnectedAtNanos = -1L;
            return managed.handleLocked();
        }
    }

    @Override
    public void disconnect(String memberId, String sessionId, String connectionId) {
        String owner = requireText(memberId, "memberId");
        ManagedGameSession managed = managedSessions.get(requireText(sessionId, "sessionId"));
        if (managed == null || !managed.memberId.equals(owner)) {
            return;
        }
        synchronized (managed.monitor) {
            if (!managed.connectionId.equals(requireText(connectionId, "connectionId"))) {
                return;
            }
            managed.connected = false;
            managed.disconnectedAtNanos = nanoTime.getAsLong();
        }
    }

    @Override
    public boolean setInput(
            String memberId,
            String sessionId,
            String connectionId,
            long commandSequence,
            InputState input
    ) {
        if (commandSequence < 0L) {
            throw new IllegalArgumentException("commandSequence must be non-negative");
        }
        ManagedGameSession managed = requireSession(memberId, sessionId);
        synchronized (managed.monitor) {
            ensureActiveConnectionLocked(managed, connectionId);
            if (commandSequence <= managed.latestCommandSequence) {
                return false;
            }
            managed.session.setInput(Objects.requireNonNull(input, "input"));
            managed.latestCommandSequence = commandSequence;
            return true;
        }
    }

    @Override
    public void chooseLevelUp(
            String memberId,
            String sessionId,
            String connectionId,
            String optionId
    ) {
        ManagedGameSession managed = requireSession(memberId, sessionId);
        synchronized (managed.monitor) {
            ensureActiveConnectionLocked(managed, connectionId);
            managed.session.chooseLevelUp(requireText(optionId, "optionId"));
        }
    }

    @Override
    public void chooseChestReward(
            String memberId,
            String sessionId,
            String connectionId,
            String optionId
    ) {
        ManagedGameSession managed = requireSession(memberId, sessionId);
        synchronized (managed.monitor) {
            ensureActiveConnectionLocked(managed, connectionId);
            managed.session.chooseChestReward(requireText(optionId, "optionId"));
        }
    }

    @Override
    public GameSnapshot snapshot(String memberId, String sessionId) {
        ManagedGameSession managed = requireSession(memberId, sessionId);
        synchronized (managed.monitor) {
            managed.expireIfNeeded(nanoTime.getAsLong());
            return toSnapshot(managed.session.state());
        }
    }

    @Override
    public GameSessionSubscription subscribe(
            String memberId,
            String sessionId,
            String connectionId,
            GameSessionListener listener
    ) {
        ManagedGameSession managed = requireSession(memberId, sessionId);
        GameSessionListener nonNullListener = Objects.requireNonNull(listener, "listener");
        synchronized (managed.monitor) {
            ensureActiveConnectionLocked(managed, connectionId);
            managed.listeners.add(nonNullListener);
        }
        AtomicBoolean subscribed = new AtomicBoolean(true);
        return () -> {
            if (subscribed.compareAndSet(true, false)) {
                managed.listeners.remove(nonNullListener);
            }
        };
    }

    @PostConstruct
    void startServerLoop() {
        if (serverLoopStarted.compareAndSet(false, true)) {
            long periodNanos = Math.round(TimeUnit.SECONDS.toNanos(1L) * SERVER_TICK_SECONDS);
            serverLoop.scheduleAtFixedRate(
                    this::runServerFrameSafely,
                    periodNanos,
                    periodNanos,
                    TimeUnit.NANOSECONDS);
        }
    }

    @PreDestroy
    public void close() {
        serverLoopStarted.set(false);
        serverLoop.shutdownNow();
        if (ownedResultExecutor != null) {
            ownedResultExecutor.shutdown();
            try {
                if (!ownedResultExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    ownedResultExecutor.shutdownNow();
                }
            } catch (InterruptedException exception) {
                ownedResultExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    void runServerFrame() {
        runServerFrame(nanoTime.getAsLong());
    }

    void runServerFrame(long nowNanos) {
        for (ManagedGameSession managed : managedSessions.values()) {
            try {
                process(managed.advance(nowNanos));
                if (managed.canRetire(nowNanos)) {
                    retire(managed);
                }
            } catch (RuntimeException exception) {
                LOGGER.log(System.Logger.Level.ERROR,
                        "Game session {0} frame failed; continuing other sessions",
                        managed.sessionId);
            }
        }
    }

    int managedSessionCount() {
        return managedSessions.size();
    }

    private void runServerFrameSafely() {
        try {
            runServerFrame();
        } catch (RuntimeException ignored) {
            // One malformed session must not terminate the fixed-rate server loop.
        }
    }

    private ManagedGameSession requireSession(String memberId, String sessionId) {
        String owner = requireText(memberId, "memberId");
        String requestedSessionId = requireText(sessionId, "sessionId");
        ManagedGameSession managed = managedSessions.get(requestedSessionId);
        String currentSessionId = currentSessionByMember.get(owner);
        if (managed == null || !managed.memberId.equals(owner)
                || !requestedSessionId.equals(currentSessionId)) {
            throw new IllegalArgumentException("The game session does not exist");
        }
        return managed;
    }

    private static void ensureActiveConnectionLocked(
            ManagedGameSession managed,
            String connectionId
    ) {
        if (!managed.connected
                || !managed.connectionId.equals(requireText(connectionId, "connectionId"))) {
            throw new IllegalStateException("The game connection is no longer active");
        }
    }

    private long nextManagedSeed() {
        return seed + managedSessionSeeds.getAndIncrement();
    }

    private void ensureCharacterUnlocked(String memberId, CharacterType character) {
        if (progressionFinder == ALLOW_ALL_CHARACTERS) {
            return;
        }
        Long internalMemberId;
        try {
            internalMemberId = Long.valueOf(memberId);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("memberId must be an internal numeric identifier", exception);
        }
        if (!progressionFinder.unlockedCharacterIds(internalMemberId).contains(character.id())) {
            throw new IllegalArgumentException("The character is not unlocked for this member");
        }
    }

    private PlayRecordInfo createResult(String memberId, String sessionId, GameState state) {
        long durationMillis = Math.round(state.elapsedSeconds() * 1_000.0);
        long score = durationMillis + state.killCount() * 100L;
        Map<String, Object> finalBuild = Map.of(
                "items",
                state.items().stream()
                        .map(item -> Map.<String, Object>of(
                                "itemId", item.itemId(),
                                "level", item.level()
                        ))
                        .toList(),
                "evolutions",
                state.evolutions().stream().map(evolution -> evolution.id()).toList()
        );
        return new PlayRecordInfo(
                UUID.fromString(sessionId),
                Long.valueOf(memberId),
                state.character().id(),
                score,
                state.killCount(),
                state.level(),
                toPlayOutcome(state.phase()),
                durationMillis,
                finalBuild,
                state.phase() != GamePhase.ABANDONED
        );
    }

    private void scheduleResult(PendingResult pending) {
        try {
            resultExecutor.execute(() -> persistResultWithRetry(pending));
        } catch (RuntimeException exception) {
            pending.session.persistenceAttemptStopped();
            LOGGER.log(System.Logger.Level.WARNING,
                    "Could not submit game result {0} for persistence",
                    pending.result.gameSession());
        }
    }

    private void persistResultWithRetry(PendingResult pending) {
        long retryMillis = RESULT_RETRY_INITIAL_MILLIS;
        int failures = 0;
        while (!Thread.currentThread().isInterrupted()) {
            try {
                playRecorder.record(pending.result);
                pending.session.persistenceSucceeded();
                return;
            } catch (RuntimeException exception) {
                failures++;
                if (failures == 1 || (failures & (failures - 1)) == 0) {
                    LOGGER.log(System.Logger.Level.WARNING,
                            "Game result {0} persistence failed; retry attempt {1}",
                            pending.result.gameSession(),
                            failures);
                }
                if (!pauseBeforeRetry(retryMillis)) {
                    pending.session.persistenceAttemptStopped();
                    return;
                }
                retryMillis = Math.min(retryMillis * 2L, RESULT_RETRY_MAX_MILLIS);
            }
        }
        pending.session.persistenceAttemptStopped();
    }

    private static boolean pauseBeforeRetry(long delayMillis) {
        try {
            Thread.sleep(delayMillis);
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static PlayOutcome toPlayOutcome(GamePhase phase) {
        return switch (phase) {
            case VICTORY -> PlayOutcome.VICTORY;
            case DEFEAT -> PlayOutcome.DEFEAT;
            case ABANDONED -> PlayOutcome.ABANDONED;
            default -> throw new IllegalArgumentException("The game has not ended: " + phase);
        };
    }

    private static ExecutorService newResultExecutor() {
        return Executors.newCachedThreadPool(task -> Thread.ofPlatform()
                .name("game-result-recorder")
                .unstarted(task));
    }

    private void process(PendingUpdate pending) {
        if (pending == null) {
            return;
        }
        if (pending.update != null) {
            for (GameSessionListener listener : pending.listeners) {
                try {
                    listener.onUpdate(pending.update);
                } catch (RuntimeException ignored) {
                    // A failed network listener must not affect simulation state or other listeners.
                }
            }
        }
        if (pending.result != null) {
            scheduleResult(pending.result);
        }
    }

    private void retire(ManagedGameSession managed) {
        managedSessions.remove(managed.sessionId, managed);
        currentSessionByMember.remove(managed.memberId, managed.sessionId);
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    private static boolean isResult(GamePhase phase) {
        return phase == GamePhase.VICTORY
                || phase == GamePhase.DEFEAT
                || phase == GamePhase.ABANDONED;
    }

    private static GameSnapshot toSnapshot(GameState state) {
        return new GameSnapshot(
                state.phase(),
                state.elapsedSeconds(),
                state.remainingSeconds(),
                state.level(),
                state.experience(),
                state.experienceToNextLevel(),
                state.killCount(),
                state.character(),
                state.barrierAvailable(),
                state.invulnerabilityRemainingSeconds(),
                toSnapshot(state.player()),
                state.enemies().stream().map(GameService::toSnapshot).toList(),
                state.projectiles().stream().map(GameService::toSnapshot).toList(),
                state.lightningStrikes().stream()
                        .map(strike -> new LightningStrikeSnapshot(
                                strike.id(),
                                strike.x(),
                                strike.y(),
                                strike.remainingSeconds(),
                                strike.kindId()))
                        .toList(),
                state.soulFlames().stream().map(GameService::toSnapshot).toList(),
                state.upgradeChoices(),
                state.items(),
                state.evolutions(),
                state.occupiedItemSlots(),
                state.chests(),
                state.chestIndicators(),
                state.levelUpOptions(),
                state.chestRewardOptions(),
                state.soundEvents()
        );
    }

    private static EntitySnapshot toSnapshot(EntityState state) {
        return new EntitySnapshot(
                state.id(),
                state.x(),
                state.y(),
                state.radius(),
                state.rotationDegrees(),
                state.kindId()
        );
    }

    private final class ManagedGameSession {
        private final Object monitor = new Object();
        private final String memberId;
        private final String sessionId;
        private final GameSession session;
        private final CopyOnWriteArrayList<GameSessionListener> listeners =
                new CopyOnWriteArrayList<>();
        private String connectionId;
        private boolean connected = true;
        private boolean resultNotificationPublished;
        private boolean resultPersistenceScheduled;
        private boolean resultPersisted;
        private long disconnectedAtNanos = -1L;
        private long terminalAtNanos = -1L;
        private long latestCommandSequence = -1L;
        private int serverTicks;

        private ManagedGameSession(
                String memberId,
                String sessionId,
                String connectionId,
                GameSession session
        ) {
            this.memberId = memberId;
            this.sessionId = sessionId;
            this.connectionId = connectionId;
            this.session = session;
        }

        private GameSessionHandle handle() {
            synchronized (monitor) {
                return handleLocked();
            }
        }

        private GameSessionHandle handleLocked() {
            return new GameSessionHandle(sessionId, connectionId, toSnapshot(session.state()));
        }

        private PendingUpdate advance(long nowNanos) {
            synchronized (monitor) {
                expireIfNeeded(nowNanos);
                if (!connected || isResult(session.state().phase())) {
                    return pendingTerminalWork(nowNanos);
                }
                session.tick(SERVER_TICK_SECONDS);
                serverTicks++;
                PendingUpdate result = pendingTerminalWork(nowNanos);
                if (result != null) {
                    return result;
                }
                if (serverTicks % SNAPSHOT_EVERY_SERVER_TICKS != 0) {
                    return null;
                }
                return pending(false);
            }
        }

        private PendingUpdate abandon(long nowNanos) {
            synchronized (monitor) {
                session.abandon();
                return pendingTerminalWork(nowNanos);
            }
        }

        private void expireIfNeeded(long nowNanos) {
            if (connected || disconnectedAtNanos < 0L || isResult(session.state().phase())) {
                return;
            }
            long disconnectedNanos = nowNanos - disconnectedAtNanos;
            if (disconnectedNanos >= RECONNECT_GRACE_PERIOD.toNanos()) {
                session.abandon();
            }
        }

        private PendingUpdate pendingTerminalWork(long nowNanos) {
            if (!isResult(session.state().phase())) {
                return null;
            }
            GameSessionUpdate update = null;
            List<GameSessionListener> updateListeners = List.of();
            if (!resultNotificationPublished) {
                resultNotificationPublished = true;
                terminalAtNanos = nowNanos;
                update = snapshotUpdate(true);
                updateListeners = new ArrayList<>(listeners);
            }
            PendingResult result = null;
            if (resultRecordingEnabled && !resultPersistenceScheduled && !resultPersisted) {
                try {
                    result = new PendingResult(
                            this,
                            createResult(memberId, sessionId, session.state())
                    );
                    resultPersistenceScheduled = true;
                } catch (RuntimeException exception) {
                    LOGGER.log(System.Logger.Level.ERROR,
                            "Could not create persistent result for game session {0}",
                            sessionId);
                }
            }
            if (update == null && result == null) {
                return null;
            }
            return new PendingUpdate(update, updateListeners, result);
        }

        private PendingUpdate pending(boolean result) {
            return new PendingUpdate(
                    snapshotUpdate(result),
                    new ArrayList<>(listeners),
                    null
            );
        }

        private GameSessionUpdate snapshotUpdate(boolean result) {
            GameSessionUpdate update = new GameSessionUpdate(
                    sessionId,
                    toSnapshot(session.state()),
                    result);
            return update;
        }

        private void persistenceSucceeded() {
            synchronized (monitor) {
                resultPersisted = true;
            }
        }

        private void persistenceAttemptStopped() {
            synchronized (monitor) {
                if (!resultPersisted) {
                    resultPersistenceScheduled = false;
                }
            }
        }

        private boolean canRetire(long nowNanos) {
            synchronized (monitor) {
                return resultNotificationPublished
                        && terminalAtNanos >= 0L
                        && nowNanos - terminalAtNanos >= RECONNECT_GRACE_PERIOD.toNanos();
            }
        }
    }

    private record PendingUpdate(
            GameSessionUpdate update,
            List<GameSessionListener> listeners,
            PendingResult result
    ) {
    }

    private record PendingResult(
            ManagedGameSession session,
            PlayRecordInfo result
    ) {
    }
}
