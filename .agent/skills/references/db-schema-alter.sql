DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*)
    INTO v_count
    FROM user_constraints
    WHERE constraint_name = 'CHK_RPS_DRAFT_SESSIONS_STATUS';

    IF v_count > 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE rps_draft_sessions DROP CONSTRAINT chk_rps_draft_sessions_status';
    END IF;
END;
/

UPDATE rps_draft_sessions
SET started_at = SYSTIMESTAMP
WHERE status = 'READY'
  AND started_at IS NULL;

UPDATE rps_draft_sessions
SET status = 'RPS_PENDING'
WHERE status = 'READY';

ALTER TABLE rps_draft_sessions
    MODIFY (status DEFAULT 'RPS_PENDING');

ALTER TABLE rps_draft_sessions
    ADD CONSTRAINT chk_rps_draft_sessions_status
        CHECK (status IN ('RPS_PENDING', 'PICKING', 'FINISHED'));

COMMIT;

DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*)
    INTO v_count
    FROM user_tables
    WHERE table_name = 'AI_CHAT_SETTINGS';

    IF v_count = 0 THEN
        EXECUTE IMMEDIATE q'[
            CREATE TABLE ai_chat_settings (
                setting_key VARCHAR2(50 CHAR) PRIMARY KEY,
                routing_mode VARCHAR2(30 CHAR) DEFAULT 'AUTO' NOT NULL,
                cloudflare_model VARCHAR2(255 CHAR) NOT NULL,
                ollama_model VARCHAR2(255 CHAR) NOT NULL,
                updated_by NUMBER,
                updated_at TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL,
                CONSTRAINT chk_ai_chat_settings_key CHECK (setting_key = 'default'),
                CONSTRAINT chk_ai_chat_settings_mode
                    CHECK (routing_mode IN ('AUTO', 'CLOUDFLARE_ONLY', 'OLLAMA_ONLY')),
                CONSTRAINT fk_ai_chat_settings_updated_by FOREIGN KEY (updated_by) REFERENCES users(id)
            )
        ]';
    END IF;
END;
/

DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*)
    INTO v_count
    FROM user_indexes
    WHERE index_name = 'IDX_AI_CHAT_SETTINGS_UPDATED_BY';

    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'CREATE INDEX idx_ai_chat_settings_updated_by ON ai_chat_settings(updated_by)';
    END IF;
END;
/

COMMIT;
