CREATE SEQUENCE entry_submission_sessions_seq START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;

CREATE TABLE entry_submission_sessions (
    id NUMBER DEFAULT entry_submission_sessions_seq.NEXTVAL PRIMARY KEY,
    title VARCHAR2(200 CHAR) NOT NULL,
    owner_user_id NUMBER NOT NULL,
    status VARCHAR2(20 CHAR) DEFAULT 'SUBMITTING' NOT NULL,
    set_count NUMBER(6) NOT NULL,
    completed_at TIMESTAMP,
    reg_date TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL,
    update_date TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT fk_entry_submission_sessions_owner
        FOREIGN KEY (owner_user_id) REFERENCES users(id),
    CONSTRAINT chk_entry_submission_sessions_status
        CHECK (status IN ('SUBMITTING', 'COMPLETED')),
    CONSTRAINT chk_entry_submission_sessions_sets
        CHECK (set_count > 0)
);

CREATE SEQUENCE entry_submission_teams_seq START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;

CREATE TABLE entry_submission_teams (
    id NUMBER DEFAULT entry_submission_teams_seq.NEXTVAL PRIMARY KEY,
    entry_submission_session_id NUMBER NOT NULL,
    team_name VARCHAR2(100 CHAR) NOT NULL,
    display_order NUMBER(1) NOT NULL,
    captain_user_id NUMBER NOT NULL,
    submitted_at TIMESTAMP,
    CONSTRAINT fk_entry_submission_teams_session
        FOREIGN KEY (entry_submission_session_id) REFERENCES entry_submission_sessions(id),
    CONSTRAINT fk_entry_submission_teams_captain
        FOREIGN KEY (captain_user_id) REFERENCES users(id),
    CONSTRAINT uq_entry_submission_teams_session_order
        UNIQUE (entry_submission_session_id, display_order),
    CONSTRAINT uq_entry_submission_teams_session_captain
        UNIQUE (entry_submission_session_id, captain_user_id),
    CONSTRAINT uq_entry_submission_teams_id_session
        UNIQUE (id, entry_submission_session_id),
    CONSTRAINT chk_entry_submission_teams_order
        CHECK (display_order IN (1, 2))
);

CREATE SEQUENCE entry_submission_players_seq START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;

CREATE TABLE entry_submission_players (
    id NUMBER DEFAULT entry_submission_players_seq.NEXTVAL PRIMARY KEY,
    entry_submission_session_id NUMBER NOT NULL,
    entry_submission_team_id NUMBER NOT NULL,
    player_name VARCHAR2(100 CHAR) NOT NULL,
    display_order NUMBER(6) NOT NULL,
    captain_yn CHAR(1) DEFAULT 'N' NOT NULL,
    CONSTRAINT fk_entry_submission_players_session
        FOREIGN KEY (entry_submission_session_id) REFERENCES entry_submission_sessions(id),
    CONSTRAINT fk_entry_submission_players_team
        FOREIGN KEY (entry_submission_team_id, entry_submission_session_id)
        REFERENCES entry_submission_teams(id, entry_submission_session_id),
    CONSTRAINT uq_entry_submission_players_team_name
        UNIQUE (entry_submission_team_id, player_name),
    CONSTRAINT uq_entry_submission_players_team_order
        UNIQUE (entry_submission_team_id, display_order),
    CONSTRAINT uq_entry_submission_players_id_session
        UNIQUE (id, entry_submission_session_id),
    CONSTRAINT chk_entry_submission_players_order
        CHECK (display_order > 0),
    CONSTRAINT chk_entry_submission_players_captain
        CHECK (captain_yn IN ('Y', 'N'))
);

CREATE TABLE entry_submission_entries (
    entry_submission_session_id NUMBER NOT NULL,
    entry_submission_team_id NUMBER NOT NULL,
    set_no NUMBER(6) NOT NULL,
    entry_submission_player_id NUMBER NOT NULL,
    submitted_by_user_id NUMBER NOT NULL,
    submitted_at TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_entry_submission_entries
        PRIMARY KEY (entry_submission_session_id, entry_submission_team_id, set_no),
    CONSTRAINT fk_entry_submission_entries_session
        FOREIGN KEY (entry_submission_session_id) REFERENCES entry_submission_sessions(id),
    CONSTRAINT fk_entry_submission_entries_team
        FOREIGN KEY (entry_submission_team_id, entry_submission_session_id)
        REFERENCES entry_submission_teams(id, entry_submission_session_id),
    CONSTRAINT fk_entry_submission_entries_player
        FOREIGN KEY (entry_submission_player_id, entry_submission_session_id)
        REFERENCES entry_submission_players(id, entry_submission_session_id),
    CONSTRAINT fk_entry_submission_entries_submitter
        FOREIGN KEY (submitted_by_user_id) REFERENCES users(id),
    CONSTRAINT chk_entry_submission_entries_set_no
        CHECK (set_no > 0)
);

CREATE INDEX idx_entry_submission_sessions_status ON entry_submission_sessions(status);
CREATE INDEX idx_entry_submission_sessions_owner ON entry_submission_sessions(owner_user_id);

CREATE INDEX idx_entry_submission_teams_session ON entry_submission_teams(entry_submission_session_id);
CREATE INDEX idx_entry_submission_teams_captain ON entry_submission_teams(captain_user_id);

CREATE INDEX idx_entry_submission_players_session ON entry_submission_players(entry_submission_session_id);
CREATE INDEX idx_entry_submission_players_team ON entry_submission_players(entry_submission_team_id);

CREATE INDEX idx_entry_submission_entries_session ON entry_submission_entries(entry_submission_session_id);
CREATE INDEX idx_entry_submission_entries_team ON entry_submission_entries(entry_submission_team_id);
CREATE INDEX idx_entry_submission_entries_player ON entry_submission_entries(entry_submission_player_id);
