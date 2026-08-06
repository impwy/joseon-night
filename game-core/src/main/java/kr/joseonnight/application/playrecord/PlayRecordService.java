package kr.joseonnight.application.playrecord;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToLongFunction;
import kr.joseonnight.application.member.provided.MemberProgressionManager;
import kr.joseonnight.application.playrecord.provided.MemberBestValue;
import kr.joseonnight.application.playrecord.provided.PlayRecordFinder;
import kr.joseonnight.application.playrecord.provided.PlayRecordInfo;
import kr.joseonnight.application.playrecord.provided.PlayRecordRankingSource;
import kr.joseonnight.application.playrecord.provided.PlayRecordView;
import kr.joseonnight.application.playrecord.provided.PlayRecorder;
import kr.joseonnight.application.playrecord.required.PlayRecordEventPublisher;
import kr.joseonnight.application.playrecord.required.PlayRecordRepository;
import kr.joseonnight.domain.playrecord.PlayRecord;
import kr.joseonnight.domain.playrecord.PlayOutcome;
import kr.joseonnight.support.stereotype.ValidatedApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@ValidatedApplicationService
@RequiredArgsConstructor
public final class PlayRecordService implements PlayRecorder, PlayRecordFinder, PlayRecordRankingSource {

    private final PlayRecordRepository playRecordRepository;
    private final PlayRecordEventPublisher eventPublisher;
    private final MemberProgressionManager progressionManager;
    private final Clock clock;

    @Override
    @Transactional
    public PlayRecordView record(PlayRecordInfo info) {
        PlayRecordView existing = playRecordRepository.findByGameSession(info.gameSession())
                .map(PlayRecordView::from)
                .orElse(null);
        if (existing != null) {
            return existing;
        }
        PlayRecord saved = playRecordRepository.save(PlayRecord.record(
                info.gameSession(),
                info.memberId(),
                info.characterId(),
                info.score(),
                info.killCount(),
                info.level(),
                info.outcome(),
                info.durationMillis(),
                info.finalBuild(),
                Instant.now(clock),
                info.rankingEligible()
        ));
        PlayRecordView view = PlayRecordView.from(saved);
        eventPublisher.publish(view);
        if (view.outcome() == PlayOutcome.DEFEAT) {
            progressionManager.unlockFirstDefeatRewards(view.memberId());
        }
        return view;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PlayRecordView> recent(Long memberId, int limit) {
        return playRecordRepository
                .findRecentByMemberId(memberId, limit)
                .stream()
                .map(PlayRecordView::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<MemberBestValue> bestSurvivalByMember() {
        return bestByMember(
                playRecordRepository.findByRankingEligibleTrueOrderByDurationMillisDescEndedAtAsc(),
                PlayRecord::getDurationMillis
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<MemberBestValue> bestKillsByMember() {
        return bestByMember(
                playRecordRepository.findByRankingEligibleTrueOrderByKillCountDescEndedAtAsc(),
                PlayRecord::getKillCount
        );
    }

    private static List<MemberBestValue> bestByMember(
            List<PlayRecord> records,
            ToLongFunction<PlayRecord> valueExtractor
    ) {
        Map<Long, Long> bestByMember = new LinkedHashMap<>();
        for (PlayRecord record : records) {
            bestByMember.putIfAbsent(record.getMemberId(), valueExtractor.applyAsLong(record));
        }
        return bestByMember.entrySet().stream()
                .map(entry -> new MemberBestValue(entry.getKey(), entry.getValue()))
                .toList();
    }
}
