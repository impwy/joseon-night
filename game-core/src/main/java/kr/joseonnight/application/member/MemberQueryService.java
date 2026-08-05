package kr.joseonnight.application.member;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import kr.joseonnight.application.member.provided.MemberFinder;
import kr.joseonnight.application.member.provided.MemberView;
import kr.joseonnight.application.member.required.MemberCache;
import kr.joseonnight.support.stereotype.ValidatedApplicationService;
import org.springframework.transaction.annotation.Transactional;

@ValidatedApplicationService
public final class MemberQueryService implements MemberFinder {

    private final MemberValidationService validationService;
    private final MemberCache memberCache;

    @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "Application port is an injected collaborator")
    public MemberQueryService(MemberValidationService validationService, MemberCache memberCache) {
        this.validationService = validationService;
        this.memberCache = memberCache;
    }

    @Override
    @Transactional(readOnly = true)
    public MemberView find(Long memberId) {
        return memberCache.find(memberId).orElseGet(() -> {
            MemberView member = MemberView.from(validationService.requireMember(memberId));
            memberCache.put(member);
            return member;
        });
    }
}
