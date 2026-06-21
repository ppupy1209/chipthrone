# 👑 chipthrone

> 국장 반도체 왕좌 대결 — 삼성전자 vs SK하이닉스, 지금 이 순간 1위는 누구?

두 반도체 거인의 **주가**와 **시가총액**을 실시간으로 비교하고, "언제 순위가 뒤집히는가"를 보여주는 대시보드입니다.

- **정규장(09:00~15:30)** · **NXT 애프터마켓(15:40~20:00)** — 실제 시세
- **야간 · 주말** — Hyperliquid HIP-3 perp(무기한 선물) 기반 **추정 시세**
- **시총 실시간 비교** + **역전 계산기 / D-게이지** — "하이닉스가 +X% 오르면 왕좌 교체"

🔗 https://chipthrone.yeonwoo.dev (예정)

## 왜 만들었나

2026년 상반기, KOSPI 9,000 시대를 맞아 삼성전자와 SK하이닉스의 시가총액 1·2위 자리가 엎치락뒤치락하는 것이 국장 최대 화두다.
"지금 누가 1위인가"를 한 화면에서, 장이 닫힌 시간에도 추정치로 확인할 수 있게 만들었다.

## 아키텍처

```
GitHub(public) ──Actions(build/test)──▶ EC2 t3.micro (Spring Boot · Nginx)
                                              └▶ RDS MySQL db.t3.micro
프론트엔드 ──▶ Vercel(무료, 정적) ──▶ chipthrone.yeonwoo.dev
외부 데이터  정규장: 한국투자증권 KIS API · 야간: Hyperliquid perp × USD/KRW
```

자세한 내용은 [docs/01-architecture.md](docs/01-architecture.md) 참고.

## 기술 스택

| 영역 | 스택 |
|---|---|
| 백엔드 | Java 21, Spring Boot 3, MySQL 8 (외래키·JPA 연관관계 미사용) |
| 캐시/실시간 | Caffeine, SSE fan-out, Resilience4j |
| 프론트엔드 | React, Vite, TypeScript, Tailwind, lightweight-charts |
| 인프라/CICD | AWS EC2·RDS(프리티어), GitHub Actions, Docker, Nginx |

## 핵심 엔지니어링 과제

> 프리티어 EC2 1대로, 동시 접속 N명이 붙어도 외부 시세 API는 1초에 1번만 호출하기

스케줄러 단일 폴링 → 캐시 → SSE fan-out → 서킷브레이커/폴백. 자세한 기록은 블로그에 연재 예정.

## 프로젝트 구조

```
chipthrone/
├── backend/    Spring Boot API
├── frontend/   React 대시보드
├── infra/      배포 스크립트, Nginx 설정
├── docs/       설계 문서
└── .github/    CI/CD 워크플로
```

## 로컬 실행

```bash
# 백엔드
cd backend && ./gradlew bootRun

# 프론트엔드
cd frontend && npm install && npm run dev
```

---
투자 참고용 서비스이며, 추정 시세는 공식 거래소 가격이 아닙니다.
