package kr.joseonnight.application.gameplay.provided;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import kr.joseonnight.domain.gameplay.CharacterType;
import kr.joseonnight.domain.gameplay.InputState;

/**
 * Driving port used by network adapters for server-authoritative play sessions.
 */
public interface GameSessionManager {

    GameSessionHandle startNewGame(
            @NotBlank String memberId,
            @NotNull CharacterType character,
            @Min(640) @Max(3840) int viewportWidth,
            @Min(360) @Max(2160) int viewportHeight
    );

    GameSessionHandle reconnect(
            @NotBlank String memberId,
            @NotBlank String sessionId
    );

    void disconnect(
            @NotBlank String memberId,
            @NotBlank String sessionId,
            @NotBlank String connectionId
    );

    boolean setInput(
            @NotBlank String memberId,
            @NotBlank String sessionId,
            @NotBlank String connectionId,
            @PositiveOrZero long commandSequence,
            @Valid @NotNull InputState input
    );

    void setViewport(
            @NotBlank String memberId,
            @NotBlank String sessionId,
            @NotBlank String connectionId,
            @Min(640) @Max(3840) int viewportWidth,
            @Min(360) @Max(2160) int viewportHeight
    );

    void setPaused(
            @NotBlank String memberId,
            @NotBlank String sessionId,
            @NotBlank String connectionId,
            boolean paused
    );

    void chooseLevelUp(
            @NotBlank String memberId,
            @NotBlank String sessionId,
            @NotBlank String connectionId,
            @NotBlank String optionId
    );

    void chooseChestReward(
            @NotBlank String memberId,
            @NotBlank String sessionId,
            @NotBlank String connectionId,
            @NotBlank String optionId
    );

    GameSnapshot snapshot(
            @NotBlank String memberId,
            @NotBlank String sessionId
    );

    GameSessionSubscription subscribe(
            @NotBlank String memberId,
            @NotBlank String sessionId,
            @NotBlank String connectionId,
            @NotNull GameSessionListener listener
    );
}
