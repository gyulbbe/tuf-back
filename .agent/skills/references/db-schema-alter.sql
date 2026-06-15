CREATE SEQUENCE tournament_clan_share_send_logs_seq START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;

CREATE TABLE tournament_clan_share_send_logs (
    id NUMBER DEFAULT tournament_clan_share_send_logs_seq.NEXTVAL PRIMARY KEY,
    tournament_id NUMBER NOT NULL,
    match_id NUMBER NOT NULL,
    send_group_id VARCHAR2(36 CHAR) NOT NULL,
    player1 VARCHAR2(255 CHAR) NOT NULL,
    player2 VARCHAR2(255 CHAR) NOT NULL,
    winner VARCHAR2(255 CHAR) NOT NULL,
    loser VARCHAR2(255 CHAR) NOT NULL,
    map_name VARCHAR2(255 CHAR) NOT NULL,
    match_type VARCHAR2(50 CHAR) NOT NULL,
    match_name VARCHAR2(255 CHAR) NOT NULL,
    played_date VARCHAR2(20 CHAR) NOT NULL,
    elo_status VARCHAR2(20 CHAR) NOT NULL,
    elo_message VARCHAR2(500 CHAR),
    sheet_status VARCHAR2(20 CHAR) NOT NULL,
    sheet_message VARCHAR2(500 CHAR),
    requested_by_user_id NUMBER NOT NULL,
    reg_date TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT fk_tournament_clan_share_log_tournament
        FOREIGN KEY (tournament_id) REFERENCES tournaments(id),
    CONSTRAINT fk_tournament_clan_share_log_match
        FOREIGN KEY (match_id) REFERENCES tournament_matches(id),
    CONSTRAINT fk_tournament_clan_share_log_user
        FOREIGN KEY (requested_by_user_id) REFERENCES users(id),
    CONSTRAINT chk_tournament_clan_share_log_elo
        CHECK (elo_status IN ('SUCCESS', 'FAILED')),
    CONSTRAINT chk_tournament_clan_share_log_sheet
        CHECK (sheet_status IN ('SUCCESS', 'FAILED'))
);

CREATE INDEX idx_tournament_clan_share_logs_tournament ON tournament_clan_share_send_logs(tournament_id);
CREATE INDEX idx_tournament_clan_share_logs_match ON tournament_clan_share_send_logs(match_id);
CREATE INDEX idx_tournament_clan_share_logs_group ON tournament_clan_share_send_logs(send_group_id);
CREATE INDEX idx_tournament_clan_share_logs_user ON tournament_clan_share_send_logs(requested_by_user_id);
