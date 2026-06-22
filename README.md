# 👑 chipthrone

> 국장 반도체 왕좌 대결 — 삼성전자 vs SK하이닉스, 지금 이 순간 1위는 누구?

두 반도체 거인의 **주가**와 **시가총액**을 실시간으로 비교하고, "언제 순위가 뒤집히는가"를 보여주는 대시보드입니다.

- **프리마켓(08:00~09:00) · 정규장(09:00~15:30) · 애프터마켓/NXT(15:40~20:00)** — KIS 실시세
- **야간 · 주말 · 공휴일** — Hyperliquid HIP-3 perp(무기한 선물) × 환율 기반 **추정 시세**
- **시총 실시간 비교 + 역전 계산기(근접도)** — "하이닉스가 +X% 오르면 왕좌 교체"
- **증권사 투자의견 컨센서스** · **라이트/다크 테마**

🔗 https://www.chipthrone.com

## 왜 만들었나

2026년 상반기, KOSPI 9,000 시대를 맞아 삼성전자와 SK하이닉스의 시가총액 1·2위 자리가 엎치락뒤치락하는 것이 국장 최대 화두다.
"지금 누가 1위인가"를 한 화면에서, 장이 닫힌 시간에도 추정치로 확인할 수 있게 만들었다.

## 아키텍처

```
GitHub(public) ─Actions(build/test)─▶ GHCR(이미지) ─watchtower(60s 자동 pull)─▶ EC2 t3.micro
                                                                          (Spring Boot · Nginx · SSL)
프론트엔드 ─▶ Vercel ─▶ www.chipthrone.com        백엔드 ─▶ api.chipthrone.com
외부데이터: 정규장/NXT=KIS · 야간/주말/공휴일=Hyperliquid perp×환율 · 투자의견=KIS 종목투자의견
```

자세한 내용은 [docs/01-architecture.md](docs/01-architecture.md) 참고.

## 기술 스택

| 영역 | 스택 |
|---|---|
| 백엔드 | Java 21, Spring Boot 3 (인메모리 캐시, 외래키·JPA 연관관계 미사용 원칙) |
| 실시간 | 단일 폴링 → 인메모리 캐시 → SSE fan-out, 외부 실패 시 마지막값 폴백 |
| 프론트엔드 | React, Vite, TypeScript, Tailwind, 라이트/다크 테마 |
| 인프라/CICD | AWS EC2(프리티어), GitHub Actions, GHCR, Docker, watchtower, Nginx + certbot |

> 현재 DB는 사용하지 않는다(인메모리). 과거 시총·역전 기록 영속화 시 RDS(MySQL)를 추가할 예정.

## 핵심 엔지니어링 과제

> 프리티어 EC2 1대로, 동시 접속 N명이 붙어도 외부 시세 API는 폴링당 1번만 호출하기

스케줄러 단일 폴링 → 인메모리 캐시 → SSE fan-out → 마지막값 폴백. KIS 종가는 1회 성공 후 캐시 고정(거래일 바뀔 때만 재조회). 자세한 기록은 블로그에 연재 예정.

## API

| 엔드포인트 | 설명 |
|---|---|
| `GET /api/health` | 헬스체크 |
| `GET /api/quotes` | 최신 시세 스냅샷(JSON) 1회 |
| `GET /api/stream` | SSE — 갱신마다 스냅샷 push(fan-out) |
| `GET /api/opinions` | 증권사 투자의견 컨센서스(KIS) |

응답 형식은 [docs/04-api.md](docs/04-api.md) 참고.

## 프로젝트 구조

```
chipthrone/
├── backend/    Spring Boot API (quote: web/service/client/model/config)
├── frontend/   React 대시보드
├── infra/      EC2 부트스트랩, Nginx 설정
├── docs/       설계 문서
└── .github/    CI/CD 워크플로
```

## 로컬 실행

```bash
# 백엔드 (KIS 키 없이도 동작 — 야간 추정 모드로 폴백)
cd backend && ./gradlew bootRun

# 프론트엔드
cd frontend && npm install && npm run dev
```

KIS 정규장/투자의견 연동은 환경변수 `KIS_APP_KEY`, `KIS_APP_SECRET` 필요(없으면 Hyperliquid 추정만 사용).

---
투자 참고용 서비스이며, 추정 시세는 공식 거래소 가격이 아닙니다.
