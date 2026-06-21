# chipthrone-api

DB 없이 즉시 실행 가능한 Spring Boot 워킹 스켈레톤이다.

## 실행

```bash
./gradlew bootRun
```

## 확인

- `GET /api/health`
- `GET /actuator/health`

## 운영 원칙

현재 단계에는 DB/JPA/MySQL 의존성이 없다. 이후 DB를 도입하더라도 운영 관행상 DDL 외래키와 JPA 연관관계 매핑(`@ManyToOne` 등)은 사용하지 않고, 참조는 ID 값으로만 처리한다.
