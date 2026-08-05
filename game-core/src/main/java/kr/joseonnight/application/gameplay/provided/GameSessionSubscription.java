package kr.joseonnight.application.gameplay.provided;

/**
 * Removes a game-state listener without checked-exception handling.
 */
@FunctionalInterface
public interface GameSessionSubscription extends AutoCloseable {

    @Override
    void close();
}
