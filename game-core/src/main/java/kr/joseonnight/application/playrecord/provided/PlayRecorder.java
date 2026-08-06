package kr.joseonnight.application.playrecord.provided;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public interface PlayRecorder {

    PlayRecordView record(@Valid @NotNull PlayRecordInfo playRecordInfo);
}
