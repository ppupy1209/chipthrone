package dev.yeonwoo.chipthrone.controller;

import java.time.OffsetDateTime;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class HealthController {

    @GetMapping("/health")
    public HealthResponse health() {
        return new HealthResponse(
                "UP",
                "chipthrone-api",
                "0.0.1",
                OffsetDateTime.now()
        );
    }

    public record HealthResponse(
            String status,
            String service,
            String version,
            OffsetDateTime serverTime
    ) {
    }
}
