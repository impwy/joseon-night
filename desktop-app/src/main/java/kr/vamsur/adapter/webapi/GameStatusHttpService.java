package kr.vamsur.adapter.webapi;

import com.linecorp.armeria.common.HttpRequest;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.HttpStatus;
import com.linecorp.armeria.common.MediaType;
import com.linecorp.armeria.server.AbstractHttpService;
import com.linecorp.armeria.server.ServiceRequestContext;
import java.util.Locale;
import java.util.Objects;
import kr.vamsur.application.gameplay.provided.GameSnapshot;

public final class GameStatusHttpService extends AbstractHttpService {
    private final GameStatusPublisher statusPublisher;

    public GameStatusHttpService(GameStatusPublisher statusPublisher) {
        this.statusPublisher = Objects.requireNonNull(statusPublisher, "statusPublisher");
    }

    @Override
    protected HttpResponse doGet(ServiceRequestContext context, HttpRequest request) {
        GameSnapshot snapshot = statusPublisher.latest();
        String body = String.format(
                Locale.ROOT,
                "{\"phase\":\"%s\",\"elapsedSeconds\":%.3f,\"remainingSeconds\":%.3f,"
                        + "\"level\":%d,\"experience\":%d,\"experienceToNextLevel\":%d,"
                        + "\"enemyCount\":%d,\"killCount\":%d}",
                snapshot.phase().name(),
                snapshot.elapsedSeconds(),
                snapshot.remainingSeconds(),
                snapshot.level(),
                snapshot.experience(),
                snapshot.experienceToNextLevel(),
                snapshot.enemies().size(),
                snapshot.killCount());
        return HttpResponse.of(HttpStatus.OK, MediaType.JSON_UTF_8, body);
    }
}
