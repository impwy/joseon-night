package kr.joseonnight.application.member.provided;

import jakarta.validation.constraints.Positive;

public interface MemberProgressionManager {

    void initializeNewMember(@Positive Long memberId);

    void unlockFirstVictoryRewards(@Positive Long memberId);
}
