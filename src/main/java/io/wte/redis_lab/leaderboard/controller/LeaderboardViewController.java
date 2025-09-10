package io.wte.redis_lab.leaderboard.controller;

import io.wte.redis_lab.leaderboard.dto.LeaderboardEntry;
import io.wte.redis_lab.leaderboard.service.LeaderboardService;
import io.wte.redis_lab.leaderboard.service.LeaderboardKeyFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Controller
@RequestMapping("/leaderboard")
@RequiredArgsConstructor
public class LeaderboardViewController {

    private final LeaderboardService leaderboardService;
    private final LeaderboardKeyFactory keyFactory;

    @GetMapping("/view")
    public String leaderboardView(
            @RequestParam(defaultValue = "weekly") String scope,
            @RequestParam(defaultValue = "10") int limit,
            Model model) {

        try {
            if (limit <= 0 || limit > 50) {
                limit = 10;
            }

            String leaderboardKey = getLeaderboardKey(scope, LocalDate.now());
            List<LeaderboardService.ScoredValue> scoredValues =
                    leaderboardService.getTopN(leaderboardKey, limit);

            // 순위를 포함하여 응답 생성
            AtomicLong rankCounter = new AtomicLong(1); // UI에서는 1부터 시작
            List<LeaderboardEntry> entries = scoredValues.stream()
                    .map(sv -> new LeaderboardEntry(
                            rankCounter.getAndIncrement(),
                            sv.userId(),
                            sv.score()))
                    .toList();

            model.addAttribute("entries", entries);
            model.addAttribute("currentScope", scope);
            model.addAttribute("totalUsers", leaderboardService.getTotalMembers(leaderboardKey));

            log.info("리더보드 뷰 로드 완료 - 스코프: {}, 엔트리 수: {}, 총 사용자: {}", 
                    scope, entries.size(), leaderboardService.getTotalMembers(leaderboardKey));
            
            return "leaderboard/index";
        } catch (Exception e) {
            log.error("리더보드 뷰 로드 중 오류 발생", e);
            model.addAttribute("entries", List.of());
            model.addAttribute("currentScope", scope);
            model.addAttribute("totalUsers", 0L);
            return "leaderboard/index";
        }
    }

    /**
     * 스코프와 날짜를 기반으로 적절한 리더보드 키를 반환한다.
     *
     * @param scope 리더보드 범위 (all, weekly, daily)
     * @param date 기준 날짜
     * @return Redis 리더보드 키
     * @throws IllegalArgumentException 유효하지 않은 스코프인 경우
     */
    private String getLeaderboardKey(String scope, LocalDate date) {
        return switch (scope.toLowerCase()) {
            case "all" -> keyFactory.getAllTimeKey();
            case "weekly" -> keyFactory.getWeeklyKey(date);
            case "daily" -> keyFactory.getDailyKey(date);
            default -> throw new IllegalArgumentException("유효하지 않은 스코프: " + scope);
        };
    }
}