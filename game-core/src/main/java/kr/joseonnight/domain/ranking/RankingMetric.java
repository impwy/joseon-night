package kr.joseonnight.domain.ranking;

import java.util.Locale;
import java.util.Objects;
import org.springframework.util.Assert;

public enum RankingMetric {
    SURVIVAL,
    KILLS;

    public static RankingMetric fromPath(String value) {
        String normalized = Objects.requireNonNull(value, "value").toUpperCase(Locale.ROOT);
        Assert.isTrue(
                SURVIVAL.name().equals(normalized) || KILLS.name().equals(normalized),
                "metric must be survival or kills"
        );
        return valueOf(normalized);
    }
}
