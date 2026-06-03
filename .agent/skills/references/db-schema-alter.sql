ALTER TABLE entry_submission_sessions
    ADD source_rps_draft_session_id NUMBER;

ALTER TABLE entry_submission_sessions
    ADD CONSTRAINT fk_entry_submission_sessions_source_rps
        FOREIGN KEY (source_rps_draft_session_id)
        REFERENCES rps_draft_sessions(id)
        ON DELETE SET NULL;

CREATE INDEX idx_entry_submission_sessions_source_rps
    ON entry_submission_sessions(source_rps_draft_session_id);

ALTER TABLE tournament_match_score_submissions
    ADD map_id NUMBER;

ALTER TABLE tournament_match_score_submissions
    ADD CONSTRAINT fk_tournament_score_sub_map
        FOREIGN KEY (map_id)
        REFERENCES maps(id);

CREATE INDEX idx_tournament_score_sub_map
    ON tournament_match_score_submissions(map_id);
