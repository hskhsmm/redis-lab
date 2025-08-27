package io.wte.redis_lab.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

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
