package kr.joseonnight.application.playrecord.provided;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import java.util.List;

public interface PlayRecordFinder {

    List<PlayRecordView> recent(@Positive Long memberId, @Min(1) @Max(100) int limit);
}
