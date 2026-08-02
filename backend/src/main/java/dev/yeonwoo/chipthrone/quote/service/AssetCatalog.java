package dev.yeonwoo.chipthrone.quote.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.yeonwoo.chipthrone.quote.config.QuoteProperties;

import org.springframework.stereotype.Component;

@Component
public class AssetCatalog {

    private final List<QuoteProperties.Asset> assets;
    private final Map<String, QuoteProperties.Asset> byCode;

    public AssetCatalog(QuoteProperties properties) {
        this.assets = List.copyOf(properties.assets());
        Map<String, QuoteProperties.Asset> indexed = new LinkedHashMap<>();
        assets.forEach(asset -> indexed.put(asset.code(), asset));
        this.byCode = Map.copyOf(indexed);
    }

    public List<QuoteProperties.Asset> all() {
        return assets;
    }

    public Set<String> allCodes() {
        return byCode.keySet();
    }

    public List<QuoteProperties.Asset> requireAssets(Set<String> codes) {
        List<String> unsupported = codes.stream().filter(code -> !byCode.containsKey(code)).toList();
        if (!unsupported.isEmpty()) {
            throw new IllegalArgumentException("Unsupported symbols: " + String.join(",", unsupported));
        }
        return assets.stream().filter(asset -> codes.contains(asset.code())).toList();
    }
}
