package kr.joseonnight.application.member;

import java.time.Clock;
import java.time.Instant;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import kr.joseonnight.application.member.provided.MemberAuthentication;
import kr.joseonnight.application.member.provided.MemberAuthenticator;
import kr.joseonnight.application.member.provided.MemberView;
import kr.joseonnight.application.member.required.MemberCache;
import kr.joseonnight.application.member.required.MemberRepository;
import kr.joseonnight.application.member.required.OAuthIdentityRepository;
import kr.joseonnight.domain.member.Member;
import kr.joseonnight.domain.member.OAuthIdentity;
import kr.joseonnight.domain.member.OAuthProvider;
import kr.joseonnight.support.stereotype.ValidatedApplicationService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ValidatedApplicationService
public final class MemberAuthenticationService implements MemberAuthenticator {

    private final MemberRepository memberRepository;
    private final OAuthIdentityRepository identityRepository;
    private final MemberCache memberCache;
    private final Clock clock;

    @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "Application port is an injected collaborator")
    public MemberAuthenticationService(
            MemberRepository memberRepository,
            OAuthIdentityRepository identityRepository,
            MemberCache memberCache,
            Clock clock
    ) {
        this.memberRepository = memberRepository;
        this.identityRepository = identityRepository;
        this.memberCache = memberCache;
        this.clock = clock;
    }

    @Override
    @Transactional
    public MemberAuthentication authenticate(String providerSubjectHmac) {
        return identityRepository.findByProviderAndSubjectHmac(
                        OAuthProvider.GOOGLE,
                        providerSubjectHmac
                )
                .map(this::recordLogin)
                .orElseGet(MemberAuthentication::requiresRegistration);
    }

    private MemberAuthentication recordLogin(OAuthIdentity identity) {
        Member member = memberRepository.findById(identity.getMemberId())
                .orElseThrow(() -> new MemberNotFoundException(identity.getMemberId()));
        Instant now = Instant.now(clock);
        identity.recordLogin(now);
        member.recordLogin(now);
        MemberView view = MemberView.from(member);
        evictCacheAfterCommit(member.getId());
        return MemberAuthentication.authenticated(view);
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
            // Cache invalidation must not turn an already committed login into a failure.
        }
    }
}
