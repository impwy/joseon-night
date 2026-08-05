package kr.joseonnight.application.ranking.required;

/**
 * Signals that the disposable leaderboard projection cannot currently be updated.
 */
public final class LeaderboardUnavailableException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public LeaderboardUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
