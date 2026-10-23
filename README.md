# TasteDNA

TasteDNA는 사용자의 패션 취향을 질문과 선택지 응답을 통해 관측하고, 이를 근거로 취향 분석 및 RESULT 분류를 제공하기 위한 백엔드 시스템입니다.

기존 패션 쇼핑몰과 연동하는 것을 주요 목표로 하며, 독립적인 쇼핑몰 전체를 개발하는 프로젝트는 아닙니다.

## 1. 개발 목표

TasteDNA의 기본 처리 흐름은 다음과 같습니다.

질문 제공 → 사용자 답변 저장 → 선택지별 관측 규칙 조회 → Raw 관측 생성 → RESULT 후보 추출 → 근거 평가 → 최종 RESULT 판별

**현재는 초기 RESULT 후보 추출과 근거 준비 상태 평가까지 일부 구현된 개발 단계입니다. 최종 RESULT 판별 기능은 완성되지 않았습니다.**

## 2. 기술 스택

- Java 21
- Spring Boot 4.1.1
- Gradle
- MySQL 8.4
- Spring Data JPA
- Flyway
- Spring Security
- JUnit 5 / Mockito

주요 개발 환경은 Windows 및 IntelliJ IDEA Ultimate입니다.

## 3. 테스트 구성

| 구분 | Simple | Deep |
|---|---|---|
| 질문 수 | 10개 | 25개 |
| 최종 RESULT 목표 | 최대 5개 | 최대 2개 |

전체 RESULT 설계 대상은 25개 후보입니다.

최종 RESULT 개수를 채우기 위해 근거가 부족한 후보를 임의로 출력하지 않습니다.

점수, 가중치, 판별 임계값 등은 승인된 설계와 검증을 거쳐 결정해야 합니다.

## 4. 프로젝트 구조

```text
src/main/java/com/tastedna/
├── config/       보안 및 실행 환경 설정
├── definition/   테스트 정의 및 질문 정의
├── session/      세션 생성, 질문 조회, 답변 저장 및 검증
└── scoring/      직접 관측, Raw 관측, RESULT 후보 관련 기능

src/main/resources/db/migration/
                  Flyway 데이터베이스 스키마 변경

src/test/
                  단위 테스트 및 통합 테스트

dev/fixtures/
                  로컬 개발용 질문, 관측 규칙 및 RESULT 설정

docs/
                  질문·답변, 채점, 이미지 제작 관련 계약과 명세
```

## 5. 현재 구현 상태

### 구현된 주요 기능

- 회원별 테스트 세션 생성 및 조회
- 기존 세션 재개
- 단일·복수·비교 선택형 답변 검증
- 질문별 답변 저장 및 수정
- Revision을 통한 오래된 수정 요청 차단
- 세션 행 잠금을 이용한 동시 변경 제어
- 선택지별 직접 관측 규칙 조회
- Raw 관측 데이터 생성
- Q02 직접 스타일 선택을 이용한 초기 RESULT 후보 추출
- 시각적 근거 메타데이터의 준비 상태 검사
- 관련 단위 테스트 및 일부 실제 DB 통합 테스트

### 아직 완료되지 않은 기능

- 전체 RESULT 판별 규칙
- 확정된 점수·가중치·임계값 기반 계산
- 최종 RESULT 저장 및 조회 API
- 이미지 기반 질문의 정식 이미지 연결 및 검수
- 운영용 쇼핑몰 회원 인증 연동
- 운영 환경용 RESULT 규칙·메타데이터 관리
- Deep 전체 질문의 실제 구현 및 검증

클래스나 데이터 구조가 존재하더라도 기능 구현이 완료됐다는 뜻은 아닙니다.

## 6. 주요 API

기본 경로: `/api/v1`

| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/health` | 상태 확인 |
| POST | `/sessions` | 테스트 세션 생성 |
| GET | `/sessions/active` | 활성 세션 조회 |
| GET | `/sessions/{sessionId}` | 세션 조회 |
| POST | `/sessions/{sessionId}/resume` | 세션 재개 |
| POST | `/sessions/{sessionId}/answers` | 답변 저장·수정 |
| GET | `/sessions/{sessionId}/questions` | 질문 목록 조회 |
| GET | `/sessions/{sessionId}/questions/{questionId}` | 개별 질문 조회 |

개발 전용 API:

`GET /api/v1/sessions/{sessionId}/raw-observations`

해당 API는 `dev` 프로필에 연결된 개발용 관측 확인 인터페이스이며, 정식 RESULT API가 아닙니다.

## 7. 로컬 실행 환경

데이터베이스 연결 설정은 `src/main/resources/application.yaml`에서 관리합니다.

기본 개발 DB:

```text
tastedna
```

테스트 DB:

```text
tastedna_test
```

필요한 환경 변수:

```text
DB_USERNAME
DB_PASSWORD
```

로컬 개발용 인증은 `local` 프로필에서 동작하며 다음 환경 변수를 사용합니다.

```text
DEV_MEMBER_PASSWORD
```

개발용 계정:

```text
dev-member-001
dev-member-002
```

이 계정들은 쇼핑몰의 실제 회원 인증을 대체하지 않습니다.

`local` 이외의 보안 설정은 기본적으로 헬스 체크 외의 API 요청을 차단합니다.

개발용 DB와 테스트 DB를 혼동하지 않아야 하며, 테스트 실행 전 필요한 스키마와 fixture 데이터가 준비돼 있어야 합니다.

## 8. 빌드 및 테스트

Windows 프로젝트 루트에서 Gradle Wrapper를 사용합니다.

```powershell
.\gradlew.bat compileJava
```

전체 테스트:

```powershell
.\gradlew.bat test
```

통합 테스트에는 MySQL 테스트 DB와 사전 준비된 개발 데이터가 필요할 수 있습니다.

일부 테스트는 개발용 fixture 또는 고정된 세션 데이터를 참조하므로 독립 실행 가능 여부를 확인해야 합니다.

테스트가 실패했다고 해서 실제 개발 DB 데이터를 임의로 수정하거나 초기화하지 않습니다.

## 9. RESULT 개발 원칙

1. 직접 관측과 추론을 구분합니다.
2. 사용자가 선택하지 않은 항목을 자동으로 부정적 취향으로 해석하지 않습니다.
3. 비교형 질문의 상대적 선호 관계를 보존합니다.
4. 관측 근거 없이 RESULT를 확정하지 않습니다.
5. 미확정 점수·가중치·임계값을 임의로 만들지 않습니다.
6. 테스트 정의와 질문·선택지 ID의 버전 정합성을 유지합니다.
7. 동일한 입력과 동일한 판별 규칙은 재현 가능한 결과를 생성해야 합니다.

현재 초기 후보 추출은 Q02의 명시적 스타일 선호 관측을 기반으로 합니다.

Q05·Q08·Q09·Q10의 시각적 근거는 추가 메타데이터 검증이 필요하며, 현재 최종 RESULT 준비 상태는 완료로 표시되지 않습니다.

## 10. 개발자 및 AI 에이전트 작업 지침

개발을 시작하기 전에 다음 자료를 확인합니다.

- `AGENTS.md`: 프로젝트 개발 원칙 및 AI 작업 규칙
- `docs/question-answer-contract.md`: 질문 및 답변 계약
- `docs/scoring-contract.md`: 관측·채점·RESULT 설계 원칙
- `docs/image-production-spec.md`: 이미지 제작 및 검수 기준
- `dev/fixtures/`: 개발용 질문·관측·RESULT 설정
- 실제 Java 구현 및 테스트

AI 또는 외부 개발자는 기존 구현을 확인한 후 변경해야 합니다.

승인되지 않은 RESULT 계산 공식, 점수, 판별 조건을 새로 만들지 않습니다.

Java 코드에는 유지보수에 필요한 역할, 설계 의도 및 중요한 제약을 중심으로 한글 주석을 사용합니다. 단순 코드에 불필요한 주석을 반복하지 않습니다.

## 11. 다음 개발 방향

현재 우선 검토할 개발 작업은 다음과 같습니다.

1. RESULT 초기 후보와 직접 관측 근거의 연결
2. 후보별 근거 추적 및 설명 가능성 강화
3. 이미지 기반 근거 검증 및 메타데이터 확정
4. 승인된 RESULT 판별 규칙 구현
5. 최종 RESULT 저장 및 조회 기능
6. 쇼핑몰 연동 및 운영 환경 준비

구체적인 구현 순서는 최신 코드와 승인된 설계 문서를 기준으로 결정합니다.

---

이 문서는 현재 개발 상태를 설명하는 프로젝트 진입점입니다. 세부 설계와 실제 동작은 승인된 설계 문서 및 최신 소스 코드를 함께 확인해야 합니다.