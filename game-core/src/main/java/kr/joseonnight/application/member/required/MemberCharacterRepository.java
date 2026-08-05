package kr.joseonnight.application.member.required;

import java.util.List;
import kr.joseonnight.domain.member.MemberCharacter;
import kr.joseonnight.domain.member.MemberCharacterId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberCharacterRepository extends JpaRepository<MemberCharacter, MemberCharacterId> {

    List<MemberCharacter> findByMemberIdOrderByUnlockedAtAsc(Long memberId);

    boolean existsByMemberIdAndCharacterId(Long memberId, String characterId);
}
