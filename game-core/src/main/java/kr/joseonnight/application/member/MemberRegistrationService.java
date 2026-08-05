package kr.joseonnight.application.member;

import java.time.Clock;
import java.time.Instant;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import kr.joseonnight.application.member.provided.MemberRegister;
import kr.joseonnight.application.member.provided.MemberProgressionManager;
import kr.joseonnight.application.member.provided.MemberRegistrationInfo;
import kr.joseonnight.application.member.provided.MemberView;
import kr.joseonnight.application.member.required.MemberCache;
import kr.joseonnight.application.member.required.MemberRepository;
import kr.joseonnight.application.member.required.OAuthIdentityRepository;
import kr.joseonnight.application.membersettings.provided.MemberSettingsCreator;
import kr.joseonnight.domain.member.Member;
import kr.joseonnight.support.stereotype.ValidatedApplicationService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ValidatedApplicationService
public final class MemberRegistrationService implements MemberRegister {

    private final MemberRepository memberRepository;
    private final OAuthIdentityRepository identityRepository;
    private final MemberValidationService validationService;
    private final MemberSettingsCreator settingsCreator;
    private final MemberProgressionManager progressionManager;
    private final MemberCache memberCache;
    private final Clock clock;

    @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "Application port is an injected collaborator")
    public MemberRegistrationService(
            MemberRepository memberRepository,
            OAuthIdentityRepository identityRepository,
            MemberValidationService validationService,
            MemberSettingsCreator settingsCreator,
            MemberProgressionManager progressionManager,
            MemberCache memberCache,
            Clock clock
    ) {
        this.memberRepository = memberRepository;
        this.identityRepository = identityRepository;
        this.validationService = validationService;
        this.settingsCreator = settingsCreator;
        this.progressionManager = progressionManager;
        this.memberCache = memberCache;
        this.clock = clock;
    }

    @Override
    @Transactional
    public MemberView register(MemberRegistrationInfo registrationInfo) {
        validationService.rejectRegisteredGoogleIdentity(registrationInfo.providerSubjectHmac());
        Instant now = Instant.now(clock);
        Member member = memberRepository.save(Member.register(now));
        identityRepository.save(member.connectGoogle(
                registrationInfo.providerSubjectHmac(),
                now
        ));
        settingsCreator.create(member.getId(), registrationInfo.nickname());
        progressionManager.initializeNewMember(member.getId());
        MemberView view = MemberView.from(member);
        evictCacheAfterCommit(member.getId());
        return view;
    }

    private void evictCacheAfterCommit(Long memberId) {
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            evictQuietly(memberId);
                        }
                    }
            );
            return;
        }
        evictQuietly(memberId);
    }

    private void evictQuietly(Long memberId) {
        try {
            memberCache.evict(memberId);
        } catch (RuntimeException ignored) {
            // Cache invalidation must not turn an already committed registration into a failure.
        }
    }
}
