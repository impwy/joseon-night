package kr.vamsur.adapter.desktopapi;

import com.linecorp.armeria.server.annotation.ConsumesJson;
import com.linecorp.armeria.server.annotation.Get;
import com.linecorp.armeria.server.annotation.Post;
import com.linecorp.armeria.server.annotation.ProducesJson;
import com.linecorp.armeria.server.annotation.Put;
import com.linecorp.armeria.server.annotation.RequestObject;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import java.util.Objects;
import java.util.Set;
import kr.vamsur.application.gameplay.provided.GameRunner;
import kr.vamsur.application.gameplay.provided.GameSnapshot;
import kr.vamsur.support.stereotype.DesktopApiAdapter;

/**
 * Armeria adapter used by the desktop screen to drive the backend game session.
 */
@DesktopApiAdapter
public final class DesktopApi {

    private final GameRunner gameRunner;
    private final Validator validator;

    public DesktopApi(GameRunner gameRunner, Validator validator) {
        this.gameRunner = Objects.requireNonNull(gameRunner, "gameRunner");
        this.validator = Objects.requireNonNull(validator, "validator");
    }

    @Get("/api/v1/game/status")
    @ProducesJson
    public GameSnapshot status() {
        return gameRunner.snapshot();
    }

    @Post("/api/v1/game/start")
    @ProducesJson
    public GameSnapshot start() {
        gameRunner.startNewGame();
        return gameRunner.snapshot();
    }

    @Put("/api/v1/game/input")
    @ConsumesJson
    @ProducesJson
    public GameSnapshot input(@RequestObject GameInputRequest request) {
        GameInputRequest validRequest = validate(request);
        gameRunner.setInput(validRequest.toInputState());
        return gameRunner.snapshot();
    }

    @Post("/api/v1/game/tick")
    @ConsumesJson
    @ProducesJson
    public GameSnapshot tick(@RequestObject GameTickRequest request) {
        GameTickRequest validRequest = validate(request);
        for (int step = 0; step < validRequest.steps(); step++) {
            gameRunner.tick(validRequest.deltaSeconds());
        }
        return gameRunner.snapshot();
    }

    @Post("/api/v1/game/upgrade")
    @ConsumesJson
    @ProducesJson
    public GameSnapshot upgrade(@RequestObject GameUpgradeRequest request) {
        GameUpgradeRequest validRequest = validate(request);
        gameRunner.chooseUpgrade(validRequest.upgradeType());
        return gameRunner.snapshot();
    }

    private <T> T validate(T request) {
        T nonNullRequest = Objects.requireNonNull(request, "request");
        Set<ConstraintViolation<T>> violations = validator.validate(nonNullRequest);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
        return nonNullRequest;
    }
}
