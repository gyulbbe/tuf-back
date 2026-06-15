# tuf-back DB 스키마 요약

이 문서는 `tuf-back` 백엔드에서 사용하는 주요 테이블 구조를 빠르게 파악하기 위한 요약본이다.

기준 파일
- 전체 생성 스크립트: `db-schema.sql`
- 기존 스키마 변경 스크립트: `db-schema-alter.sql`

## 전체 구조

- 기본 정보
  - `users`
  - `site_menu_visibility`
  - `home_schedules`
  - `home_schedule_matches`
  - `home_schedule_match_players`
  - `leagues`
  - `maps`
- 리그 및 팀
  - `proleague_teams`
  - `league_participations`
  - `series_infos`
- 경기
  - `match_infos`
  - `match_players`
  - `commentaries`
- 커뮤니티 및 로그
  - `boards`
  - `comments`
  - `traces`
- AI 및 검색
  - `ai_knowledge_documents`
  - `vectors`
  - `speeches`
- 포인트 규칙
  - `point_rules`
- 기존 드래프트
  - `draft_sessions`
  - `draft_teams`
  - `draft_candidates`
  - `draft_orders`
  - `draft_picks`
- 가위바위보 드래프트
  - `rps_draft_sessions`
  - `rps_draft_teams`
  - `rps_draft_candidates`
  - `rps_draft_picks`
- Entry submission
  - `entry_submission_sessions`
  - `entry_submission_teams`
  - `entry_submission_players`
  - `entry_submission_entries`
- 토너먼트 대진표
  - `tournaments`
  - `tournament_participants`
  - `tournament_stages`
  - `tournament_groups`
  - `tournament_group_entries`
  - `tournament_matches`
  - `tournament_match_slots`
  - `tournament_match_score_submissions`
  - `race_survival_progress_submissions`
  - `race_survival_progress_submission_matches`
  - `tournament_result_slots`
  - `tournament_routes`

## Tournament creation API notes

- `POST /tournaments` creates the full bracket graph in one request: tournament, participants, stage, groups, group entries, matches, match slots, routes, and result slots.
- Created tournaments are always stored as `LIVE`. The `publishNow` request field is ignored for backward compatibility.
- The request supports `SINGLE_ELIMINATION` and `DUAL_GROUP`. `bestOf` defaults to 3 and must be a positive odd number.
- BYE is not a participant. It is represented only as a `tournament_match_slots` row with `participant_id = null`, `placeholder_label = 'BYE'`, `is_bye = 1`, `is_winner = 0`, and `score = null`.
- A real participant vs BYE match is automatically finished and the real participant is propagated through the `WINNER` route. `LOSER` propagation is ignored for BYE auto wins.
- A tournament is changed to `FINISHED` only when a `CHAMPION` result slot is decided.
- Single elimination creation uses the submitted slots as seed/order, computes the next power-of-two bracket size, and distributes BYE slots to avoid BYE-vs-BYE matches where possible.
- Dual group creation creates five matches per group: opening 1, opening 2, winners match, losers match, and decider. Missing seats are represented as BYE slots.
- Match scores use a submit -> admin approval flow. Submitting scores creates a `tournament_match_score_submissions` row only. Match slots, match status, routes, and result slots are updated only when an admin approves a pending submission.
- `RACE_SURVIVAL` does not use match-by-match score submissions. It uses `race_survival_progress_submissions` and `race_survival_progress_submission_matches`, then applies the full official result only when an admin approves one complete progress submission.

## PK 생성 방식

- 단일 숫자 PK 테이블은 Oracle 시퀀스를 사용한다.
- 예시
  - `users_seq`
  - `leagues_seq`
  - `maps_seq`
  - `home_schedule_matches_seq`
  - `home_schedule_match_players_seq`
  - `match_infos_seq`
  - `draft_sessions_seq`
  - `draft_teams_seq`
  - `rps_draft_sessions_seq`
  - `rps_draft_teams_seq`
  - `rps_draft_candidates_seq`
  - `entry_submission_sessions_seq`
  - `entry_submission_teams_seq`
  - `entry_submission_players_seq`
  - `tournaments_seq`
  - `tournament_matches_seq`
- 복합 PK
  - `draft_candidates`: `(draft_session_id, candidate_user_id)`
  - `draft_orders`: `(draft_session_id, pick_no)`
  - `draft_picks`: `(draft_session_id, pick_no)`
  - `rps_draft_picks`: `(rps_draft_session_id, pick_no)`
  - `entry_submission_entries`: `(entry_submission_session_id, entry_submission_team_id, set_no)`
- String PK
  - `site_menu_visibility`: `menu_key`
- Identity PK
  - `home_schedules`: Oracle identity column `id`

## 1. 기본 정보 테이블

### `users`

- 서비스 사용자 기본 정보
- 주요 컬럼
  - `id`
  - `user_id`, `password`
  - `name`, `phone`, `battle_tag`, `photo`
  - `tier`, `coin`
  - `race`
  - `user_type`
  - `status`
  - `reg_date`, `update_date`
- 제약
  - `race`: `ZERG`, `TERRAN`, `PROTOSS`, `RANDOM`
  - `user_type`: `ROLE_USER`, `ROLE_MANAGER`, `ROLE_MASTER`, `ROLE_ADMIN`
  - `status`: `ACTIVE`, `BANNED`, `INACTIVE`
- 특이사항
  - `status != 'INACTIVE'` 인 경우에만 `user_id` 가 유니크하도록 함수 기반 유니크 인덱스를 둔다.

### `site_menu_visibility`

- Site-wide menu visibility settings for frontend menu rendering.
- Hiding a key only hides it from navigation; direct URL authorization is handled by existing security rules.
- Supported menu keys are managed in application code and missing rows are treated as `visible = true`.
- Main columns
  - `menu_key`
  - `visible`
  - `updated_by`
  - `updated_at`
- PK
  - `menu_key`
- FK
  - `updated_by -> users.id`
- Constraints
  - `visible IN (0, 1)`
- Notes
  - Reserved keys `admin` and `admin.menuVisibility` are not stored or returned by the API.
  - Current supported keys are `chat`, `draft.proleague`, `draft.content`, `game`, `gallery`, `admin.proleague`, `admin.personalLeague`, `admin.proleagueHistory`, `admin.draftHistory`, `admin.users`, `admin.homeSchedules`, `admin.maps`, `external.recordManager`, `external.betting`.

### `home_schedules`

- Main page schedule entries managed by admins.
- Public API `GET /home/schedules` returns upcoming group representatives for the main page.
- Public API `GET /home/schedules/{scheduleId}/redirect` redirects to `target_url`.
- Admin APIs under `/admin/home/schedules` create, update, list, and physically delete schedules.
- Main columns
  - `id`
  - `schedule_group`
  - `title`
  - `description`
  - `scheduled_at`
  - `target_url`
  - `link_type`
  - `display_priority`
  - `reg_date`, `update_date`
- PK
  - `id` uses Oracle identity generation.
- Constraints
  - `schedule_group` is required and up to 50 characters.
  - `title` is required and up to 200 characters.
  - `scheduled_at` is required.
  - `link_type IN ('DIRECT', 'REDIRECT')`.
- Indexes
  - `idx_home_schedules_main (scheduled_at, schedule_group, display_priority)`
  - `idx_home_schedules_group (schedule_group, display_priority)`
- Notes
  - Main exposure is automatic. There is no manual visibility flag.
  - Public schedule candidates must satisfy `scheduled_at >= current time`.
  - Public schedule selection keeps one row per `schedule_group`, ordered inside a group by `display_priority DESC, scheduled_at ASC, id ASC`.
  - Public representatives are returned as `display_priority DESC, scheduled_at ASC, id ASC`.
  - Admin schedule list ordering is `display_priority DESC, scheduled_at ASC, id ASC`.
  - Admin keyword search uses only `description`.
  - Delete APIs physically remove rows.
  - Schedule rows can have zero or more `home_schedule_matches` rows, and each match can have zero or more `home_schedule_match_players` rows.
  - Public and admin schedule responses include nested `matches`; notice schedules can return an empty match list.

### `home_schedule_matches`

- Normalized set/match rows attached to a home schedule.
- Used for proleague sets and personal league match previews, including 1:1 and team-play formats.
- Main columns
  - `id`
  - `schedule_id`
  - `display_order`
  - `set_label`
  - `match_format`
  - `team_a_name`, `team_b_name`
  - `map_id`
  - `note`
  - `reg_date`, `update_date`
- PK
  - `id` uses `home_schedule_matches_seq`.
- FK
  - `schedule_id -> home_schedules.id ON DELETE CASCADE`
  - `map_id -> maps.id`
- Constraints
  - `display_order > 0`
  - `match_format IN ('1V1', '2V2', '3V3', 'ACE', 'CUSTOM')`
- Indexes
  - `idx_home_schedule_matches_schedule_order (schedule_id, display_order, id)`
  - `idx_home_schedule_matches_map (map_id)`
- Notes
  - `match_format` is a display hint. The actual participant count is determined by `home_schedule_match_players`.
  - Map display names are resolved from `maps.map_name`; arbitrary `map_name` text is not stored here.

### `home_schedule_match_players`

- Participant slots for a home schedule match.
- Supports 1:1, 2:2, 3:3, ACE, and CUSTOM layouts through repeated side/slot rows.
- Main columns
  - `id`
  - `match_id`
  - `side`
  - `slot_order`
  - `user_id`
  - `player_name`
  - `player_rank`
  - `player_race`
  - `note`
  - `reg_date`, `update_date`
- PK
  - `id` uses `home_schedule_match_players_seq`.
- FK
  - `match_id -> home_schedule_matches.id ON DELETE CASCADE`
  - `user_id -> users.id`
- Constraints
  - `UNIQUE (match_id, side, slot_order)`
  - `side IN ('A', 'B')`
  - `slot_order > 0`
  - `user_id IS NOT NULL OR player_name IS NOT NULL`
  - `player_race IS NULL OR player_race IN ('ZERG', 'TERRAN', 'PROTOSS', 'RANDOM')`
- Indexes
  - `idx_home_schedule_match_players_match (match_id, side, slot_order, id)`
  - `idx_home_schedule_match_players_user (user_id)`
- Notes
  - If `user_id` is present, public display uses `users.user_id`.
  - If `user_id` is absent, public display uses `player_name`.
  - `users.name` is not used in public home schedule responses.

### `leagues`

- 리그 기본 정보
- 주요 컬럼
  - `id`
  - `league_name`
  - `season_name`
  - `description`
  - `status`
  - `league_type`
  - `start_date`, `end_date`
  - `draft_session_id`
  - `tournament_id`
  - `champion_team_id`
  - `runner_up_team_id`
  - `reg_date`, `update_date`
- 제약
  - `status`: `READY`, `LIVE`, `FINISHED`
  - `league_type`: required application-defined string. DB default and allowed-value CHECK are not used.
- FK
  - `draft_session_id -> draft_sessions.id`
  - `tournament_id -> tournaments.id`
  - `champion_team_id -> proleague_teams.id`
  - `runner_up_team_id -> proleague_teams.id`
- 관리자 API
  - `/admin/proleagues` uses only rows where `league_type = 'PROLEAGUE'`.
  - `/admin/personal-leagues` uses only rows where `league_type = 'PERSONAL'`.
  - Admin create/update UIs send a fixed hidden `leagueType` payload for their route.
  - 한 프로리그는 대표 드래프트 호환용 `draft_session_id`를 유지하고, 추가 연동 드래프트는 `draft_sessions.proleague_id`로 여러 개 연결할 수 있다.
  - `createDraft = true`이면 연결된 `draft_sessions`와 `draft_*` 하위 데이터를 한 트랜잭션으로 생성한다.
  - `createTournament = true` for personal leagues stores a linked `tournament_id` and creates the tournament graph from `league_participations`.
  - 연결된 드래프트 설정은 `draft_sessions.status = READY`이고 픽이 없을 때만 수정/삭제할 수 있다.
  - 프로리그 응답의 사용자 표시값은 `users.user_id` 기준이며 `users.name`은 사용하지 않는다.

### `maps`

- 경기 맵 정보
- 주요 컬럼
  - `id`
  - `map_name`
  - `image`
  - `reg_date`, `update_date`
- 제약
  - `map_name` 은 유니크하다.

## 2. 리그 및 팀 구조

### `proleague_teams`

- 프로리그 팀 정보
- 주요 컬럼
  - `id`
  - `team_name`
  - `league_id`
  - `team_leader_id`
  - `vice_leader_id`
  - `display_order`
  - `draft_team_id`
  - `reg_date`, `update_date`
- FK
  - `league_id -> leagues.id`
  - `team_leader_id -> users.id`
  - `vice_leader_id -> users.id`
  - `draft_team_id -> draft_teams.id`
- Notes
  - 팀장/부팀장은 이 테이블에서 관리한다.
  - 드래프트로 픽된 선수 로스터는 `proleague_team_members`에서 별도로 관리한다.

### `proleague_team_members`

- 프로리그 팀원 로스터. 팀장/부팀장은 포함하지 않고 픽된 선수 또는 수동 등록 선수만 저장한다.
- 주요 컬럼
  - `id`
  - `league_id`
  - `proleague_team_id`
  - `user_id`
  - `source`: `DRAFT`, `MANUAL`
  - `source_draft_session_id`
  - `draft_pick_no`
  - `display_order`
  - `status`: `ACTIVE`, `REMOVED`
  - `reg_date`, `update_date`
- FK
  - `league_id -> leagues.id`
  - `proleague_team_id -> proleague_teams.id`
  - `user_id -> users.id`
- 제약
  - `(league_id, user_id, status)` 유니크로 한 리그 내 ACTIVE 중복 소속을 방지한다.
  - `source_draft_session_id`는 드래프트 삭제가 공식 로스터를 막지 않도록 FK를 두지 않는 nullable trace 컬럼이다.
- Notes
  - 프로리그 연동 드래프트가 `FINISHED`가 되면 해당 `source_draft_session_id`의 `DRAFT` row만 교체한다.
  - 다른 드래프트에서 생성된 팀원 row는 유지한다.

### `league_participations`

- 유저의 리그 참가 정보
- 주요 컬럼
  - `id`
  - `league_id`
  - `user_id`
  - `race`
  - `status`
  - `reg_date`, `update_date`
- FK
  - `league_id -> leagues.id`
  - `user_id -> users.id`
- 제약
  - `race`: `ZERG`, `TERRAN`, `PROTOSS`, `RANDOM`
  - `status`: `ACTIVE`, `BANNED`

### `series_infos`

- 일정 또는 라운드 단위 상위 경기 묶음
- 주요 컬럼
  - `id`
  - `league_id`
  - `round`
  - `title`
  - `match_date`
  - `reg_date`, `update_date`
- FK
  - `league_id -> leagues.id`

## 3. 경기 구조

### `match_infos`

- 개별 경기 메타 정보
- 주요 컬럼
  - `id`
  - `league_id`
  - `series_info_id`
  - `map_id`
  - `match_type`
  - `format`
  - `set_number`
  - `reg_date`, `update_date`
- FK
  - `league_id -> leagues.id`
  - `series_info_id -> series_infos.id`
  - `map_id -> maps.id`

### `match_players`

- 경기 참가 사용자 정보
- 주요 컬럼
  - `id`
  - `user_id`
  - `player_id`, `player_name`
  - `match_info_id`
  - `proleague_team_id`
  - `race`
  - `result`
  - `remark`
  - `reg_date`, `update_date`
- FK
  - `match_info_id -> match_infos.id`
  - `user_id -> users.id`
  - `proleague_team_id -> proleague_teams.id`
- 제약
  - `race`: `ZERG`, `TERRAN`, `PROTOSS`, `RANDOM`
  - `result`: `WIN`, `LOSE`, `DRAW`

### `commentaries`

- 경기 해설 또는 요약
- 주요 컬럼
  - `id`
  - `user_id`
  - `match_info_id`
  - `type`
  - `match_summary`
  - `reg_date`, `update_date`
- FK
  - `match_info_id -> match_infos.id`
  - `user_id -> users.id`

## 4. 커뮤니티 및 로그

### `boards`

- 게시글
- 주요 컬럼
  - `id`
  - `user_id` nullable
  - `author_name`
  - `title`
  - `text`
  - `reg_date`, `update_date`
- FK
  - `user_id -> users.id`
- 특이사항
  - guest 작성 허용 때문에 `user_id` 는 nullable 이다.

### `comments`

- 게시글 댓글 및 대댓글
- 주요 컬럼
  - `id`
  - `board_id`
  - `user_id` nullable
  - `author_name`
  - `parent_id`
  - `depth`
  - `content`
  - `reg_date`, `update_date`
- FK
  - `board_id -> boards.id` with `ON DELETE CASCADE`
  - `user_id -> users.id`
  - `parent_id -> comments.id` with `ON DELETE CASCADE`
- 제약
  - `depth >= 0`

### `traces`

- 로그성 텍스트 데이터
- 주요 컬럼
  - `id`
  - `user_id`
  - `type`
  - `text`
  - `reg_date`, `update_date`
- FK
  - `user_id -> users.id`

## 5. AI 및 검색 구조

### `ai_knowledge_documents`

- Chatbot-readable knowledge documents generated from structured data.
- Main columns
  - `id`
  - `document_type`: `USER_LEAGUE_RECORD_SUMMARY`, `MATCH_RESULT_SUMMARY`, `LEAGUE_RECORD_SUMMARY`
  - `source_table`
  - `source_id`
  - `title`
  - `content`
  - `metadata`
  - `reg_date`, `update_date`
- Constraints
  - `UNIQUE (document_type, source_table, source_id)`
- Notes
  - Record documents are regenerated from `v_user_match_results` and `v_user_league_records`.
  - Vector rows reference these documents with `vectors.reference_table = 'ai_knowledge_documents'`.

### `vectors`

- 범용 임베딩 저장 테이블
- 주요 컬럼
  - `id`
  - `reference_id`
  - `reference_table`
  - `chunk_index`
  - `content`
  - `metadata`
  - `embedding_vector VECTOR(1024, FLOAT32)`
  - `reg_date`, `update_date`
- 제약
  - `reference_table`: `boards`, `match_infos`, `commentaries`, `speeches`, `ai_knowledge_documents`
  - `UNIQUE (reference_table, reference_id, chunk_index)`
- 특이사항
  - 정형 FK 대신 `reference_table + reference_id` 조합으로 원본을 가리킨다.
  - `ai_knowledge_documents` 재생성 시 같은 `reference_id` 의 기존 chunk 를 지우고 다시 임베딩한다.
  - 마이그레이션 시 기존 중복 vector 는 같은 `(reference_table, reference_id, chunk_index)` 안에서 가장 최신 `id` 만 남긴다.
  - Oracle ANN 벡터 인덱스를 사용한다.

### `speeches`

- 발화 또는 채팅 저장 테이블
- 주요 컬럼
  - `id`
  - `user_id`
  - `nickname`
  - `chat`
  - `chat_embedding_vector VECTOR(1024, FLOAT32)`
  - `reg_date`, `update_date`
- FK
  - `user_id -> users.id`

## 6. 포인트 규칙

### `point_rules`

- 경기 결과 기반 포인트 규칙
- 주요 컬럼
  - `id`
  - `rule_name`
  - `match_type`
  - `result`
  - `tier_diff`
  - `point`
  - `description`
  - `reg_date`, `update_date`

## 7. 기존 드래프트 구조

기존 드래프트는 `FIXED_ORDER` 단일 구조로 운영한다.

지원 방식
- `FIXED_ORDER`

주요 특징
- 순번 테이블 `draft_orders` 를 사용한다.
- 현재 턴의 진실 원천은 `current_pick_no + draft_orders` 이다.
- 세션별 팀 수는 2팀 이상일 수 있다.
- 제한 시간과 현재 턴 마감 시간을 가진다.
- `current_draft_team_id` 는 현재 턴 캐시 용도로 유지한다.

### `draft_sessions`

- 기존 드래프트 세션 상위 엔티티
- 주요 컬럼
  - `id`
  - `title`
  - `owner_user_id`
  - `proleague_id`
  - `status`
  - `order_mode`
  - `team_count`
  - `pick_time_seconds`
  - `current_pick_no`
  - `current_draft_team_id`
  - `deadline_at`
  - `started_at`, `ended_at`
  - `reg_date`, `update_date`
- 제약
  - `status`: `READY`, `LIVE`, `PAUSED`, `FINISHED`, `CANCELLED`
  - `team_count > 1`
  - `pick_time_seconds > 0`
  - `current_pick_no > 0`
- FK
  - `owner_user_id -> users.id`
  - `proleague_id -> leagues.id`
  - `current_draft_team_id + id -> draft_teams.id + draft_teams.draft_session_id`
- 특이사항
  - `owner_user_id` 는 세션 생성자이며 owner/admin 권한 판정 기준이다.
  - 기존 legacy 세션은 owner 백필 전까지 `owner_user_id` 가 비어 있을 수 있고, 이 경우 관리자만 제어할 수 있다.
  - `order_mode` 는 DraftOrder 자동 생성 방식이다. 현재 애플리케이션은 `BASIC`, `SNAKE` 를 지원하지만 향후 모드 확장을 위해 DB CHECK 제약은 두지 않는다.
  - `current_draft_team_id` 는 `draft_orders.pick_no = current_pick_no` 와 같은 팀을 가리키는 캐시 성격의 컬럼이다.

- 프로리그 연동 드래프트는 `draft_sessions.proleague_id`로 원본 프로리그를 가리킨다. 한 프로리그에 여러 드래프트가 연결될 수 있다.

### `draft_teams`

- 기존 드래프트 세션에 속한 팀 정보
- 주요 컬럼
  - `id`
  - `draft_session_id`
  - `team_name`
  - `display_order`
  - `picker_user_id`
  - `proleague_team_id`
  - `reg_date`, `update_date`
- FK
  - `draft_session_id -> draft_sessions.id`
  - `picker_user_id -> users.id`
  - `proleague_team_id -> proleague_teams.id`
- 제약
  - `(draft_session_id, team_name)` 유니크
  - `(draft_session_id, display_order)` 유니크
  - `(id, draft_session_id)` 유니크
  - `display_order > 0`

- Notes
  - 프로리그 연동 드래프트에서는 `proleague_team_id`가 원본 `proleague_teams.id`를 가리키며, 종료 동기화가 픽 결과를 공식 팀원 테이블에 반영할 때 사용한다.

### `draft_candidates`

- 기존 드래프트 후보 목록
- 주요 컬럼
  - `draft_session_id`
  - `candidate_user_id`
  - `candidate_name`
  - `race`
  - `status`
  - `picked_draft_team_id`
  - `picked_at`
  - `reg_date`, `update_date`
- PK
  - `(draft_session_id, candidate_user_id)`
- FK
  - `draft_session_id -> draft_sessions.id`
  - `candidate_user_id -> users.id`
  - `picked_draft_team_id + draft_session_id -> draft_teams.id + draft_teams.draft_session_id`
- 제약
  - `race`: `ZERG`, `TERRAN`, `PROTOSS`, `RANDOM`
  - `status`: `WAITING`, `PICKED`, `SKIPPED`, `EXCLUDED`

### `draft_orders`

- 기존 드래프트 순번 정보
- 주요 컬럼
  - `draft_session_id`
  - `pick_no`
  - `draft_team_id`
  - `reg_date`, `update_date`
- PK
  - `(draft_session_id, pick_no)`
- FK
  - `draft_session_id -> draft_sessions.id`
  - `draft_team_id + draft_session_id -> draft_teams.id + draft_teams.draft_session_id`
- 제약
  - `pick_no > 0`

### `draft_picks`

- 기존 드래프트 실제 픽 결과 로그
- 주요 컬럼
  - `draft_session_id`
  - `pick_no`
  - `draft_team_id`
  - `candidate_user_id`
  - `picked_by_user_id`
  - `picked_at`
  - `reg_date`, `update_date`
- PK
  - `(draft_session_id, pick_no)`
- FK
  - `draft_session_id -> draft_sessions.id`
  - `draft_team_id + draft_session_id -> draft_teams.id + draft_teams.draft_session_id`
  - `draft_session_id + candidate_user_id -> draft_candidates.draft_session_id + draft_candidates.candidate_user_id`
  - `picked_by_user_id -> users.id`
- 제약
  - `(draft_session_id, candidate_user_id)` 유니크
  - `pick_no > 0`

## 8. 가위바위보 드래프트 구조

가위바위보 드래프트는 기존 `draft_*` 와 별도 구조로 운영한다.

핵심 원칙
- `2팀 전용` 이다.
- 세션 생성 시 팀도 자동으로 2개 생성한다.
- 순번 테이블이 없다.
- 제한 시간이 없다.
- 진행 흐름은 `가위바위보 -> 승자 1픽 -> 패자 1픽 -> 다시 가위바위보` 반복이다.
- 후보 등록과 팀별 픽커 지정 방식은 기존 흐름과 유사하게 가져간다.

상태 흐름
- `RPS_PENDING`
  - 세션 생성 직후 상태이며 양 팀 픽커가 가위바위보를 제출하는 상태
  - 누가 냈는지만 보여주고, 둘 다 내기 전까지 선택값은 공개하지 않는다
- `PICKING`
  - 승패가 정해져 2픽을 처리하는 상태
  - `current_draft_team_id`: 현재 픽 차례 팀
  - `pending_draft_team_id`: 다음 픽 차례 팀
- `FINISHED`
  - 후보가 모두 소진된 상태

### `rps_draft_sessions`

- 가위바위보 드래프트 세션 상위 엔티티
- 주요 컬럼
  - `id`
  - `title`
  - `owner_user_id`
  - `status`
  - `current_pick_no`
  - `current_draft_team_id`
  - `pending_draft_team_id`
  - `team1_rps_choice`
  - `team2_rps_choice`
  - `rps_result`
  - `started_at`, `ended_at`
  - `reg_date`, `update_date`
- FK
  - `owner_user_id -> users.id`
  - `current_draft_team_id + id -> rps_draft_teams.id + rps_draft_teams.rps_draft_session_id`
  - `pending_draft_team_id + id -> rps_draft_teams.id + rps_draft_teams.rps_draft_session_id`
- 제약
  - `status`: `RPS_PENDING`, `PICKING`, `FINISHED`
  - `current_pick_no > 0`
  - `team1_rps_choice`, `team2_rps_choice`: `ROCK`, `PAPER`, `SCISSORS`
  - `rps_result`: `PENDING`, `DRAW`, `TEAM1_WIN`, `TEAM2_WIN`
- 특이사항
  - `owner_user_id` 는 세션을 등록하고 라이브 진행을 관리하는 사용자다.
  - 세션 생성 직후 `RPS_PENDING` 상태이며 별도 시작 단계는 없다.
  - `team1_*`, `team2_*` 는 `rps_draft_teams.display_order = 1, 2` 와 대응한다.
  - 누가 제출했는지는 각 choice 가 `NULL` 인지로 판단한다.
  - 두 팀 모두 제출하기 전까지는 프론트에서 선택값을 숨기고, 제출 여부만 사용한다.

### `rps_draft_teams`

- 가위바위보 드래프트 세션에 속한 팀
- 주요 컬럼
  - `id`
  - `rps_draft_session_id`
  - `team_name`
  - `display_order`
  - `picker_user_id`
  - `reg_date`, `update_date`
- FK
  - `rps_draft_session_id -> rps_draft_sessions.id`
  - `picker_user_id -> users.id`
- 제약
  - `(rps_draft_session_id, team_name)` 유니크
  - `(rps_draft_session_id, display_order)` 유니크
  - `(rps_draft_session_id, picker_user_id)` 유니크
  - `(id, rps_draft_session_id)` 유니크
  - `display_order IN (1, 2)`
- 특이사항
  - 세션 생성 시 애플리케이션이 자동으로 2개 행을 만든다.
  - 팀명은 팀장 `users.user_id` 기준으로 생성하며 공개 응답에도 로그인 아이디만 표시한다.
  - `picker_user_id` 는 가위바위보 제출과 실제 픽을 수행하는 대표 계정이다.

### `rps_draft_candidates`

- 가위바위보 드래프트 후보 목록
- 주요 컬럼
  - `id`
  - `rps_draft_session_id`
  - `candidate_name`
  - `display_order`
  - `status`
  - `picked_rps_draft_team_id`
  - `picked_at`
  - `reg_date`, `update_date`
- PK
  - `id`
- FK
  - `rps_draft_session_id -> rps_draft_sessions.id`
  - `picked_rps_draft_team_id + rps_draft_session_id -> rps_draft_teams.id + rps_draft_teams.rps_draft_session_id`
- 제약
  - `(rps_draft_session_id, candidate_name)` 유니크
  - `(rps_draft_session_id, display_order)` 유니크
  - `(id, rps_draft_session_id)` 유니크
  - `display_order > 0`
  - `status`: `WAITING`, `PICKED`, `EXCLUDED`
- 특이사항
  - 후보는 `users` 와 연결하지 않는 이름 문자열이다.
  - 후보 표시값은 `candidate_name` 이며 tier/race/user login id 를 내려주지 않는다.

### `rps_draft_picks`

- 가위바위보 드래프트 실제 픽 결과 로그
- 주요 컬럼
  - `rps_draft_session_id`
  - `pick_no`
  - `rps_draft_team_id`
  - `candidate_id`
  - `picked_by_user_id`
  - `picked_at`
  - `reg_date`, `update_date`
- PK
  - `(rps_draft_session_id, pick_no)`
- FK
  - `rps_draft_session_id -> rps_draft_sessions.id`
  - `rps_draft_team_id + rps_draft_session_id -> rps_draft_teams.id + rps_draft_teams.rps_draft_session_id`
  - `candidate_id + rps_draft_session_id -> rps_draft_candidates.id + rps_draft_candidates.rps_draft_session_id`
  - `picked_by_user_id -> users.id`
- 제약
  - `(rps_draft_session_id, candidate_id)` 유니크
  - `pick_no > 0`

## 9. 토너먼트 대진표 구조

토너먼트 대진표는 듀얼 조별전과 싱글 엘리미네이션을 같은 구조로 처리한다. 핵심 모델은 `경기(tournament_matches)`, `경기 슬롯(tournament_match_slots)`, `결과 이동 규칙(tournament_routes)`, `결과 슬롯(tournament_result_slots)` 이다.

## Entry submission

Entry submission stores two captain-led teams, team player cards, and submitted set entries.

Status flow
- `SUBMITTING`: one or both captains have not submitted entries yet.
- `COMPLETED`: both captains submitted entries and the set matchup table is final.

### `entry_submission_sessions`

- Top-level entry submission session.
- Main columns
  - `id`
  - `title`
  - `owner_user_id`
  - `source_rps_draft_session_id`
  - `status`
  - `set_count`
  - `completed_at`
  - `reg_date`, `update_date`
- FK
  - `owner_user_id -> users.id`
  - `source_rps_draft_session_id -> rps_draft_sessions.id ON DELETE SET NULL`
- Constraints
  - `status`: `SUBMITTING`, `COMPLETED`
  - `set_count > 0`
- Notes
  - Default set count is calculated by application code as `max(team1 player count, team2 player count)`.
  - A manually supplied set count overrides the default.
  - If created from a finished RPS draft, `source_rps_draft_session_id` links the entry session to that RPS draft.

### `entry_submission_teams`

- Two teams belonging to an entry submission session.
- Main columns
  - `id`
  - `entry_submission_session_id`
  - `team_name`
  - `display_order`
  - `captain_user_id`
  - `submitted_at`
- FK
  - `entry_submission_session_id -> entry_submission_sessions.id`
  - `captain_user_id -> users.id`
- Constraints
  - `(entry_submission_session_id, display_order)` unique
  - `(entry_submission_session_id, captain_user_id)` unique
  - `(id, entry_submission_session_id)` unique
  - `display_order IN (1, 2)`
- Notes
  - `team_name` is based on the captain's `users.user_id`.
  - `submitted_at IS NOT NULL` means the team entry is locked.

### `entry_submission_players`

- Player cards for each entry submission team.
- Main columns
  - `id`
  - `entry_submission_session_id`
  - `entry_submission_team_id`
  - `player_name`
  - `display_order`
  - `captain_yn`
- FK
  - `entry_submission_session_id -> entry_submission_sessions.id`
  - `entry_submission_team_id + entry_submission_session_id -> entry_submission_teams.id + entry_submission_teams.entry_submission_session_id`
- Constraints
  - `(entry_submission_team_id, player_name)` unique
  - `(entry_submission_team_id, display_order)` unique
  - `(id, entry_submission_session_id)` unique
  - `display_order > 0`
  - `captain_yn IN ('Y', 'N')`
- Notes
  - Captains are automatically inserted as the first player card for each team.
  - Additional players are name strings and are not linked to `users`.

### `entry_submission_entries`

- Submitted set entries for a team.
- Main columns
  - `entry_submission_session_id`
  - `entry_submission_team_id`
  - `set_no`
  - `entry_submission_player_id`
  - `submitted_by_user_id`
  - `submitted_at`
- PK
  - `(entry_submission_session_id, entry_submission_team_id, set_no)`
- FK
  - `entry_submission_session_id -> entry_submission_sessions.id`
  - `entry_submission_team_id + entry_submission_session_id -> entry_submission_teams.id + entry_submission_teams.entry_submission_session_id`
  - `entry_submission_player_id + entry_submission_session_id -> entry_submission_players.id + entry_submission_players.entry_submission_session_id`
  - `submitted_by_user_id -> users.id`
- Constraints
  - `set_no > 0`
- Notes
  - Application code requires one entry per set for each submitted team.
  - Player repetition is allowed only when `set_count > that team's player count`.

### `tournaments`

- 대회 최상위 정보
- 주요 컬럼
  - `id`
  - `title`
  - `owner_user_id`
  - `status`
  - `reg_date`, `update_date`
- FK
  - `owner_user_id -> users.id`
- 제약
  - `status`: `LIVE`, `FINISHED`
- 예시
  - `2026 시즌1 스타 대회`, `status = LIVE`
- 활용
  - 대회 목록, 공개 상태, 전체 진행 상태를 관리한다.
  - 개별 경기 진행 상태와 별개로 대회 전체가 진행 중이면 `LIVE` 를 사용한다.

### `tournament_participants`

- 대회 전체 참가자 목록
- 주요 컬럼
  - `id`
  - `tournament_id`
  - `user_id`
  - `participant_name`
  - `seed_no`
  - `status`
  - `reg_date`, `update_date`
- FK
  - `tournament_id -> tournaments.id`
  - `user_id -> users.id`
- 제약
  - `(tournament_id, seed_no)` 유니크
  - `status`: `READY`, `WAITING`, `DROPPED`
  - `user_id IS NOT NULL OR participant_name IS NOT NULL`
- 예시
  - 내부 회원: `user_id = 101`, `participant_name = null`
  - 외부 참가자: `user_id = null`, `participant_name = '외부초청선수'`
- 활용
  - `user_id` 가 있으면 `users` 정보를 표시한다.
  - `user_id` 가 없으면 외부 참가자로 보고 `participant_name` 을 화면에 표시한다.
  - 참가자의 조 배치는 `tournament_group_entries` 에서 관리한다.

### `tournament_stages`

- 대회 안의 진행 단계
- 주요 컬럼
  - `id`
  - `tournament_id`
  - `stage_no`
  - `stage_name`
  - `stage_type`
  - `status`
  - `display_order`
  - `reg_date`, `update_date`
- FK
  - `tournament_id -> tournaments.id`
- 제약
  - `(tournament_id, stage_no)` 유니크
  - `stage_type`: `DUAL_GROUP`, `SINGLE_ELIMINATION`, `ULTIMATE_BATTLE`, `RACE_SURVIVAL`
  - `status`: `DRAFT`, `READY`, `FINISHED`
- 예시
  - `stage_no = 1`, `stage_name = 조별 듀얼`, `stage_type = DUAL_GROUP`
  - `stage_no = 2`, `stage_name = 4강 본선`, `stage_type = SINGLE_ELIMINATION`
  - 끝장전: `stage_no = 1`, `stage_type = ULTIMATE_BATTLE`
  - 종족 최강전: `stage_no = 1`, `stage_type = RACE_SURVIVAL`
- 활용
  - 대진 방식이 바뀌거나 조별전 결과를 본선으로 넘길 때 stage 를 나눈다.
  - `default_best_of` 는 두지 않는다. 실제 경기 방식은 항상 `tournament_matches.best_of` 를 본다.

### `tournament_groups`

- 스테이지 안의 조 또는 본선 브래킷 그룹
- 주요 컬럼
  - `id`
  - `stage_id`
  - `group_code`
  - `group_name`
  - `display_order`
  - `reg_date`, `update_date`
- FK
  - `stage_id -> tournament_stages.id`
- 제약
  - `(stage_id, group_code)` 유니크
- 예시
  - 듀얼 조별전: `A`, `A조`; `B`, `B조`
  - 싱글 엘리미네이션: `MAIN`, `본선`
- 활용
  - 화면에서 A조/B조 패널 또는 본선 브래킷 하나를 나타낸다.

### `tournament_group_entries`

- 조 안의 참가자 배치 정보
- 주요 컬럼
  - `id`
  - `group_id`
  - `participant_id`
  - `group_seed_no`
  - `entry_label`
  - `reg_date`, `update_date`
- FK
  - `group_id -> tournament_groups.id`
  - `participant_id -> tournament_participants.id`
- 제약
  - `(group_id, group_seed_no)` 유니크
  - `(group_id, participant_id)` 유니크
- 예시
  - `A조 group_seed_no = 1`, `entry_label = A1`
  - `A조 group_seed_no = 2`, `entry_label = A2`
- 활용
  - 관리자 조 편성과 첫 경기 슬롯 생성을 위한 입력값이다.
  - 같은 stage 안의 중복 배정은 서비스에서 추가 검증한다.

### `tournament_matches`

- 화면의 경기 카드 하나
- 주요 컬럼
  - `id`
  - `stage_id`
  - `group_id`
  - `match_key`
  - `match_role`
  - `round_no`, `match_no`
  - `display_name`
  - `best_of`
  - `status`
  - `winner_participant_id`
  - `map_id`
  - `scheduled_at`
  - `layout_col`, `layout_row`
  - `display_order`
  - `reg_date`, `update_date`
- FK
  - `stage_id -> tournament_stages.id`
  - `group_id -> tournament_groups.id`
  - `winner_participant_id -> tournament_participants.id`
  - `map_id -> maps.id`
- 제약
  - `(group_id, match_key)` 유니크
  - `match_role`: `OPENING`, `WINNERS`, `LOSERS`, `DECIDER`, `ROUND`, `FINAL`
  - `status`: `PENDING`, `READY`, `FINISHED`, `CANCELLED`
  - `best_of > 0 AND MOD(best_of, 2) = 1`
- 예시
  - 듀얼 조별전: `A1`, `A2`, `AW`, `AL`, `AF`
  - 싱글 엘리미네이션: `SF1`, `SF2`, `FINAL`
  - 끝장전: `ULTIMATE`, `best_of` 는 총 판수
  - 종족 최강전: `M1`, `M2` ... 승자연전 경기
- 활용
  - `best_of` 는 홀수 총 판수이므로 `NUMBER(2)`처럼 작은 자릿수로 제한하지 않는다.
  - `PENDING`: 참가 슬롯이 아직 다 차지 않은 경기
  - `READY`: 결과 입력 가능한 경기
  - `FINISHED`: 결과 확정과 route 전파가 끝난 경기
  - `CANCELLED`: 취소된 경기
  - 개별 경기는 `LIVE` 상태를 두지 않고 `READY` 에서 결과 입력 후 바로 `FINISHED` 로 확정한다.
  - `map_id` 는 선택 사항이며 관리자 진행 화면에서 경기별 맵을 지정할 때 사용한다.

### `tournament_match_slots`

- 경기 카드 안의 1번/2번 선수 자리
- 주요 컬럼
  - `id`
  - `match_id`
  - `slot_no`
  - `participant_id`
  - `source_match_id`
  - `source_outcome`
  - `placeholder_label`
  - `score`
  - `is_winner`
  - `is_bye`
  - `reg_date`, `update_date`
- FK
  - `match_id -> tournament_matches.id`
  - `participant_id -> tournament_participants.id`
  - `source_match_id -> tournament_matches.id`
- 제약
  - `(match_id, slot_no)` 유니크
  - `slot_no`: `1`, `2`
  - `source_outcome`: `WINNER`, `LOSER`
  - `score >= 0`
  - `is_winner`, `is_bye`: `0`, `1`
- 예시
  - 최종전 slot 1: `source_match_id = AW`, `source_outcome = LOSER`, `placeholder_label = 승자전 패자`
  - 최종전 slot 2: `source_match_id = AL`, `source_outcome = WINNER`, `placeholder_label = 패자전 승자`
- 활용
  - 초기에는 `placeholder_label` 로 미정 참가자를 표시하고, 이전 경기 결과 확정 후 `participant_id` 를 채운다.
  - 점수와 승자 표시는 슬롯 단위로 저장한다.

#### 싱글 엘리미네이션 BYE 처리

- BYE는 가짜 참가자로 만들지 않고 슬롯으로만 표현한다.
- BYE 슬롯은 `participant_id = null`, `is_bye = 1`, `placeholder_label = 'BYE'`, `score = null`, `is_winner = 0` 으로 저장한다.
- 실제 참가자와 BYE가 만나는 싱글 엘리미네이션 경기는 자동으로 `FINISHED` 처리하고, 실제 참가자를 `winner_participant_id` 로 둔다.
- 자동 승리한 실제 참가자 슬롯은 `is_winner = 1`, `score = null` 로 두고, BYE 슬롯은 `is_winner = 0`, `score = null` 을 유지한다.
- 자동 승자는 `WINNER` route 를 따라 다음 경기 슬롯 또는 결과 슬롯으로 이동한다.
- BYE 경기에는 실제 패자가 없으므로 `LOSER -> ELIMINATED` route 는 만들지 않거나, 처리 시 무시한다.

### `tournament_match_score_submissions`

- 관리자 승인 전 경기 점수 제출 내역
- 주요 컬럼
  - `id`
  - `tournament_id`
  - `match_id`
  - `submitted_by_user_id`
  - `submitted_by_participant_id`
  - `submitter_role`
  - `slot1_score`, `slot2_score`
  - `winner_slot_no`
  - `map_id`
  - `status`
  - `admin_reviewer_user_id`
  - `admin_reviewed_at`
  - `admin_note`
  - `reg_date`, `update_date`
- FK
  - `tournament_id -> tournaments.id`
  - `match_id -> tournament_matches.id`
  - `submitted_by_user_id -> users.id`
  - `submitted_by_participant_id -> tournament_participants.id`
  - `map_id -> maps.id`
  - `admin_reviewer_user_id -> users.id`
- 제약
  - `submitter_role`: `PLAYER`, `ADMIN`
  - `status`: `PENDING`, `APPROVED`, `REJECTED`
  - `slot1_score >= 0`, `slot2_score >= 0`
  - `winner_slot_no`: `1`, `2`
- 사용
  - 내부 회원 참가자와 관리자는 READY 경기의 점수를 제출할 수 있다.
  - 외부 참가자는 `user_id = null` 이므로 직접 제출할 수 없고 관리자가 대신 제출한다.
  - 제출만으로는 match slot, match status, route, result slot, 공식 match map 을 변경하지 않는다.
  - 제출 시 선택한 맵은 `map_id` 에 저장한다.
  - 관리자 승인 시 `map_id` 를 `tournament_matches.map_id` 로 반영하고, 점수를 `tournament_match_slots` 에 반영하고 match 를 `FINISHED` 로 확정한 뒤 route 를 전파한다.
  - 한 제출이 승인되면 같은 경기의 다른 `PENDING` 제출은 `REJECTED` 로 바꾼다.

### `tournament_clan_share_send_logs`

- 토너먼트 종료 후 ELO/시트 연동 시도 이력
- 주요 컬럼
  - `id`
  - `tournament_id`
  - `match_id`
  - `send_group_id`
  - `player1`, `player2`
  - `winner`, `loser`
  - `map_name`
  - `match_type`
  - `match_name`
  - `played_date`
  - `elo_status`, `elo_message`
  - `sheet_status`, `sheet_message`
  - `requested_by_user_id`
  - `reg_date`
- FK
  - `tournament_id -> tournaments.id`
  - `match_id -> tournament_matches.id`
  - `requested_by_user_id -> users.id`
- 제약
  - `elo_status`: `SUCCESS`, `FAILED`
  - `sheet_status`: `SUCCESS`, `FAILED`
- 사용
  - ELO API 전송 결과와 Google Sheet 기록 결과를 경기 1건 단위로 저장한다.
  - 성공/실패 모두 저장한다.
  - 관리자 화면에서 “이미 연동한 이력” 경고를 띄우는 기준은 해당 토너먼트의 로그가 1건 이상 존재하는지 여부다.
  - 같은 버튼 클릭으로 처리된 여러 경기 로그는 같은 `send_group_id` 를 가진다.

### `race_survival_progress_submissions`

- `RACE_SURVIVAL` 전체 진행안 제출 헤더
- 주요 컬럼
  - `id`
  - `tournament_id`
  - `submitted_by_user_id`
  - `status`
  - `reviewed_by_user_id`
  - `admin_note`
  - `reg_date`
  - `reviewed_at`
  - `update_date`
- FK
  - `tournament_id -> tournaments.id`
  - `submitted_by_user_id -> users.id`
  - `reviewed_by_user_id -> users.id`
- 제약
  - `status`: `PENDING`, `APPROVED`, `REJECTED`
- 사용
  - 종족 최강전은 매치별 점수 제출/승인이 아니라 전체 출전 순서/맵/스코어 진행안을 제출한다.
  - 관리자 또는 해당 토너먼트 참가자는 진행안을 제출할 수 있다.
  - 한 사용자가 같은 토너먼트에 새 진행안을 제출하면 기존 본인 `PENDING` 진행안은 `REJECTED` 로 교체된다.
  - 관리자가 진행안 하나를 승인하면 공식 `tournament_matches`, `tournament_match_slots`, 참가자 `DROPPED`, `tournament_result_slots` 가 한 번에 반영된다.

### `race_survival_progress_submission_matches`

- `RACE_SURVIVAL` 전체 진행안의 경기별 행
- 주요 컬럼
  - `id`
  - `submission_id`
  - `match_order`
  - `map_id`
  - `slot1_participant_id`
  - `slot2_participant_id`
  - `slot1_score`
  - `slot2_score`
  - `reg_date`, `update_date`
- FK
  - `submission_id -> race_survival_progress_submissions.id`
  - `map_id -> maps.id`
  - `slot1_participant_id -> tournament_participants.id`
  - `slot2_participant_id -> tournament_participants.id`
- 제약
  - `(submission_id, match_order)` 유니크
  - `match_order > 0`
  - 스코어는 `1:0` 또는 `0:1`
  - `slot1_participant_id <> slot2_participant_id`
- 사용
  - 서버 검증에서 첫 경기는 서로 다른 종족 팀 참가자 2명이어야 한다.
  - 다음 경기부터는 직전 승자가 반드시 한 슬롯에 있어야 하고, 상대는 생존 중인 다른 종족 팀 참가자여야 한다.
  - 패자는 시뮬레이션상 탈락 처리되며 같은 진행안에서 다시 출전할 수 없다.
  - 한 종족 팀만 생존할 때 진행안이 완성된 것으로 본다.

### `tournament_result_slots`

- 진출자, 우승자, 준우승자 같은 결과 자리
- 주요 컬럼
  - `id`
  - `stage_id`
  - `group_id`
  - `result_key`
  - `result_type`
  - `rank_no`
  - `label`
  - `participant_id`
  - `decided_at`
  - `reg_date`, `update_date`
- FK
  - `stage_id -> tournament_stages.id`
  - `group_id -> tournament_groups.id`
  - `participant_id -> tournament_participants.id`
- 제약
  - `(stage_id, result_key)` 유니크
  - `result_type`: `QUALIFIED`, `CHAMPION`, `RUNNER_UP`, `ELIMINATED`
- 예시
  - `A_1ST`, `QUALIFIED`, `A조 1위 진출`
  - `A_2ND`, `QUALIFIED`, `A조 2위 진출`
  - `CHAMPION`, `CHAMPION`, `우승`
- 활용
  - 듀얼 조별전에서는 조 1위/2위 진출자를 저장한다.
  - 싱글 엘리미네이션에서는 우승/준우승 결과를 저장한다.
  - 다음 stage 생성 시 `QUALIFIED` 슬롯을 읽어 본선 슬롯을 채울 수 있다.

### `tournament_routes`

- 경기 결과 이동 규칙
- 주요 컬럼
  - `id`
  - `from_match_id`
  - `outcome`
  - `target_type`
  - `to_match_id`
  - `to_slot_no`
  - `to_result_slot_id`
  - `reg_date`, `update_date`
- FK
  - `from_match_id -> tournament_matches.id`
  - `to_match_id -> tournament_matches.id`
  - `to_result_slot_id -> tournament_result_slots.id`
- 제약
  - `(from_match_id, outcome)` 유니크
  - `outcome`: `WINNER`, `LOSER`
  - `target_type`: `MATCH_SLOT`, `RESULT_SLOT`, `ELIMINATED`
  - `MATCH_SLOT` 은 `to_match_id + to_slot_no` 를 사용한다.
  - `RESULT_SLOT` 은 `to_result_slot_id` 를 사용한다.
  - `ELIMINATED` 는 목적지 컬럼을 사용하지 않는다.
- 예시
  - `A1 WINNER -> AW slot 1`
  - `A1 LOSER -> AL slot 1`
  - `AW WINNER -> A_1ST result slot`
  - `AW LOSER -> AF slot 1`
  - `AL LOSER -> ELIMINATED`
- 활용
  - 경기 확정 시 서버는 `outcome` 에 맞는 route 를 읽고 다음 경기 슬롯 또는 결과 슬롯을 채운다.
  - 듀얼 조별전과 싱글 엘리미네이션 모두 같은 route 처리 로직을 사용한다.

## 토너먼트 관계 요약

- `tournaments`
  - `tournament_participants`
  - `tournament_stages`
  - `tournament_clan_share_send_logs`
- `tournament_stages`
  - `tournament_groups`
  - `tournament_matches`
  - `tournament_result_slots`
- `tournament_groups`
  - `tournament_group_entries`
  - `tournament_matches`
- `tournament_matches`
  - `tournament_match_slots`
  - `tournament_match_score_submissions`
  - `tournament_clan_share_send_logs`
  - `tournament_routes.from_match_id`
  - `maps`
- `users`
  - `tournament_clan_share_send_logs.requested_by_user_id`
- `tournament_result_slots`
  - `tournament_routes.to_result_slot_id`

## 드래프트 관계 요약

### 기존 드래프트

- `draft_sessions`
  - `draft_teams`
  - `draft_candidates`
  - `draft_orders`
  - `draft_picks`
- `draft_teams`
  - `draft_candidates.picked_draft_team_id`
  - `draft_orders`
  - `draft_picks`
- `users`
  - `draft_sessions.owner_user_id`
  - `draft_teams.picker_user_id`
  - `draft_candidates.candidate_user_id`
  - `draft_picks.picked_by_user_id`

### 가위바위보 드래프트

- `rps_draft_sessions`
  - `entry_submission_sessions.source_rps_draft_session_id`
  - `rps_draft_teams`
  - `rps_draft_candidates`
  - `rps_draft_picks`
- `rps_draft_teams`
  - `rps_draft_sessions.current_draft_team_id`
  - `rps_draft_sessions.pending_draft_team_id`
  - `rps_draft_candidates.picked_rps_draft_team_id`
  - `rps_draft_picks`
- `users`
  - `rps_draft_sessions.owner_user_id`
  - `rps_draft_teams.picker_user_id`
  - `rps_draft_picks.picked_by_user_id`

### Entry submission

- `entry_submission_sessions`
  - `entry_submission_teams`
  - `entry_submission_players`
  - `entry_submission_entries`
- `entry_submission_teams`
  - `entry_submission_players`
  - `entry_submission_entries`
- `entry_submission_players`
  - `entry_submission_entries.entry_submission_player_id`
- `users`
  - `entry_submission_sessions.owner_user_id`
  - `entry_submission_teams.captain_user_id`
  - `entry_submission_entries.submitted_by_user_id`

## 드래프트 인덱스 요약

### 기존 드래프트

- `draft_sessions`
  - `status`
  - `owner_user_id`
  - `proleague_id`
- `draft_teams`
  - `draft_session_id`
  - `picker_user_id`
  - `proleague_team_id`
- `draft_candidates`
  - `draft_session_id`
  - `candidate_user_id`
  - `draft_session_id, status`
- `draft_orders`
  - `draft_session_id`
  - `draft_team_id`
- `draft_picks`
  - `draft_session_id`
  - `draft_team_id`
  - `candidate_user_id`

### 가위바위보 드래프트

- `rps_draft_sessions`
  - `status`
  - `owner_user_id`
  - `current_draft_team_id`
  - `pending_draft_team_id`
- `rps_draft_teams`
  - `rps_draft_session_id`
  - `picker_user_id`
- `rps_draft_candidates`
  - `rps_draft_session_id`
  - `rps_draft_session_id, status`
  - `rps_draft_session_id, display_order`
- `rps_draft_picks`
  - `rps_draft_session_id`
  - `rps_draft_team_id`
  - `candidate_id`

### Entry submission

- `entry_submission_sessions`
  - `status`
  - `owner_user_id`
  - `source_rps_draft_session_id`
- `entry_submission_teams`
  - `entry_submission_session_id`
  - `captain_user_id`
- `entry_submission_players`
  - `entry_submission_session_id`
  - `entry_submission_team_id`
- `entry_submission_entries`
  - `entry_submission_session_id`
  - `entry_submission_team_id`
  - `entry_submission_player_id`

## 토너먼트 인덱스 요약

- `tournaments`
  - `status`
  - `owner_user_id`
- `tournament_participants`
  - `tournament_id`
  - `user_id`
  - `tournament_id, status`
- `tournament_stages`
  - `tournament_id`
  - `tournament_id, status`
- `tournament_groups`
  - `stage_id`
- `tournament_group_entries`
  - `group_id`
  - `participant_id`
- `tournament_matches`
  - `stage_id`
  - `group_id`
  - `stage_id, status`
  - `winner_participant_id`
- `tournament_match_slots`
  - `match_id`
  - `participant_id`
  - `source_match_id`
- `tournament_match_score_submissions`
  - `match_id`
  - `tournament_id`
  - `status`
  - `submitted_by_user_id`
- `tournament_result_slots`
  - `stage_id`
  - `group_id`
  - `participant_id`
- `tournament_routes`
  - `from_match_id`
  - `to_match_id`
  - `to_result_slot_id`

## 작업 시 주의사항

- `match_infos.league_id` 는 비정규화 컬럼이므로 `series_infos.league_id` 와 논리적으로 일치해야 한다.
- `boards.user_id`, `comments.user_id` 는 guest 작성 지원 때문에 nullable 이다.
- `comments` 는 자기참조 구조이므로 삭제와 조회 로직에서 부모-자식 관계를 같이 봐야 한다.
- `vectors` 는 정형 FK 가 없으므로 애플리케이션에서 무결성을 보장해야 한다.
- 기존 드래프트 구조 `draft_*` 는 `FIXED_ORDER` 단일 구조를 기준으로 유지한다.
- `draft_sessions.order_mode` 는 필수 컬럼이다. 기존 행은 `BASIC` 으로 백필하되, 향후 모드 추가를 위해 DB CHECK 제약은 추가하지 않는다.
- 가위바위보 드래프트는 `rps_draft_*` 테이블군으로 분리해서 운영한다.
- 토너먼트 대진표는 `tournaments` 와 `tournament_*` 테이블군으로 운영하며 기존 `match_infos` 경기 기록 구조와 섞지 않는다.
- `tournament_matches.status` 는 `LIVE` 를 쓰지 않는다. 개별 경기는 `READY` 에서 결과 입력 후 `FINISHED` 로 확정한다.
- `tournament_matches.best_of` 가 실제 경기 방식의 단일 기준이다. stage 단위 `default_best_of` 컬럼은 두지 않는다.
- `db-schema-alter.sql` 은 이번 변경분인 가위바위보 드래프트 `READY` 제거와 즉시 `RPS_PENDING` 시작 상태 전환만 반영한다.
## Tournament Match Set Results

This schema version stores SINGLE_ELIMINATION and DUAL_GROUP match results at set level.

- `tournament_match_score_submissions`
  - `best_of` stores the BO value submitted for review.
  - `slot1_score`, `slot2_score`, and `winner_slot_no` remain aggregate values derived from submitted sets.
  - `map_id` remains a representative map value for compatibility and uses the first played set map for set-based submissions.

- `tournament_match_sets`
  - Official match set defaults and approved set results.
  - Columns: `id`, `match_id`, `set_no`, `map_id`, `winner_slot_no`, `reg_date`, `update_date`.
  - `winner_slot_no` is null for creation-time defaults and set to `1` or `2` after score approval.
  - Unique key: `(match_id, set_no)`.

- `tournament_match_score_submission_sets`
  - Pending/reviewed submitted set results.
  - Columns: `id`, `score_submission_id`, `set_no`, `winner_slot_no`, `map_id`, `reg_date`, `update_date`.
  - Every submitted played set requires a winner slot and map.
  - Bo3 2:0 stores only sets 1 and 2; later entered sets are ignored.
