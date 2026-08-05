package kr.joseonnight.application.gameplay.provided;

import jakarta.validation.Valid;
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
            @NotNull CharacterType character
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
