package io.wte.redis_lab.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 실습별 페이지. 이동만 담당하는 컨트롤러
 */
@Controller
public class PageController {

    @GetMapping("/")
    public String home() {
        return "index";
    }

    @GetMapping("/idempotency")
    public String idempotency() {
        return "idempotency/index";
    }
}
