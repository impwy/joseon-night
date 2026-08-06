package kr.joseonnight.application.playrecord.required;

import kr.joseonnight.application.playrecord.provided.PlayRecordView;

public interface PlayRecordEventPublisher {

    void publish(PlayRecordView playRecord);
}
