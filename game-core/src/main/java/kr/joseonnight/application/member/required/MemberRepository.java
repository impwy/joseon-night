package kr.joseonnight.application.member.required;

import kr.joseonnight.domain.member.Member;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<Member, Long> {
}
