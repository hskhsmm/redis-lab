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
