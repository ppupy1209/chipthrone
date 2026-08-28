# CHIPTHRONE

삼성전자와 SK하이닉스가 시가총액 1위를 겨루는 모습을 한 화면에서 볼 수 있는 대시보드입니다. 확정 시세와 해외 파생시장의 추정 시세를 비교하고, 관심 있는 미국 반도체와 AI 종목도 함께 살펴볼 수 있습니다.

[chipthrone.com](https://chipthrone.com)

![CHIPTHRONE 전체 화면](https://github.com/user-attachments/assets/e161b560-776b-4413-828a-ddc8b71c14d4)

## 주요 기능

### 시가총액 비교

금융위원회 일별 주식시세정보의 최근 종가와 상장주식 수로 삼성전자와 SK하이닉스의 시가총액을 비교합니다. 현재 1위와 두 회사의 비중, 왕좌가 바뀌려면 주가가 얼마나 움직여야 하는지 함께 보여줍니다.

### 해외 추정 시세

Hyperliquid의 해외 파생 가격을 Pyth Network의 USD/KRW 환율로 원화 환산한 추정값입니다. 확정 종가와 추정 가격, 추정 시가총액, 종가 대비 등락률을 나란히 볼 수 있습니다. 실제 주식 체결가와는 다를 수 있습니다.

### 미국 반도체와 AI

반도체와 M7 목록에서 관심 종목을 골라 볼 수 있습니다. 선택한 종목의 추정 가격과 원화 환산 가격, 추정 시가총액, 종가 대비 등락률이 카드로 표시됩니다. 선택 결과는 브라우저에 저장됩니다.

## 아키텍처

<img width="896" height="543" alt="CHIPTHRONE 배포 및 운영 아키텍처" src="https://github.com/user-attachments/assets/6b5a74d0-9151-4ed3-ba9b-8933f7df6d91" />

GitHub Actions가 테스트와 빌드를 수행해 이미지를 GHCR에 저장하면, EC2 내부의 Watchtower가 새 이미지를 감지해 컨테이너를 교체합니다. 서비스 외부 응답과 애플리케이션 내부 이벤트는 서로 다른 층에서 감시하고 Slack으로 알립니다.

## CI/CD

PR과 `main` 반영 시 인프라 스크립트, 백엔드 빌드·테스트, 프론트엔드 빌드를 검증합니다. `main`의 백엔드 변경은 Docker 이미지로 만들어 `latest`와 커밋 SHA 태그를 붙여 GHCR에 저장합니다.

- [CI 워크플로 — 인프라·백엔드·프론트엔드 검증 →](https://github.com/ppupy1209/chipthrone/blob/main/.github/workflows/ci.yml)
- [배포 워크플로 — Docker 이미지 빌드와 GHCR 저장 →](https://github.com/ppupy1209/chipthrone/blob/main/.github/workflows/deploy-backend.yml)

## 문제 해결: SSH 접속 없는 자동 배포

GitHub Actions 러너가 EC2에 SSH로 접속해 재배포하는 단계에서 `i/o timeout`이 발생했습니다. 22번 포트는 개인 IP만 허용하고 있었지만, GitHub Actions 러너의 IP는 실행할 때마다 달라 고정된 허용 목록으로 관리할 수 없었습니다.

22번 포트를 전체 공개하는 대신 배포 방향을 push에서 pull로 바꿨습니다. GitHub Actions는 GHCR에 이미지를 저장하는 역할까지만 맡고, EC2의 Watchtower가 60초마다 새 이미지를 확인해 컨테이너를 교체하도록 구성했습니다.

그 결과 22번 포트는 개인 IP만 허용한 상태로 유지하면서도 `main` 반영 후 최대 60초 안에 자동 배포할 수 있게 되었고, 배포 경로에서 SSH 키와 러너 IP를 관리할 필요도 없어졌습니다.

## 로컬 실행

Git과 Docker Desktop을 설치한 뒤 Docker가 실행 중인지 확인해 주세요.

### 1. 저장소 내려받기

```bash
git clone https://github.com/ppupy1209/chipthrone.git
cd chipthrone
```

### 2. 환경 변수 준비하기

환율과 추정 시세는 별도 키 없이 바로 볼 수 있습니다. 확정 종가와 시가총액도 확인하려면 `.env.example`을 `.env`로 복사합니다. 인증키를 사용하지 않는다면 이 단계는 건너뛰어도 됩니다.

Windows PowerShell에서는 다음 명령을 사용합니다.

```powershell
Copy-Item .env.example .env
```

macOS와 Linux에서는 다음 명령을 사용합니다.

```bash
cp .env.example .env
```

복사한 `.env` 파일을 열고 공공데이터포털에서 발급받은 금융위원회 주식시세정보 인증키를 입력합니다.

```dotenv
PUBLIC_DATA_SERVICE_KEY=발급받은_인증키
```

### 3. 실행하기

```bash
docker compose up --build -d
```

처음 실행할 때는 이미지를 빌드하므로 몇 분 정도 걸릴 수 있습니다. 실행 상태는 다음 명령으로 확인합니다.

```bash
docker compose ps
```

### 4. 접속하기

- 웹 화면: `http://localhost:5173`
- API: `http://localhost:8080`

인증키가 없어도 확정값 영역을 뺀 나머지 화면은 그대로 작동합니다.

### 5. 종료하기

```bash
docker compose down
```

## 이용 안내

CHIPTHRONE은 시세 비교를 위한 정보 서비스이며 투자 권유나 자문을 제공하지 않습니다.

