package kr.joseonnight.application.playrecord;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;
import kr.joseonnight.application.member.provided.MemberProgressionFinder;
import kr.joseonnight.application.member.provided.MemberRegister;
import kr.joseonnight.application.member.provided.MemberRegistrationInfo;
import kr.joseonnight.application.member.provided.MemberView;
import kr.joseonnight.application.member.required.MemberCache;
import kr.joseonnight.application.member.required.MemberCharacterRepository;
import kr.joseonnight.application.member.required.MemberItemRepository;
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
    private MemberCharacterRepository characterRepository;

    @Autowired
    private MemberItemRepository itemRepository;

    @Autowired
    private OutboxEventRepository outboxRepository;

    @Test
    void providedPortsPersistOneRecordAndOutboxAndUnlockFirstDefeatRewards() {
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
                PlayOutcome.DEFEAT,
                412_000L,
                Map.of("seal-talisman", 5),
                true
        );

        PlayRecordView first = playRecorder.record(completedGame);
        PlayRecordView duplicate = playRecorder.record(completedGame);

        assertThat(duplicate.id()).isEqualTo(first.id());
        assertThat(outboxRepository.count()).isEqualTo(1L);
        assertThat(progressionFinder.unlockedCharacterIds(member.id()))
                .containsExactly("dokkaebi-hunter", "gale-shaman");
        assertThat(itemRepository.existsByMemberIdAndItemId(member.id(), "flame-fan"))
                .isTrue();
    }

    @Test
    void repeatedDefeatsKeepCharacterAndStartingItemUnlocksIdempotent() {
        MemberView member = memberRegister.register(new MemberRegistrationInfo(
                "e".repeat(64),
                "바람달"
        ));

        playRecorder.record(defeat(member.id(), Instancio.create(UUID.class)));
        playRecorder.record(defeat(member.id(), Instancio.create(UUID.class)));

        assertThat(characterRepository.existsByMemberIdAndCharacterId(
                member.id(),
                "gale-shaman"
        )).isTrue();
        assertThat(itemRepository.existsByMemberIdAndItemId(member.id(), "flame-fan"))
                .isTrue();
        assertThat(characterRepository.findAll())
                .filteredOn(character -> member.id().equals(character.getMemberId())
                        && "gale-shaman".equals(character.getCharacterId()))
                .hasSize(1);
        assertThat(itemRepository.findAll())
                .filteredOn(item -> member.id().equals(item.getMemberId())
                        && "flame-fan".equals(item.getItemId()))
                .hasSize(1);
    }

    @Test
    void abandonedRunDoesNotUnlockCompletionRewards() {
        MemberView member = memberRegister.register(new MemberRegistrationInfo(
                "c".repeat(64),
                "달바람"
        ));
        PlayRecordInfo abandonedGame = new PlayRecordInfo(
                Instancio.create(UUID.class),
                member.id(),
                "dokkaebi-hunter",
                1_000L,
                2,
                1,
                PlayOutcome.ABANDONED,
                1_000L,
                Map.of("seal-talisman", 1),
                false
        );

        playRecorder.record(abandonedGame);

        assertThat(progressionFinder.unlockedCharacterIds(member.id()))
                .containsExactly("dokkaebi-hunter");
        assertThat(itemRepository.existsByMemberIdAndItemId(member.id(), "flame-fan"))
                .isFalse();
    }

    private static PlayRecordInfo defeat(Long memberId, UUID gameSession) {
        return new PlayRecordInfo(
                gameSession,
                memberId,
                "dokkaebi-hunter",
                10_000L,
                25,
                7,
                PlayOutcome.DEFEAT,
                412_000L,
                Map.of("seal-talisman", 5),
                true
        );
    }
}
