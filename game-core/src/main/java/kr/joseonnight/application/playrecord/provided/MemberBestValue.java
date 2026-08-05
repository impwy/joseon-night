package kr.joseonnight.application.playrecord.provided;

import java.util.Objects;

/**
 * The authoritative best value for one member, ordered for ranking projection.
 */
public record MemberBestValue(Long memberId, long value) {

    public MemberBestValue {
        Objects.requireNonNull(memberId, "memberId");
        if (memberId <= 0L) {
            throw new IllegalArgumentException("memberId must be positive");
        }
        if (value < 0L) {
            throw new IllegalArgumentException("value must not be negative");
        }
    }
}
