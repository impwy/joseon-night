package kr.joseonnight.application.ranking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.UUID;
import kr.joseonnight.application.member.provided.MemberRegister;
import kr.joseonnight.application.member.provided.MemberRegistrationInfo;
import kr.joseonnight.application.member.provided.MemberView;
import kr.joseonnight.application.member.required.MemberCache;
import kr.joseonnight.application.playrecord.provided.PlayRecordInfo;
import kr.joseonnight.application.playrecord.provided.PlayRecorder;
import kr.joseonnight.application.ranking.provided.RankingFinder;
import kr.joseonnight.application.ranking.required.LeaderboardStore;
import kr.joseonnight.application.ranking.required.LeaderboardUnavailableException;
import kr.joseonnight.domain.playrecord.PlayOutcome;
import kr.joseonnight.domain.ranking.RankingMetric;
import kr.joseonnight.support.test.ApplicationServiceTest;
import org.instancio.Instancio;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@ApplicationServiceTest
class RankingApplicationServiceTest {

    @MockitoBean
    private MemberCache memberCache;

    @MockitoBean
    private LeaderboardStore leaderboardStore;

    @Autowired
    private MemberRegister memberRegister;

    @Autowired
    private PlayRecorder playRecorder;

    @Autowired
    private RankingFinder rankingFinder;

    @Test
    void rankingFinderReturnsPostgresqlValuesWhenRedisRebuildFails() {
        MemberView member = memberRegister.register(new MemberRegistrationInfo(
                "c".repeat(64),
                "달빛검객"
        ));
        playRecorder.record(new PlayRecordInfo(
                Instancio.create(UUID.class),
                member.id(),
                "dokkaebi-hunter",
                9_000L,
                19,
                6,
                PlayOutcome.DEFEAT,
                240_000L,
                Map.of("seal-talisman", 4),
                true
        ));
        when(leaderboardStore.top(RankingMetric.SURVIVAL, 10)).thenReturn(java.util.List.of());
        doThrow(new LeaderboardUnavailableException(
                "Redis unavailable",
                new IllegalStateException("offline")
        )).when(leaderboardStore).rebuild(any(), any());

        var ranking = rankingFinder.top(RankingMetric.SURVIVAL, 10);

        assertThat(ranking).hasSize(1);
        assertThat(ranking.getFirst().memberId()).isEqualTo(member.id());
        assertThat(ranking.getFirst().value()).isEqualTo(240_000L);
    }
}
