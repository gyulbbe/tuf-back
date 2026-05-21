---
name: tuf-back-backend
description: Use when working in the tuf-back Spring Boot backend repository, including controller/service/repository changes, Oracle DB schema work, QueryDSL or JPA changes, JWT auth flow, and backend support for draft, match, league, board, or chat features.
---

# tuf-back Backend

## 참가자/사용자 표시명 정책

- 특별한 요청이 없으면 API 응답에서 사용자 실명이나 이름(`name`)을 화면 표시명으로 내려주지 않는다.
- 내부 회원은 토너먼트/드래프트/대진표/경기 결과 등 사용자에게 보이는 표시명에 로그인 아이디/사용자 아이디(`userId`)만 사용한다.
- 토너먼트 참가자 응답의 `displayName`은 내부 회원이면 `users.user_id`, 외부 참가자면 `tournament_participants.participant_name`을 사용한다.
- 내부 회원의 `participantName`을 `displayName`으로 덮어쓰지 않는다. 내부 회원의 `participantName`은 null 또는 저장된 원본 값으로 둔다.
- 외부 참가자처럼 연결된 `userId`가 없는 경우에만 `participantName`을 사용자 표시명으로 사용한다.
- `users.name`은 관리자 관리 화면처럼 명시적으로 요구된 경우에만 사용하고, 기본 공개/진행 화면 응답에는 노출하지 않는다.

이 스킬은 `tuf-back` 백엔드 작업용이다.

## 먼저 볼 문서

- 개발 환경과 실행 방법: [references/dev-environment.md](references/dev-environment.md)
- DB 구조와 관계: [references/database-schema.md](references/database-schema.md)

## 현재 개발 환경 요약

- Java 25
- Spring Boot 3.5.7
- Gradle Wrapper 기반
- 기본 프로필: `local`
- 기본 포트: `8080`
- 프론트 개발 서버 기본 주소: `http://localhost:5173`
- Oracle Database
- JPA + MyBatis + QueryDSL 혼합 사용
- JWT 기반 stateless 인증
- 로컬 AI는 Ollama, 운영 기본은 Cloudflare Workers AI

## 에이전트 작업 경계 (중요)

이 저장소에서 에이전트는 **빌드/실행/테스트를 직접 수행하지 않는다.**
이유: `./gradlew build`, `bootRun`, `test` 는 `build/`, `.gradle/`, `~/.gradle/`
아래에 대량의 산출물과 캐시를 만들고, 사용자의 IntelliJ 빌더와 충돌한다.

에이전트가 할 수 있는 일
- 소스 읽기, 편집, 신규 파일 생성
- 정적 분석 수준의 검증 (임포트 확인, 시그니처 일치, 엔티티-스키마 대조)
- 테스트 코드 작성 및 수정
- 설정 파일, 스키마 파일 갱신

에이전트가 하지 말아야 할 일
- `gradlew`, `gradle`, `mvn` 계열 명령 실행
- `bootRun`, `build`, `test`, `bootJar`, `check` 태스크 실행
- 의존성을 받는 모든 명령 (`--refresh-dependencies` 포함)
- `build/`, `out/`, `.gradle/` 디렉토리에 파일 쓰기

검증이 필요하면 사용자에게 요청한다. 아래 "사용자에게 요청하기" 참고.

## 작업 원칙

- 설정 변경 전에는 `application.properties`, `application-local.properties`, `application-prod.properties` 를 같이 본다.
- 데이터 조회 경로를 하나로 단정하지 말고 JPA Repository, QueryDSL, MyBatis 사용 지점을 같이 확인한다.
- DB 변경 전에는 항상 [references/database-schema.md](references/database-schema.md), `db-schema.sql`, `db-schema-alter.sql` 을 같이 맞춘다.
- FK 관계가 있는 테이블은 삭제나 컬럼 변경 전에 영향 범위를 먼저 확인한다.
- 민감값은 하드코딩하지 말고 설정 또는 secret 주입 기준으로 둔다.

## DB 스키마 파일 역할

세 파일은 역할이 다르다. 섞어 쓰지 않는다.

- `references/database-schema.md`
  현재 DB 구조를 사람이 읽기 위한 문서. 테이블/컬럼/관계/제약이 바뀌면 같이 갱신한다.

- `references/db-schema.sql`
  **현재 DB 전체 스키마의 스냅샷.** 빈 DB에서 처음부터 구축할 때 쓰는 전체 DDL이다.
  변경분 누적이 아니라 "지금 이 순간 완성된 상태" 기준으로 유지한다.
  스키마가 바뀌면 해당 부분을 수정해서 최종 상태가 반영되도록 한다.

- `references/db-schema-alter.sql`
  **이번 변경분만 담는 일회성 마이그레이션 스크립트.**
  이미 운영 중인 DB에 그대로 실행해서 "현재 상태 → 목표 상태" 로 맞추는 용도다.
  아래 규칙을 반드시 지킨다.

### `db-schema-alter.sql` 작성 규칙 (중요)

이 파일은 누적 히스토리가 아니다. 이전 변경분은 이미 사용자가 실행해서 DB에 반영됐다고 가정한다.

- 스키마 변경 작업을 시작하면 **파일의 기존 내용을 전부 삭제하고**, 이번 작업에서 바뀐 부분만 새로 작성한다.
- 이전 ALTER / DROP / ADD 문을 보존하거나 뒤에 덧붙이지 않는다.
- 이 파일 하나를 SQL Developer 등에서 그대로 실행했을 때 DB가 목표 상태로 한 번에 맞춰지도록 구성한다.
- 필요한 경우 ADD / DROP / MODIFY / RENAME, 데이터 백필, 시퀀스 조정 등을 실제 실행 순서대로 나열한다.
- 실행이 끝났다는 사용자 확인이 있으면, 다음 작업에서 다시 내용을 비우고 쓴다.
- `db-schema.sql` 과 `database-schema.md` 는 위와 별개로, 변경이 모두 반영된 **최종 상태** 기준으로 갱신한다.

## 드래프트 작업 지침

현재 드래프트는 두 갈래가 공존한다.

### 1. 기존 드래프트

- `draft_*` 테이블을 사용한다.
- `draft_orders` 기반 순서형 드래프트다.
- `FIXED_ORDER` 단일 구조로 본다.
- `draft_sessions.draft_mode` 는 제거 대상이며 새 분기 기준으로 삼지 않는다.
- `assignNextPicker` 같은 수동 팀장 전용 흐름은 폐기 대상으로 본다.
- 기존 드래프트를 수정할 때는 `current_pick_no + draft_orders` 기준 흐름을 우선한다.

### 2. 가위바위보 드래프트

- `rps_draft_*` 테이블을 사용한다.
- 기존 `draft_*` 와 섞지 않는다.
- `2팀 전용` 이다.
- 세션 생성 시 팀 2개를 자동 생성하는 전제를 둔다.
- 순번 테이블이 없다.
- 제한 시간이 없다.
- 흐름은 `가위바위보 -> 승자 1픽 -> 패자 1픽 -> 다시 가위바위보` 반복이다.

가위바위보 드래프트 작업 시 확인할 포인트

- `rps_draft_sessions.status` 는 `READY`, `RPS_PENDING`, `PICKING`, `FINISHED` 만 쓴다.
- `owner_user_id` 는 세션 등록자이자 라이브 관리 주체다.
- `rps_draft_teams.picker_user_id` 는 실제 RPS 제출과 픽 수행 계정이다.
- 누가 냈는지는 `team1_rps_choice`, `team2_rps_choice` 의 null 여부로 판단할 수 있다.
- 프론트 노출 정책과 DB 저장 구조를 구분해서 본다.
  - DB에는 선택값을 저장할 수 있다.
  - 프론트에는 둘 다 제출되기 전까지 선택값을 숨길 수 있다.

## 사용자에게 요청하기

빌드와 테스트는 사용자가 IntelliJ 또는 터미널에서 직접 수행한다.
에이전트는 필요한 시점에 **무엇을 어떻게** 해야 하는지 정확히 요청한다.

### 서버가 필요한 작업을 할 때

API 동작 확인, 컨트롤러 통합 테스트, 실제 DB 연동 확인 등
서버 기동이 전제인 작업 요청을 받았을 때는, 먼저 서버 상태를 확인한다.

확인 방법: 사용자에게 아래처럼 물어본다.

> `tuf-back` 서버 지금 `8080` 포트에 떠 있어? 확인이 필요한 작업이라 상태 먼저 알려줘.

서버가 꺼져 있다고 하면, 아래 스니펫을 **사용자가 실행하도록** 안내한다.
에이전트가 직접 실행하지 않는다.

```powershell
# tuf-back 루트에서
.\gradlew.bat bootRun
```

IntelliJ 에서 실행하고 있다면 Run 구성에서 `local` 프로필로
`TufBackApplication` 을 기동한 상태인지 확인받는다.

기동 후 확인할 것을 같이 알려준다.
- 콘솔에 `Started TufBackApplication` 이 찍혔는지
- `http://localhost:8080/actuator/health` 가 200 을 주는지 (actuator 가 켜져 있다면)
- 필요한 경우 로그인 JWT 발급까지 된 상태인지

### 단위 테스트를 돌려야 할 때

단위 테스트도 에이전트가 직접 실행하지 않는다.
테스트 코드를 작성/수정한 뒤, 실행은 사용자에게 넘긴다.

추천 순서 (부하 적은 순)
1. IntelliJ 에서 테스트 클래스 옆 ▶ 버튼으로 실행 (`out/` 만 생김)

요청 문구 예시

> `DraftServiceTest#픽_순서_검증` 만 돌려보고 실패 메시지 붙여줘.
> 실패 스택 전체가 필요해.

### 에이전트가 결과를 받는 방법

- 컴파일 에러: IntelliJ `Problems` 탭의 빨간 줄 내용 또는 터미널 출력 전체
- 테스트 실패: 실패한 테스트 이름 + AssertionError 메시지 + 스택
- 런타임 에러: 서버 콘솔 로그에서 예외 스택 전체
- SQL 이슈: 실제 실행된 바인드 쿼리와 오라클 에러 코드 (`ORA-xxxxx`)

단편적인 메시지만으로 추정하지 않는다. 부족하면 추가 정보를 다시 요청한다.

## 백엔드 구현 체크리스트

- Controller 는 얇게 두고 비즈니스 로직은 Service 로 모은다.
- Entity 는 상태 변경 메서드를 두고 Setter 남발을 피한다.
- QueryDSL 조건은 메서드로 분리해서 null-safe 하게 만든다.
- 트랜잭션 경계는 Service 기준으로 잡는다.
- Oracle 시퀀스와 PK 전략이 맞는지 확인한다.
- DB 스키마를 바꿨으면 아래 파일을 같이 갱신한다. 각 파일 용도와 작성 방식은 "DB 스키마 파일 역할" 섹션을 따른다.
  - `references/database-schema.md` — 문서 갱신
  - `references/db-schema.sql` — 변경 반영된 최종 스냅샷으로 수정
  - `references/db-schema-alter.sql` — **기존 내용 전부 삭제 후** 이번 변경분만 새로 작성
