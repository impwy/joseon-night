package kr.joseonnight.application.playrecord.required;

import java.util.List;
import java.util.Optional;
import kr.joseonnight.domain.playrecord.PlayRecord;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlayRecordRepository extends JpaRepository<PlayRecord, Long> {

    List<PlayRecord> findByMemberIdOrderByEndedAtDesc(Long memberId, Pageable pageable);

    default List<PlayRecord> findRecentByMemberId(Long memberId, int limit) {
        return findByMemberIdOrderByEndedAtDesc(memberId, PageRequest.of(0, limit));
    }

    boolean existsByGameSession(java.util.UUID gameSession);

    Optional<PlayRecord> findByGameSession(java.util.UUID gameSession);

    List<PlayRecord> findByRankingEligibleTrueOrderByDurationMillisDescEndedAtAsc();

    List<PlayRecord> findByRankingEligibleTrueOrderByKillCountDescEndedAtAsc();
}
