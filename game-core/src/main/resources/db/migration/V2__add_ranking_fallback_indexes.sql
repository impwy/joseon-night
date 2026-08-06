CREATE INDEX ix_play_records_ranking_survival
    ON play_records (ranking_eligible, duration_millis DESC, ended_at ASC);

CREATE INDEX ix_play_records_ranking_kills
    ON play_records (ranking_eligible, kill_count DESC, ended_at ASC);
