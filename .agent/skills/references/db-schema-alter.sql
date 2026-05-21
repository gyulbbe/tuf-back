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
