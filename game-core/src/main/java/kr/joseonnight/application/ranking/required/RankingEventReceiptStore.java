package kr.joseonnight.application.ranking.required;

public interface RankingEventReceiptStore {

    boolean isProcessed(String eventId);

    void markProcessed(String eventId);
}
