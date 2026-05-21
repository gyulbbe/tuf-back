# tuf-back 개발환경 요약

이 문서는 `tuf-back` 백엔드 작업 전에 현재 코드베이스 기준 개발 스택과 로컬 실행 전제를 빠르게 파악하려고 만든 메모다.

## 현재 스택

- JDK 25 toolchain
- Gradle Wrapper 9.4.0
- Spring Boot 3.5.7
- Spring Dependency Management Plugin 1.1.7
- Spring AI 1.0.3
- QueryDSL 5.1.0
- MyBatis Spring Boot Starter 3.0.5
- springdoc-openapi 2.8.6
- JJWT 0.12.3
- Oracle JDBC `ojdbc17` + `oraclepki`
- Lombok, Spring DevTools

## 백엔드 구성

- Spring MVC 기반 REST API
- Spring WebSocket + STOMP 실시간 드래프트 기능
- Spring Security + JWT 기반 stateless 인증
- Spring OAuth2 Client 의존성 포함
- Spring Data JPA + QueryDSL + MyBatis + JDBC 혼합 사용
- 외부 연동은 현재 `RestTemplate` 기반
- 스케줄링 사용 (`@EnableScheduling`)
- Swagger/OpenAPI 문서화 사용

## 로컬 실행 기준

- 기본 활성 프로필: `local`
- 기본 포트: `8080`
- 로컬 프론트엔드 주소: `http://localhost:5173`
- Swagger UI: `/swagger-ui.html`
- OpenAPI Docs: `/v3/api-docs`
- WebSocket 엔드포인트: `/ws`
- STOMP broker prefix: `/topic`
- STOMP app prefix: `/app`

## 로컬 필수 의존 환경

- Oracle DB
- 로컬 접속 정보: `jdbc:oracle:thin:@localhost:1522/FREEPDB1?characterEncoding=AL32UTF8`
- 로컬 DB 계정: `tuf / 1234`
- Ollama: `http://localhost:11434`
- 로컬 채팅 모델: `gemma4:e4b`
- 임베딩 API: `http://localhost:8000`
- 로컬에서는 Cloudflare 설정을 비워두면 Ollama만 사용한다

## 데이터 계층

- 운영 DB는 Oracle Wallet/TNS 기반 접속
- JPA Repository, QueryDSL custom repository, MyBatis XML mapper를 같이 쓴다
- MyBatis 매퍼 위치: `src/main/resources/mybatis/mappers/*.xml`
- 타입 핸들러 패키지: `io.github.gyulbbe.common.utils.embeddingVector`
- Oracle 23ai 벡터 검색을 사용한다
- 유사도 검색은 MyBatis 쿼리에서 `VECTOR_DISTANCE`로 처리한다

## 인증 및 보안

- 커스텀 `SecurityConfig`를 사용한다
- 앱 시작 시 `SecurityAutoConfiguration`은 제외한다
- 세션 정책은 stateless다
- `LoginFilter`, `JWTFilter`로 인증 흐름을 처리한다
- 비밀번호 인코더는 `BCryptPasswordEncoder`
- CORS 허용 origin은 `tuf-front.url` 설정값 기준
- 현재 `/admin`만 역할 제한이 있고, 나머지 요청은 `permitAll`

## AI 및 임베딩

- 기본 Cloudflare 모델: `@cf/google/gemma-4-26b-a4b-it`
- 채팅은 Cloudflare Workers AI 우선, 실패 시 Ollama fallback 구조다
- Cloudflare quota 소진 시 UTC 자정까지 Ollama로 전환한다
- 자정 이후에는 3분 간격으로 최대 5번 probe 재시도를 한다
- 임베딩은 Spring AI 모델 호출이 아니라 외부 FastAPI `/embed` 호출 방식이다
- 생성한 벡터는 Oracle에 저장하고 유사도 검색에 사용한다
- `spring-ai-starter-vector-store-oracle` 의존성은 있지만 앱 시작 시 `OracleVectorStoreAutoConfiguration`은 제외한다

## 주요 패키지

- `board`
- `chat`
- `commentary`
- `common`
- `config`
- `draft`
- `health`
- `jwt`
- `league`
- `map`
- `match`
- `speech`
- `user`

## 설정 파일

- `src/main/resources/application.properties`: 공통 설정
- `src/main/resources/application-local.properties`: 로컬 개발 설정
- `src/main/resources/application-prod.properties`: 운영 설정 템플릿
- 민감값은 운영에서 secret 주입 기준으로 관리한다

## 빌드 및 실행

```powershell
.\gradlew.bat bootRun
.\gradlew.bat test
```

- Gradle `test`는 JUnit Platform 사용
- `jar` 태스크는 비활성화되어 있다
- `processResources`에서는 일부 Oracle SQL 스크립트를 제외한다

## 테스트 현황

- 테스트 파일은 현재 13개다
- `@DataJpaTest` + H2 Oracle mode 기반 테스트가 중심이다
- 드래프트, 시퀀스, 유저, 보드 영역 테스트가 포함돼 있다
- `@SpringBootTest` 기반 `contextLoads()` 스모크 테스트가 있다
- Mockito 기반 서비스 단위 테스트도 일부 있다
