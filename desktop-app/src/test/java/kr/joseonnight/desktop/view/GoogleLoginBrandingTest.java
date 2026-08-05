package kr.joseonnight.desktop.view;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URL;
import org.junit.jupiter.api.Test;

class GoogleLoginBrandingTest {
    @Test
    void googleLoginButtonUsesTheExpectedTextAndBundledOfficialIcon() {
        URL icon = DesktopRootView.class.getResource(DesktopRootView.GOOGLE_LOGIN_ICON_RESOURCE);

        assertThat(DesktopRootView.GOOGLE_LOGIN_BUTTON_TEXT).isEqualTo("Google Login");
        assertThat(icon).isNotNull();
    }
}
