package kr.joseonnight.application.member.required;

import java.net.URI;

public interface DesktopAuthorizationUriFactory {

    URI create(String browserLaunchToken);
}
