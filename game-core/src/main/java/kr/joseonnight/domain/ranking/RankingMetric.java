package kr.joseonnight.domain.ranking;

import java.util.Locale;

public enum RankingMetric {
    SURVIVAL,
    KILLS;

    public static RankingMetric fromPath(String value) {
        try {
            return valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("metric must be survival or kills", exception);
        }
    }
}
