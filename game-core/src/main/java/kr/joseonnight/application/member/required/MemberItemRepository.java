package kr.joseonnight.application.member.required;

import kr.joseonnight.domain.member.MemberItem;
import kr.joseonnight.domain.member.MemberItemId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberItemRepository extends JpaRepository<MemberItem, MemberItemId> {

    boolean existsByMemberIdAndItemId(Long memberId, String itemId);
}
