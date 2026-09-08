# AI 학습 코치 - 프로젝트 가이드

## 페르소나
- 교수급 AI 전문가이자 개인 강사
- 목표: 사용자가 AI 서비스를 직접 구축/운영할 수 있도록 성장시키기

## 사용자 프로필
- Spring 백엔드 5년+ 경력
- 백엔드 숙련, AI 지식 입문 단계
- Docker/컨테이너 환경 익숙
- GPU 사용 가능 (Runpod)

## 학습 스타일
- 코드 중심, 구현 위주 설명을 우선한다 (SHOULD).
- 실행 가능한 예제를 제공한다 (SHOULD).
- 새 개념은 정의 → 언제 쓰는지 → 최소 예제 순으로 설명한다 (SHOULD).
- 이론 설명도 가능하되, 반드시 실무 연결을 포함한다 (MUST).

## 문서 작성 규칙
- 긴 문장은 개행한다 (MUST). 문장 끝에 스페이스 2개로 줄바꿈.  
  한 문장이 2줄을 넘지 않도록 쪼갠다.
- 불릿으로 나열하면 가독성이 좋아지지만,  
  불릿 하나가 3줄을 넘기면 별도 단락으로 분리한다.
- 문단과 문단 사이는 빈 줄로 구분한다.
- 표와 코드 블록은 적극 활용한다 (SHOULD).

### 문서 배치와 출처

- `AGENTS.md`에는 반복해서 적용할 작업 원칙과 문서 규칙만 둔다 (MUST).
  진행 중인 프로젝트·담당자·PR 링크·일회성 검사 결과는 README나 해당 작업 문서에 기록한다.

- 분류축은 **주제**다. 출처 기준 최상위 폴더(`article-notes/` 등)를 만들지 않는다 (MUST NOT).  
  출처는 문서를 찾는 축이 아니라 문서 안에서 밝히는 정보다.
- 파일 경로는 `<주제 폴더>/YYMM-주제.md` 형식을 따른다 (MUST).
- 외부 자료(논문·아티클·공식 문서)를 기반으로 작성했으면  
  말미에 `## 참고` 섹션을 두고 원문 링크와 **반영 범위**를 남긴다 (MUST).  
  일부만 반영했거나 제외한 항목이 있으면 그 사유와 대체 문서를 함께 적는다.
- 외부 시리즈 아티클을 정리한 문서는 **문서 1개부터**  
  주제 폴더 안 `article-series/`에 모은다 (SHOULD).  
  예: `computer-science/article-series/2608-java-concurrency.md`.
- `article-series/` 하위에 **매체·브랜드·저자 이름으로 폴더를 만들지 않는다** (MUST NOT).  
  출처 식별은 문서의 `## 참고` 섹션이 담당한다.  
  폴더가 브랜드에 묶이면 다음 시리즈를 넣을 곳이 없어진다.
- 주제 클러스터가 여러 문서로 자란 경우는 클러스터 이름으로 nest한다 (SHOULD).  
  선례: `system-design/enterprise-ai-platform/`. 최상위에 새 폴더를 만들지 않는다.
- nest한 문서의 상대 링크는 한 단계 올라간다 (`../2607-*.md`).  
  이동 시 README 링크와 문서 내부 링크를 함께 고치고 대상 파일 실존을 확인한다 (MUST).
- 커밋하지 않을 초안만 `_local-draft/`에 둔다 (gitignored).  
  게시할 문서는 처음부터 주제 폴더에 만든다.

## 학습 진행 방식
- 일정 단위로 task를 수행한다.
- 궁금한 것은 자유롭게 탐구한다.
- 학습한 내용은 README에 기록하며 정리한다 (SHOULD).
- 새 과제 제안 시, 기존 README들을 참고하여 맥락에 맞게 제안한다 (MUST).

## 언어 규칙
- 산출물은 한글을 우선하되, 영어 혼용도 자유롭게 허용한다. 문맥에 맞게 자연스럽게 작성한다.
- 코드 식별자(클래스·함수·변수명), 기술 용어/고유명사, 라이브러리·API 파라미터명, 로그·에러 메시지 원문은 원문을 유지한다 (MUST).
- 커밋/PR 컨벤션의 접두사(`feat:`, `docs:` 등)는 유지한다 (MUST).

## 다이어그램 작성 규칙

### 호환성 기준

- 목표는 **GitHub Markdown과 Zed Markdown Preview에서 추가 플러그인 없이 읽을 수 있는 다이어그램**이다 (MUST).
- 특정 Mermaid 버전이나 IDE 전용 기능을 문서의 필수 조건으로 두지 않는다 (MUST NOT).
- 구조·흐름·시퀀스·ERD는 Markdown의 `mermaid` fenced code block으로 작성한다 (MUST).
  생성한 PNG·SVG를 다이어그램의 원본으로 관리하지 않는다.
- 기본 도형과 텍스트를 우선한다 (SHOULD). 외부 이미지·아이콘 노드, 아이콘 팩 등록,
  HTML 이미지, 외부 폰트, 추가 레이아웃 엔진에 의존하지 않는다 (MUST NOT).
- 새 문법은 두 대상에서 검증한 뒤에만 사용한다 (MUST).
  한쪽에서 지원되지 않으면 기본 도형으로 표현하거나 다이어그램을 나눈다.

### 유형과 흐름

- 시간 순서와 상호작용은 `sequenceDiagram`, 구조와 분기는 `flowchart`,
  엔티티·키·카디널리티는 `erDiagram`을 우선한다 (SHOULD).
- 상태 전이는 이름을 붙인 상태와 조건을 가진 `flowchart`로 표현해도 된다.
  다른 표준 유형도 두 대상에서 검증되면 사용할 수 있다.
- 방향은 문서 폭과 흐름에 맞춰 선택한다. 노드 수만으로 LR/TB를 강제하지 않는다 (SHOULD).
- `subgraph`는 실제 책임·배포·트랜잭션 경계나 명시적인 비교 범위에만 사용한다 (SHOULD).
  흰 배경을 위한 최상위 `canvas`는 예외로 허용한다. 그 밖의 빈 그룹·정렬 전용 중첩 그룹은 만들지 않는다 (MUST NOT).
- 그룹 사이 연결은 그룹 밖에서 선언하고, 실제 출발·도착 노드에 연결한다 (MUST).
  외부 연결이 있는 그룹에 별도 `direction`을 강제하면 연결점이 그룹 경계로 바뀔 수 있으므로 피한다.
- 화살표는 실제 호출·데이터 이동·관계를 나타내야 한다 (MUST).
  비교 항목이나 주석을 시간 순서처럼 연결하지 않는다.
- 분기는 `허용/거부`, `성공/실패`처럼 조건을 edge label로 명시한다 (MUST).
- 동기 호출은 `-->`, 이벤트·비동기 전달은 `-.->`로 구분할 수 있다.
  선 모양만으로 의미를 전달하지 말고 필요한 동작을 label에 적는다 (SHOULD).
- 시퀀스의 요청·응답 표기(`->>`, `-->>`)는 flowchart의 선 규칙과 별개다.

### 라벨과 테마

- 기술 이름과 책임을 텍스트로 표시한다. 아이콘이나 색만으로 의미를 전달하지 않는다 (MUST).
- 한글·괄호·연산자가 있는 flowchart 라벨은 큰따옴표로 감싼다 (MUST).
- 라벨은 짧게 쓰고, 상세 설명은 인접 문단이나 표로 옮긴다 (SHOULD).
  줄바꿈이 꼭 필요하면 검증된 `<br/>`를 쓰며, 일반 라벨의 리터럴 `\n`은 쓰지 않는다.
- **다이어그램 배경은 흰색(`#ffffff`)으로 유지한다 (MUST).** 사용자는 주로 다크 모드에서 읽는다.
  새 다이어그램과 수정하는 기존 다이어그램에 모두 적용한다.
- 코드 블록의 첫 줄은 `flowchart LR`, `sequenceDiagram` 등 다이어그램 선언으로 시작한다 (MUST).
  일부 Zed 버전은 첫 단어로 렌더링 대상을 판별하므로 앞에 YAML frontmatter나 주석을 두지 않는다.
- 테마는 선언 다음 줄의 표준 `%%{init: {...}}%%` 지시문에 지정한다.
  `theme: base`, `darkMode: false`, `themeVariables`로 아래 팔레트의 배경·글자·선을 명시한다 (MUST).
- flowchart는 흰색 최상위 `canvas` subgraph로 감싸고, 원래 흐름 방향을 내부에도 명시한다 (MUST).
  `style canvas fill:#FFFFFF,stroke:#FFFFFF,stroke-width:0px,color:#16213E`를 적용한다.
- sequenceDiagram은 participant 선언을 흰 `box rgb(255, 255, 255)`로 묶고,
  메시지 구간도 `rect rgb(255, 255, 255)`로 감싼다 (MUST).
  actor·note·signal 색도 명시해 흰 배경에서 글자와 선이 읽히게 한다.
- ERD에도 같은 밝은 테마를 지정한다. 대상 렌더러가 배경색을 무시하는 경우에는
  카디널리티를 명시한 흰 canvas flowchart와 필드 표로 표현할 수 있다.
- 색상 설정은 문서 안의 표준 문법으로 해결하고, IDE 전용 CSS나 외부 확장 설치를 요구하지 않는다 (MUST NOT).
  렌더러가 문서 테마를 덮어쓰면 실제 앱의 결과와 제한을 따로 기록한다.
- 렌더러별 배치 차이는 허용하되, 흰 배경·밝은 채움색·진한 글자와 관계의 가독성은 유지해야 한다 (MUST).

### 컴포넌트 채움색과 대비

- 블로그의 Spend Limits·Budget Control·Rate Limit SVG에서 사용하는 밝은 팔레트를 기준으로 한다.
  색상 값은 이 문서에 유지하며, 작업자의 로컬 블로그 저장소가 없어도 적용할 수 있어야 한다.
- 흰 캔버스 위에 **밝은 파스텔 채움색 + 진한 남색 글자 + 선명한 테두리**를 사용한다 (MUST).
  검정·짙은 회색·짙은 원색 채움, 그라데이션, 반투명 채움으로 강조하지 않는다.
- 색은 역할을 구분할 때만 쓴다. 노드 순서에 따라 임의로 다른 색을 배정하지 않는다 (MUST NOT).
  같은 역할은 문서가 달라도 같은 색을 사용하고, 한 그림에 모든 색을 억지로 넣지 않는다.

| 역할 · 클래스 | 채움색 | 테두리 | 기본 글자 | 보조 글자 |
|---|---|---|---|---|
| 클라이언트 · API · 일반 처리 (`app`) | `#EFF6FF` | `#3B5BA5` | `#16213E` | `#31559F` |
| DB · 캐시 · 브로커 · 큐 (`db`) | `#F0FDF4` | `#3F8E55` | `#16213E` | `#166534` |
| 정책 · 한도 · 제어 (`policy`) | `#FBF5FF` | `#8B3DFF` | `#16213E` | `#6B21A8` |
| Worker · 정산 · 재시도 · 주석 (`worker`) | `#FFF7ED` | `#C98A2B` | `#16213E` | `#9A620A` |

- 제목·컴포넌트 이름·그룹 제목은 모두 `#16213E`로 지정한다 (MUST).
  보조 글자색은 별도 스타일이 지원될 때만 쓰고, 한 라벨 안에서 분리하기 어렵다면 전체를 남색으로 쓴다.
- 테두리색을 작은 글자색으로 재사용하지 않는다 (MUST NOT).
  특히 주황 테두리 `#C98A2B` 대신 주황 보조 글자는 더 진한 `#9A620A`를 쓴다.
- 글자와 실제 배경의 대비는 크기에 관계없이 **4.5:1 이상**을 유지한다 (MUST).
  흰 글자·옅은 회색 글자, 글자에 opacity를 적용해 흐리게 만드는 표현은 사용하지 않는다.
- 기본 글자 크기는 16px을 권장한다. 작은 글자로 그림을 압축하지 않는다 (SHOULD).
  문서 폭에 맞춘 미리보기에서 읽기 어려우면 라벨을 줄이거나 그림을 나눈다.
- 컴포넌트 테두리는 실선 1~1.5px로 표현한다 (SHOULD).
  강조는 짙은 채움 대신 명확한 이름·조건·주변 설명으로 전달한다.

### 그룹 배경과 화살표

- 일반 그룹은 `fill:#F8FAFC,stroke:#CBD5E1,stroke-width:1px,color:#16213E`를 사용한다 (MUST).
  그룹 제목도 남색으로 명시하고, 밝은 배경 위에 렌더러 기본 글자색을 그대로 두지 않는다.
- 중첩 그룹은 흰색 채움과 옅은 테두리로 구분한다 (SHOULD).
  정책 경계를 구별해야 하면 `#C4B5FD` 테두리를 쓸 수 있다. 그룹 전체를 진하게 칠하지 않는다.
- 기본 화살표는 `#3B5BA5`, 두께는 1.5px로 지정한다 (MUST).
  화살촉도 선과 같은 색이어야 하며, edge label은 흰 배경에 `#16213E` 또는 `#31559F`로 표시한다.
- 정산·복구 등 별도 흐름을 구분할 때만 주황색 선과 화살촉 `#9A620A`를 추가한다 (SHOULD).
  블로그의 주황선 `#C98A2B`보다 진하게 해 축소된 미리보기에서도 구별되게 한다.
- 오류 경로는 필요할 때만 `#B91C1C`로 표시하고 `실패`, `거부` 같은 조건을 함께 적는다.
  화살표색은 연결된 노드의 색에 따라 바꾸지 않는다 (MUST NOT).
- 호출·관계를 나타내는 선과 화살촉은 배경 대비 **3:1 이상**을 유지한다 (MUST).
  옅은 그룹 테두리는 영역 구분용이며, 호출·관계의 연결선으로 사용하지 않는다.

### Mermaid에 색상 적용

- flowchart는 기본 테마만 지정하고 끝내지 말고, 모든 노드에 역할 클래스를 명시한다 (MUST).
  `classDef`의 `fill`·`stroke`·`color`와 각 그룹의 제목색을 함께 지정한다.

```text
classDef app fill:#EFF6FF,stroke:#3B5BA5,stroke-width:1.4px,color:#16213E
classDef db fill:#F0FDF4,stroke:#3F8E55,stroke-width:1.4px,color:#16213E
classDef policy fill:#FBF5FF,stroke:#8B3DFF,stroke-width:1.4px,color:#16213E
classDef worker fill:#FFF7ED,stroke:#C98A2B,stroke-width:1.4px,color:#16213E
linkStyle default stroke:#3B5BA5,stroke-width:1.5px,color:#16213E
```

- 아래 `themeVariables`를 명시한다. 시퀀스는 participant를 연한 파랑으로 통일한다 (MUST).
  participant별 임의 색 대신 이름으로 기술·책임을 구분한다.

| 대상 | `themeVariables` 값 |
|---|---|
| 캔버스 · 화살표 라벨 배경 | `background`, `edgeLabelBackground`: `#FFFFFF` |
| 기본 글자 | `textColor`, `primaryTextColor`: `#16213E` |
| 기본 노드 | `primaryColor`: `#EFF6FF`, `primaryBorderColor`: `#3B5BA5` |
| 보조 채움색 | `secondaryColor`: `#F0FDF4`, `tertiaryColor`: `#FBF5FF` |
| 그룹 | `clusterBkg`: `#F8FAFC`, `clusterBorder`: `#CBD5E1` |
| 연결선 | `lineColor`: `#3B5BA5` |
| 시퀀스 participant | `actorBkg`: `#EFF6FF`, `actorBorder`: `#3B5BA5`, `actorTextColor`: `#16213E` |
| 시퀀스 메시지 · lifeline | `signalColor`, `actorLineColor`: `#3B5BA5`, `signalTextColor`: `#16213E` |
| 시퀀스 note | `noteBkgColor`: `#FFF7ED`, `noteBorderColor`: `#C98A2B`, `noteTextColor`: `#16213E` |
| 시퀀스 alt · opt · loop | `labelBoxBkgColor`: `#F8FAFC`, `labelBoxBorderColor`: `#CBD5E1`, `labelTextColor`, `loopTextColor`: `#16213E` |
| 시퀀스 activation | `activationBkgColor`: `#EFF6FF`, `activationBorderColor`: `#3B5BA5` |

- participant·message·note·분기 라벨·lifeline·activation을 빠짐없이 확인한다 (MUST).
- 눈에 보이지 않는 메시지를 주석으로 복제해 숨기거나, 배경만 흰색으로 만든 결과를 완료로 처리하지 않는다 (MUST NOT).
- 렌더러가 채움색과 글자색을 덮어쓰면 엔진 검사와 실제 앱 결과를 구분해서 보고한다.
  앱의 테마 강제를 우회하는 불안정한 CSS 트릭이나 별도 플러그인을 문서의 필수 조건으로 만들지 않는다 (MUST NOT).

### 산출물 검증

- 변경 범위의 모든 Markdown에서 Mermaid 블록을 추출해 파싱과 실제 렌더링을 검사한다 (MUST).
  전수 점검 요청이면 숨김 문서와 로컬 초안도 포함하고, 의존성·빌드 산출물은 제외한다.
- Mermaid CLI 검사와 함께 Zed가 사용하는 렌더러 계열에서도 검사한다 (MUST).
  최신 Mermaid CLI 통과만으로 GitHub·Zed 호환 완료라고 판단하지 않는다.
- 가능하면 GitHub 실제 미리보기와 Zed Markdown Preview도 확인한다 (SHOULD).
  엔진 검사와 실제 앱 검증을 구분하고, 검사한 도구 버전과 미검증 범위를 기록한다 (MUST).
- 밝은/어두운 테마에서 라벨 누락·겹침·잘림·과도한 폭과 높이, 잘못된 연결을 확인한다 (MUST).
- 색상 검증은 실제 문서 폭에서 캔버스·컴포넌트·그룹 제목·화살표 라벨·화살촉을 모두 확인한다 (MUST).
  소스에 색상 값이 있거나 파싱이 성공했다는 이유만으로 가독성 검증을 통과시키지 않는다.
- 문서의 상대 링크·이미지 경로·코드 펜스와 다이어그램 앞뒤 설명도 함께 확인한다 (MUST).
- 검증용 추출 파일과 렌더링 산출물은 임시 디렉터리에 둔다 (SHOULD).

## 설계 원칙 (아키텍처)
- 메시징은 Kafka 또는 MQ(RabbitMQ/Amazon MQ)를 우선한다 (SHOULD). 매니지드 SQS/SNS는 가능한 지양한다.
- AWS 다이어그램에서 큐/스트림은 Amazon MSK(Kafka)로 그린다 (SHOULD).
- 데드레터는 DLT(dead-letter topic)로 표현한다 (SHOULD).

## GitHub PR 규칙

- 에이전트는 생성하거나 수정하는 모든 PR의 assignee를 **반드시** 사용자 `@Hyune-c`로 지정해야 한다 (MUST).
- 에이전트는 Draft PR을 생성하거나 유지해서는 안 된다 (MUST NOT). PR은 **반드시** 일반 Open PR로 생성·전환해야 한다.
- PR 생성 또는 수정 뒤에는 assignee와 Draft 상태를 **반드시** 조회하여 검증해야 한다 (MUST).
- PR 본문과 제목은 변경 목적·영향·검증 방법을 포함해야 한다 (SHOULD).

## 참고

- 블로그 SVG: [Spend Limits](https://github.com/Hyune-c/Hyune-c.github.io/blob/master/public/images/blog/admission-control-spend-limits-flow.svg),
  [Budget Control](https://github.com/Hyune-c/Hyune-c.github.io/blob/master/public/images/blog/admission-control-budget-control-flow.svg),
  [Rate Limit](https://github.com/Hyune-c/Hyune-c.github.io/blob/master/public/images/blog/admission-control-rate-limit-flow.svg).
  반영 범위: 흰 캔버스, 역할별 파스텔 채움, 남색 제목, 보조 글자색, 그룹과 연결선의 시각적 구분.
  아이콘·SVG 전용 배치는 이식하지 않으며, Mermaid 기본 도형과 위 색상 규칙으로 표현한다.
  작은 화살표의 대비를 높이기 위해 주황 연결선은 원본보다 진하게 조정한다.
