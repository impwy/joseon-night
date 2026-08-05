package kr.vamsur.application.gameplay;

import java.util.Objects;
import kr.vamsur.application.gameplay.provided.EntitySnapshot;
import kr.vamsur.application.gameplay.provided.GameSnapshot;
import kr.vamsur.application.gameplay.provided.GameRunner;
import kr.vamsur.domain.gameplay.EntityState;
import kr.vamsur.domain.gameplay.GameRules;
import kr.vamsur.domain.gameplay.GameSession;
import kr.vamsur.domain.gameplay.GameState;
import kr.vamsur.domain.gameplay.InputState;
import kr.vamsur.domain.gameplay.UpgradeType;
import kr.vamsur.support.stereotype.ValidatedApplicationService;

/**
 * Thread-safe application boundary around the mutable session model.
 */
@ValidatedApplicationService
public final class GameService implements GameRunner {

    private static final long DEFAULT_SEED = 2_026_08_05L;

    private final GameRules rules;
    private final long seed;
    private final Object sessionLock = new Object();
    private GameSession session;

    public GameService() {
        this(GameRules.standard(), DEFAULT_SEED);
    }

    public GameService(GameRules rules, long seed) {
        this.rules = Objects.requireNonNull(rules, "rules");
        this.seed = seed;
        session = GameSession.lobby(rules, seed);
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

    private static GameSnapshot toSnapshot(GameState state) {
        return new GameSnapshot(
                state.phase(),
                state.elapsedSeconds(),
                state.remainingSeconds(),
                state.level(),
                state.experience(),
                state.experienceToNextLevel(),
                state.killCount(),
                toSnapshot(state.player()),
                state.enemies().stream().map(GameService::toSnapshot).toList(),
                state.projectiles().stream().map(GameService::toSnapshot).toList(),
                state.soulFlames().stream().map(GameService::toSnapshot).toList(),
                state.upgradeChoices()
        );
    }

    private static EntitySnapshot toSnapshot(EntityState state) {
        return new EntitySnapshot(
                state.id(),
                state.x(),
                state.y(),
                state.radius(),
                state.rotationDegrees()
        );
    }
}
