package kr.joseonnight.application.member.provided;

import jakarta.validation.constraints.Positive;
import java.util.List;

public interface MemberProgressionFinder {

    List<String> unlockedCharacterIds(@Positive Long memberId);
}
