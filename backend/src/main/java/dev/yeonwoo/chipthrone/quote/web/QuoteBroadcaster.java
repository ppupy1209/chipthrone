package dev.yeonwoo.chipthrone.quote.web;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import dev.yeonwoo.chipthrone.quote.model.QuoteSnapshot;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
public class QuoteBroadcaster {

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public SseEmitter subscribe(QuoteSnapshot initialSnapshot) {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(error -> emitters.remove(emitter));

        if (initialSnapshot != null) {
            send(emitter, initialSnapshot);
        }
        return emitter;
    }

    public void publish(QuoteSnapshot snapshot) {
        for (SseEmitter emitter : emitters) {
            send(emitter, snapshot);
        }
    }

    private void send(SseEmitter emitter, QuoteSnapshot snapshot) {
        try {
            emitter.send(SseEmitter.event()
                    .name("quotes")
                    .data(snapshot));
        } catch (IOException | IllegalStateException ex) {
            emitters.remove(emitter);
            emitter.complete();
        }
    }
}
