package kr.joseonnight.application.gameplay.provided;

/**
 * Receives immutable states published by the authoritative server loop.
 */
@FunctionalInterface
public interface GameSessionListener {

    void onUpdate(GameSessionUpdate update);
}
