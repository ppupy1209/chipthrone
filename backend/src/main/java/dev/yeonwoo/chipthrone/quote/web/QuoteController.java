package dev.yeonwoo.chipthrone.quote.web;

import dev.yeonwoo.chipthrone.quote.model.QuoteSnapshot;
import dev.yeonwoo.chipthrone.quote.service.QuoteService;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api")
public class QuoteController {

    private final QuoteService quoteService;
    private final QuoteBroadcaster broadcaster;

    public QuoteController(QuoteService quoteService, QuoteBroadcaster broadcaster) {
        this.quoteService = quoteService;
        this.broadcaster = broadcaster;
    }

    @GetMapping("/quotes")
    public ResponseEntity<QuoteSnapshot> quotes() {
        return quoteService.currentSnapshot()
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(503).build());
    }

    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        return broadcaster.subscribe(quoteService.currentSnapshot().orElse(null));
    }
}
