package kr.joseonnight.application.member;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import kr.joseonnight.application.member.provided.MemberAuthenticator;
import kr.joseonnight.application.member.provided.MemberRegister;
import kr.joseonnight.application.member.provided.MemberRegistrationInfo;
import kr.joseonnight.application.member.provided.MemberView;
import kr.joseonnight.application.member.required.MemberCache;
import kr.joseonnight.support.test.ApplicationServiceTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.transaction.TestTransaction;

@ApplicationServiceTest
class MemberCacheTransactionTest {

    @MockitoBean
    private MemberCache memberCache;

    @Autowired
    private MemberRegister memberRegister;

    @Autowired
    private MemberAuthenticator memberAuthenticator;

    @Test
    void registrationAndLoginInvalidateOnlyAfterTheirTransactionCommits() {
        String subjectHmac = "d".repeat(64);
        MemberView member = memberRegister.register(new MemberRegistrationInfo(
                subjectHmac,
                "새벽도깨비"
        ));

        verify(memberCache, never()).put(any());
        verify(memberCache, never()).evict(any());
        TestTransaction.flagForCommit();
        TestTransaction.end();
        verify(memberCache).evict(member.id());

        clearInvocations(memberCache);
        TestTransaction.start();
        memberAuthenticator.authenticate(subjectHmac);

        verify(memberCache, never()).put(any());
        verify(memberCache, never()).evict(any());
        TestTransaction.flagForCommit();
        TestTransaction.end();
        verify(memberCache).evict(member.id());
    }
}
