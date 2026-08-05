package kr.joseonnight.application.playrecord;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;
import kr.joseonnight.application.member.provided.MemberProgressionFinder;
import kr.joseonnight.application.member.provided.MemberRegister;
import kr.joseonnight.application.member.provided.MemberRegistrationInfo;
import kr.joseonnight.application.member.provided.MemberView;
import kr.joseonnight.application.member.required.MemberCache;
import kr.joseonnight.application.playrecord.provided.PlayRecordInfo;
import kr.joseonnight.application.playrecord.provided.PlayRecordView;
import kr.joseonnight.application.playrecord.provided.PlayRecorder;
import kr.joseonnight.application.playrecord.required.OutboxEventRepository;
import kr.joseonnight.domain.playrecord.PlayOutcome;
import kr.joseonnight.support.test.ApplicationServiceTest;
import org.instancio.Instancio;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@ApplicationServiceTest
class PlayRecordApplicationServiceTest {

    @MockitoBean
    private MemberCache memberCache;

    @Autowired
    private MemberRegister memberRegister;

    @Autowired
    private PlayRecorder playRecorder;

    @Autowired
    private MemberProgressionFinder progressionFinder;

    @Autowired
    private OutboxEventRepository outboxRepository;

    @Test
    void providedPortsPersistOneRecordAndOutboxAndUnlockFirstVictoryRewards() {
        MemberView member = memberRegister.register(new MemberRegistrationInfo(
                "b".repeat(64),
                "달그림자"
        ));
        UUID gameSession = Instancio.create(UUID.class);
        PlayRecordInfo completedGame = new PlayRecordInfo(
                gameSession,
                member.id(),
                "dokkaebi-hunter",
                10_000L,
                25,
                7,
                PlayOutcome.VICTORY,
                300_000L,
                Map.of("seal-talisman", 5),
                true
        );

        PlayRecordView first = playRecorder.record(completedGame);
        PlayRecordView duplicate = playRecorder.record(completedGame);

        assertThat(duplicate.id()).isEqualTo(first.id());
        assertThat(outboxRepository.count()).isEqualTo(1L);
        assertThat(progressionFinder.unlockedCharacterIds(member.id()))
                .containsExactly("dokkaebi-hunter", "gale-shaman");
    }
}
