package dev.yeonwoo.chipthrone.quote.web;

import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import dev.yeonwoo.chipthrone.quote.config.DemandProperties;
import dev.yeonwoo.chipthrone.quote.model.QuoteSnapshot;
import dev.yeonwoo.chipthrone.quote.service.AssetCatalog;
import dev.yeonwoo.chipthrone.quote.service.QuoteService;
import dev.yeonwoo.chipthrone.quote.service.QuotePollingWorker;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.Optional;

@RestController
@RequestMapping("/api")
public class QuoteController {

    private final QuoteService quoteService;
    private final QuoteBroadcaster broadcaster;
    private final AssetCatalog catalog;
    private final DemandProperties demandProperties;
    private final Optional<QuotePollingWorker> pollingWorker;

    public QuoteController(
            QuoteService quoteService,
            QuoteBroadcaster broadcaster,
            AssetCatalog catalog,
            DemandProperties demandProperties,
            Optional<QuotePollingWorker> pollingWorker
    ) {
        this.quoteService = quoteService;
        this.broadcaster = broadcaster;
        this.catalog = catalog;
        this.demandProperties = demandProperties;
        this.pollingWorker = pollingWorker;
    }

    @GetMapping("/assets")
    public List<SupportedAsset> assets() {
        return catalog.all().stream()
                .map(asset -> new SupportedAsset(asset.code(), asset.name(), asset.market().name()))
                .toList();
    }

    @GetMapping("/quotes")
    public ResponseEntity<QuoteSnapshot> quotes(
            @RequestParam(name = "symbols", required = false) String symbolsParameter
    ) {
        if (symbolsParameter == null) {
            return quoteService.currentSnapshot()
                    .map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.status(503).build());
        }
        return quoteService.currentSnapshot(parseSymbols(symbolsParameter))
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(503).build());
    }

    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestParam("symbols") String symbolsParameter) {
        Set<String> symbols = parseSymbols(symbolsParameter);
        SseEmitter emitter = broadcaster.subscribe(
                symbols,
                quoteService.currentSnapshot(symbols).orElse(null)
        );
        if (!quoteService.hasFresh(symbols, Duration.ofMillis(demandProperties.staleAfterMs()))) {
            pollingWorker.ifPresentOrElse(
                    QuotePollingWorker::requestImmediateRefresh,
                    () -> quoteService.refresh(symbols)
            );
        }
        return emitter;
    }

    private Set<String> parseSymbols(String parameter) {
        LinkedHashSet<String> symbols = new LinkedHashSet<>(Arrays.stream(parameter.split(",", -1))
                .map(String::trim)
                .filter(symbol -> !symbol.isEmpty())
                .toList());
        if (symbols.isEmpty()) {
            throw badRequest("At least one symbol is required");
        }
        if (symbols.size() > demandProperties.maxSymbolsPerConnection()) {
            throw badRequest("Too many symbols; maximum is " + demandProperties.maxSymbolsPerConnection());
        }
        try {
            catalog.requireAssets(symbols);
        } catch (IllegalArgumentException ex) {
            throw badRequest(ex.getMessage());
        }
        return symbols;
    }

    private ResponseStatusException badRequest(String reason) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
    }

    public record SupportedAsset(String code, String name, String market) {
    }
}
