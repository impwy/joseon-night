package kr.joseonnight.adapter.security.googleoauth;

import java.util.Set;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;

public final class IdTokenOnlyOidcUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) {
        var idToken = userRequest.getIdToken();
        return new DefaultOidcUser(
                Set.of(new OidcUserAuthority(idToken)),
                idToken,
                IdTokenClaimNames.SUB
        );
    }
}
