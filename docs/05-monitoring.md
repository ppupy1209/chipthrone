# 05. 모니터링 — Slack 알림

서버 상태와 에러를 Slack으로 받기 위한 구성. **두 층**으로 나눈다.

| 층 | 보는 것 | 어디서 도나 | 비용 |
|----|---------|-------------|------|
| ① 외부 liveness 모니터 | 서버가 살아있는지(완전 다운) | GitHub Actions 러너 (EC2 밖) | 무료, EC2 자원 0 |
| ② 인앱 알림 | 애플리케이션 레벨 에러·배포 | Spring Boot 앱 안 | HTTP POST 한 방, 사실상 0 |

> 왜 둘? 앱이 완전히 죽으면 **자기 죽음을 Slack에 못 알린다.** liveness는 반드시 EC2 바깥에서 봐야 하고, 반대로 "KIS 폴백 중" 같은 앱 내부 사건은 외부 핑으로는 안 보인다.

---

## 0. Slack 가입 & 워크스페이스 만들기 (아직 없다면)

1. https://slack.com/get-started 접속 → 이메일로 가입(또는 Google 로그인).
2. **워크스페이스 생성**: "Create a Workspace" → 워크스페이스 이름(예: `chipthrone`) → 채널 이름(예: `#alerts`).
3. (선택) 데스크톱/모바일 앱 다운로드: https://slack.com/downloads — 브라우저만으로도 충분하다.

## 1. Incoming Webhook URL 발급

1. https://api.slack.com/apps → **Create New App** → **From scratch**.
2. App 이름(예: `chipthrone-alerts`) + 위에서 만든 워크스페이스 선택 → **Create App**.
3. 좌측 메뉴 **Incoming Webhooks** → 토글 **On**.
4. 하단 **Add New Webhook to Workspace** → 알림 받을 채널(`#alerts`) 선택 → **Allow**.
5. 생성된 **Webhook URL** 복사 (`https://hooks.slack.com/services/T.../B.../xxxx`).

> ⚠️ 이 URL은 **비밀값**이다. 아는 사람은 누구나 채널에 글을 쏠 수 있으니 레포·이미지·문서에 절대 커밋하지 않는다. (KIS 키와 같은 취급)

## 2. 외부 liveness 모니터 (이미 구성됨)

`.github/workflows/uptime-monitor.yml` — 5분 주기로 `https://api.chipthrone.com/api/health`를 찌르고, **3회 재시도 후에도 실패하면** Slack으로 알린다. 일시적 깜빡임은 재시도로 걸러 오탐을 줄였다.

**설정할 것 (GitHub 레포 시크릿):**
1. GitHub 레포 → **Settings → Secrets and variables → Actions → New repository secret**
2. Name: `SLACK_WEBHOOK_URL`, Value: 1번에서 복사한 URL → **Add secret**

**동작 확인:**
- 스케줄 워크플로는 **기본 브랜치(main)에서만** 돈다 → 이 PR 머지 후부터 5분 주기 실행.
- 머지 후 즉시 테스트: Actions 탭 → **Uptime Monitor → Run workflow**(수동 실행). 서버가 정상이면 알림 없음(=정상), 다운이면 `#alerts`에 메시지.

> 더 촘촘한 주기(1분)나 다중 리전 체크가 필요하면 UptimeRobot·Better Stack 무료 티어가 대안. 지금 규모엔 GitHub Actions로 충분.

## 3. 인앱 알림 — 백엔드 작업 (Codex 담당)

> chipthrone 분담상 **백엔드 코드는 Codex**가 구현한다. 아래는 그 작업 요청 스펙.

### 목표
앱 내부에서 의미 있는 사건이 생기면 Slack Webhook으로 알린다. **단, 알림 폭주를 반드시 막는다**(폴링이 3초 주기라 그대로 쏘면 노이즈 지옥).

### 알릴 이벤트
1. **배포/재시작** — `ApplicationReadyEvent`에서 1회. 예: `:white_check_mark: chipthrone-api vX.Y.Z 기동`. watchtower가 조용히 재배포하므로 의도치 않은 재시작·크래시루프를 드러내는 용도.
2. **시세 소스 장애** — `QuoteService.refresh()`의 `marketDataClient`/`snapshotFactory` 호출이 **N회 연속 실패**하면 1회 알림(현재는 `log.warn`만 남기고 마지막 스냅샷 동결). 복구되면 회복 알림 1회.
3. **KIS 지속 실패** — 정규장(`MarketMode`가 실거래 시간)인데 KIS 현재가/토큰이 N회 연속 실패해 추정치로 폴백 중이면 1회 알림. 복구 시 1회.

### 폭주 방지 규칙 (필수)
- **상태 전이에서만**: `정상→실패` 1회, `실패→복구` 1회. 매 실패마다 X.
- **연속 실패 임계값** `N`(예: 5회 ≈ 15초)과 **쿨다운**(같은 종류 알림 최소 10분 간격) 설정값화.
- 모든 임계값·쿨다운은 `application.yml`(`chipthrone.alert.*`)로 노출.

### 시크릿/설정
- Webhook URL은 환경변수 `SLACK_WEBHOOK_URL`로만 주입. EC2 `~/chipthrone.env`(`--env-file`)에 추가 — KIS 키와 동일 패턴. **레포·이미지엔 넣지 않는다.**
- `SLACK_WEBHOOK_URL` 미설정이면 알림 비활성(로컬·CI에서 조용히 끔). 키 없으면 폴백하는 KIS와 같은 결.
- `application.yml` 예시:
  ```yaml
  chipthrone:
    alert:
      slack-webhook-url: ${SLACK_WEBHOOK_URL:}
      enabled: ${SLACK_WEBHOOK_URL:+true} # URL 있을 때만 on
      consecutive-failure-threshold: 5
      cooldown-minutes: 10
  ```

### 구현 메모
- Slack 전송은 단순 `POST {"text": "..."}`. 별도 SDK 불필요(`RestClient`로 충분).
- 전송 실패가 앱 흐름을 막지 않도록 예외는 삼키고 `log.warn`만(시세 폴백 철학과 동일).
- 프리티어 1GB라 별도 스레드풀·큐 도입 없이 폴링 스레드에서 가볍게 보내되, 쿨다운으로 빈도만 제어.
