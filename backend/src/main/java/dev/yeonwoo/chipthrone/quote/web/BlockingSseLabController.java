package dev.yeonwoo.chipthrone.quote.web;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.concurrent.TimeUnit;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 동기식 장기 연결이 Tomcat worker를 고갈시키는 상황을 로컬에서만 재현한다.
 * 운영 프로필에서는 Bean 자체가 등록되지 않는다.
 */
@Profile("sse-thread-lab")
@RestController
@RequestMapping("/__lab")
public class BlockingSseLabController {

    private static final long HEARTBEAT_INTERVAL_MS = 250;
    private static final long MAX_HOLD_MS = 60_000;

    @GetMapping(path = "/blocking-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public void blockingStream(
            HttpServletResponse response,
            @RequestParam(defaultValue = "20000") long holdMs
    ) throws IOException {
        long boundedHoldMs = Math.max(HEARTBEAT_INTERVAL_MS, Math.min(holdMs, MAX_HOLD_MS));
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-cache");

        PrintWriter writer = response.getWriter();
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(boundedHoldMs);
        int heartbeat = 0;
        while (System.nanoTime() < deadline) {
            writer.printf(": blocking-worker-%d%n%n", heartbeat++);
            writer.flush();
            if (writer.checkError()) {
                return;
            }
            try {
                Thread.sleep(HEARTBEAT_INTERVAL_MS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}
