package kr.joseonnight.application.membersettings.required;

import java.util.Optional;
import kr.joseonnight.domain.membersettings.MemberSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberSettingsRepository extends JpaRepository<MemberSettings, Long> {

    Optional<MemberSettings> findByMemberId(Long memberId);

    boolean existsByNickname(String nickname);
}
