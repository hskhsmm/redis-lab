# Redis Lab — Spring Boot + Thymeleaf 미니 프로젝트 모음

**하루(또는 반나절) 안에 끝내는 Redis 실습 컬렉션.**  
한 개의 Spring Boot 앱에서 기능별 모듈로 분리해 여러 개 실습을 빠르게 쌓습니다.

- **Backend**: Java 21, Spring Boot 3.5.5, Spring Data Redis(Lettuce), Thymeleaf, Lombok, Validation
- **Redis**: Docker 로컬(`redis:7`)
- **Front**: Thymeleaf + JS(fetch)

---

## Quick Start

```bash
# 1) Redis 실행
docker run -d --name redis -p 6379:6379 redis:7

# 2) 애플리케이션 실행 (프로젝트 루트)
./gradlew bootRun   # (Windows: gradlew.bat bootRun)

# 3) 접속
open http://localhost:8080   # 브라우저에서 실습별 페이지 이동
````


### 디렉터리 구조도
````
src/
└─ main/
   ├─ java/
   │  └─ io/wte/redis_lab/
   │     ├─ RedisLabApplication.java
   │     ├─ common/
   │     │  ├─ config/        # 공통 설정(필요 시)
   │     │  └─ util/          # WeekKeyUtil 등
   │     ├─ idempotency/
   │     │  ├─ controller/    # IdempotentApiController
   │     │  └─ service/       # OrderService
   │     ├─ leaderboard/
   │     │  ├─ controller/    # WeeklyLeaderboardController
   │     │  └─ service/       # WeeklyLeaderboardService
   │     ├─ rate_limit/       # (추가 예정)
   │     ├─ presence/         # (추가 예정)
   │     ├─ delayed_queue/    # (추가 예정)
   │     ├─ hll/              # (추가 예정)
   │     ├─ cache_proxy/      # (추가 예정)
   │     ├─ pubsub/           # (추가 예정)
   │     ├─ geo/              # (추가 예정)
   │     └─ web/
   │        └─ PageController.java  # /, /leaderboard, /idempotency 등 라우팅
   └─ resources/
      ├─ application.yml
      └─ templates/
         ├─ index.html
         ├─ idempotency/
         │  └─ index.html
         └─ leaderboard/
            └─ index.html
````

---

## Features (문제 + 핵심 아이디어)

### Idempotency (중복 생성 방지)

* **문제**: 네트워크 재시도/더블클릭으로 같은 요청이 여러 번 들어와도 리소스는 1번만 생성돼야 함.
* **핵심 아이디어**: Redis `SET NX EX`로 최초 요청만 성공하도록 락을 잡고, 결과를 Redis에 저장해 동일 응답 반환.

---

### Weekly Leaderboard (주간 러닝 랭킹)

* **문제**: 매 요청마다 누적 거리 합산과 상위 N 순위를 빠르게 제공해야 함.
* **핵심 아이디어**: Redis Sorted Set(ZSET)으로 점수 누적(`ZINCRBY`) 및 정렬 조회(`ZREVRANGE`, `ZREVRANK`) 처리.

---

### Rate Limiter (레이트 리밋)

* **문제**: API 남용/봇 트래픽을 제어해야 함.
* **핵심 아이디어**: `INCR+EXPIRE`(고정 윈도우) 또는 `ZADD+ZCOUNT`(슬라이딩 윈도우)로 요청 횟수 제한.

---

### Presence / Last Seen (온라인 상태)

* **문제**: 사용자의 온라인 여부 및 마지막 활동 시간을 빠르게 표시해야 함.
* **핵심 아이디어**: `SETEX last_seen:{userId}`와 `TTL`로 온라인/오프라인 판정.

---

### Delayed Job Queue (지연 작업 큐)

* **문제**: “N초 뒤 실행” 작업을 예약 실행해야 함.
* **핵심 아이디어**: `ZADD scheduled score=executeAt` + `ZRANGEBYSCORE`로 만기된 작업만 꺼내 실행.

---

### DAU/MAU Counter (HyperLogLog)

* **문제**: 매일/매월 고유 사용자 수를 저비용으로 집계해야 함.
* **핵심 아이디어**: `PFADD`로 사용자 기록, `PFCOUNT`로 집계.

---

### Cache Proxy (외부 API 캐시)

* **문제**: 외부 API 호출 비용/지연을 줄이고 캐싱이 필요함.
* **핵심 아이디어**: `SETEX cache:{urlHash}`로 TTL 동안 캐싱 후 재사용.

---

### Pub/Sub Fanout (채팅 팬아웃)

* **문제**: 다중 서버 환경에서 채팅 메시지를 동기화해야 함.
* **핵심 아이디어**: `PUBLISH`/`SUBSCRIBE` 채널로 메시지를 팬아웃.

---

### GEO 검색 (주변 러닝 스팟)

* **문제**: 특정 좌표 반경 내 장소를 찾아야 함.
* **핵심 아이디어**: `GEOADD`, `GEOSEARCH BYRADIUS`로 반경 검색.

---

### Token Blacklist (로그아웃 무효화)

* **문제**: JWT 토큰이 만료 전이라도 서버에서 무효화해야 함.
* **핵심 아이디어**: `SETEX bl:token:{jti}`를 저장해 TTL 동안 차단.
