package kr.joseonnight.application.playrecord.required;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import kr.joseonnight.domain.playrecord.PlayOutcome;
import kr.joseonnight.domain.playrecord.PlayRecord;
import kr.joseonnight.application.member.required.MemberRepository;
import kr.joseonnight.domain.member.Member;
import kr.joseonnight.support.test.RepositoryTest;
import org.instancio.Instancio;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@RepositoryTest
class PlayRecordRepositoryTest {

    @Autowired
    private PlayRecordRepository playRecordRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private MemberRepository memberRepository;

    @Test
    void persistsJsonBuildAndUniqueGameSession() {
        UUID gameSession = Instancio.create(UUID.class);
        Long memberId = memberRepository.save(Member.register(
                Instant.parse("2026-08-05T12:00:00Z")
        )).getId();
        PlayRecord saved = playRecordRepository.save(PlayRecord.record(
                gameSession,
                memberId,
                "dokkaebi-hunter",
                12_000L,
                31,
                8,
                PlayOutcome.VICTORY,
                300_000L,
                Map.of("seal-talisman", 5, "exorcist-sword", 5),
                Instant.parse("2026-08-05T12:05:00Z"),
                true
        ));

        entityManager.flush();
        entityManager.clear();

        PlayRecord reloaded = playRecordRepository.findByGameSession(gameSession).orElseThrow();
        assertThat(reloaded.getId()).isEqualTo(saved.getId());
        assertThat(reloaded.getFinalBuild()).containsEntry("seal-talisman", 5);
        assertThat(reloaded.isRankingEligible()).isTrue();
    }
}
