package dev.yeonwoo.chipthrone.quote;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "chipthrone.quote", name = "polling-enabled", havingValue = "true", matchIfMissing = true)
public class QuotePollingWorker {

    private final QuoteService quoteService;

    public QuotePollingWorker(QuoteService quoteService) {
        this.quoteService = quoteService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void pollOnStartup() {
        quoteService.refresh();
    }

    @Scheduled(fixedDelayString = "${chipthrone.quote.poll-delay-ms:3000}")
    public void poll() {
        quoteService.refresh();
    }
}
