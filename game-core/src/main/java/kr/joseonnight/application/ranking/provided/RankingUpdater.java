package kr.joseonnight.application.ranking.provided;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public interface RankingUpdater {

    void update(@Valid @NotNull RankingUpdate update);
}
