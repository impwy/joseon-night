package kr.joseonnight.application.playrecord.provided;

import java.util.List;

/**
 * Provides authoritative ranking values derived from persistent play records.
 */
public interface PlayRecordRankingSource {

    List<MemberBestValue> bestSurvivalByMember();

    List<MemberBestValue> bestKillsByMember();
}
