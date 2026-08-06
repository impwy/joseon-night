package kr.joseonnight.application.member.required;

import java.util.Optional;
import kr.joseonnight.application.member.provided.MemberView;

public interface MemberCache {

    Optional<MemberView> find(Long memberId);

    void put(MemberView member);

    void evict(Long memberId);
}
