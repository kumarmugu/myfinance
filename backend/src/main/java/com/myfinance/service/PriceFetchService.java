package com.myfinance.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myfinance.model.Asset;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

/**
 * Fetches the latest market price for an asset from a public provider (Stooq by default, or
 * Yahoo Finance's unofficial quote endpoint). This is a best-effort helper for the Assets
 * "Update Price" feature — many instruments (e.g. Sri Lanka / CSE, niche tickers) are NOT
 * covered by these free feeds, in which case an empty Optional is returned and the caller
 * leaves the existing price untouched so the user can maintain it manually.
 *
 * The returned price is in the provider's quote currency for that symbol; the caller is
 * responsible for deciding whether it matches the asset's currency.
 */
@Slf4j
@Service
public class PriceFetchService {

    @Value("${app.price.provider:stooq}")
    private String provider;

    @Value("${app.price.enabled:true}")
    private boolean enabled;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private final ObjectMapper mapper = new ObjectMapper();

    public boolean isEnabled() { return enabled; }
    public String getProvider() { return provider; }

    /**
     * Best-effort latest price for the asset. Empty when disabled, the symbol is unknown/unquoted,
     * or the provider request fails. Never throws.
     */
    public Optional<BigDecimal> fetchLatestPrice(Asset asset) {
        if (!enabled) return Optional.empty();
        if (asset == null || asset.getSymbol() == null || asset.getSymbol().isBlank()) return Optional.empty();
        try {
            String p = provider == null ? "stooq" : provider.trim().toLowerCase();
            return switch (p) {
                case "yahoo" -> fetchFromYahoo(asset);
                default -> fetchFromStooq(asset);
            };
        } catch (Exception e) {
            // Best-effort: log at debug and let the caller keep the current price.
            log.debug("Price fetch failed for symbol={}: {}", asset.getSymbol(), e.toString());
            return Optional.empty();
        }
    }

    // ─── Stooq (CSV, no API key) ───
    // Endpoint: https://stooq.com/q/l/?s=<symbol>&f=sd2t2ohlcv&h&e=csv
    // For US tickers Stooq expects a ".us" suffix (e.g. tsla.us). We try the mapped symbol first,
    // then a bare fallback. A row with "N/D" means the symbol is not quoted.
    private Optional<BigDecimal> fetchFromStooq(Asset asset) throws Exception {
        String base = asset.getSymbol().trim().toLowerCase();
        String mapped = mapStooqSymbol(base, asset.getExchange());
        for (String sym : new String[]{ mapped, base }) {
            String url = "https://stooq.com/q/l/?s=" + urlEncode(sym) + "&f=sd2t2ohlcv&h&e=csv";
            String body = get(url);
            Optional<BigDecimal> price = parseStooqCsv(body);
            if (price.isPresent()) return price;
        }
        // Stooq often blocks server-side CSV access (returns an HTML error page). Fall back to
        // Yahoo so the "stooq" setting still yields a price for common tickers.
        log.debug("Stooq returned no price for {}, falling back to Yahoo", asset.getSymbol());
        return fetchFromYahoo(asset);
    }

    /** Parse the Stooq CSV; the close price is column index 6 (Symbol,Date,Time,Open,High,Low,Close,Volume). */
    Optional<BigDecimal> parseStooqCsv(String body) {
        if (body == null) return Optional.empty();
        String[] lines = body.strip().split("\r?\n");
        if (lines.length < 2) return Optional.empty();
        String[] cols = lines[1].split(",");
        if (cols.length < 7) return Optional.empty();
        String close = cols[6].trim();
        if (close.isEmpty() || close.equalsIgnoreCase("N/D")) return Optional.empty();
        try {
            BigDecimal v = new BigDecimal(close);
            return v.signum() > 0 ? Optional.of(v) : Optional.empty();
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /** Map a symbol to Stooq's convention. US equities need a ".us" suffix; SGX uses ".sg". */
    String mapStooqSymbol(String base, String exchange) {
        if (base.contains(".")) return base; // already qualified
        String ex = exchange == null ? "" : exchange.trim().toUpperCase();
        return switch (ex) {
            case "NASDAQ", "NYSE", "NYSEARCA", "AMEX", "BATS", "US" -> base + ".us";
            case "SGX" -> base + ".sg";
            case "LSE" -> base + ".uk";
            case "HKEX" -> base + ".hk";
            default -> base + ".us"; // best-effort default for equities
        };
    }

    // ─── Yahoo Finance (unofficial chart endpoint) ───
    // The old v7 /finance/quote endpoint now returns "Unauthorized"; the v8 /chart endpoint still
    // works without auth and exposes the latest price at chart.result[0].meta.regularMarketPrice.
    private Optional<BigDecimal> fetchFromYahoo(Asset asset) throws Exception {
        String sym = mapYahooSymbol(asset.getSymbol().trim(), asset.getExchange());
        String url = "https://query1.finance.yahoo.com/v8/finance/chart/" + urlEncode(sym) + "?range=1d&interval=1d";
        String body = get(url);
        return parseYahooJson(body);
    }

    Optional<BigDecimal> parseYahooJson(String body) {
        if (body == null) return Optional.empty();
        try {
            JsonNode meta = mapper.readTree(body).path("chart").path("result").path(0).path("meta");
            JsonNode price = meta.path("regularMarketPrice");
            if (price.isNumber()) {
                BigDecimal v = price.decimalValue();
                return v.signum() > 0 ? Optional.of(v) : Optional.empty();
            }
        } catch (Exception e) {
            log.debug("Yahoo parse failed: {}", e.toString());
        }
        return Optional.empty();
    }

    /** Map a symbol to Yahoo's convention (SGX → .SI, LSE → .L, HKEX → .HK; US as-is). */
    String mapYahooSymbol(String base, String exchange) {
        if (base.contains(".")) return base;
        String ex = exchange == null ? "" : exchange.trim().toUpperCase();
        return switch (ex) {
            case "SGX" -> base + ".SI";
            case "LSE" -> base + ".L";
            case "HKEX" -> base + ".HK";
            default -> base; // US tickers are bare on Yahoo
        };
    }

    // ─── FX rates (mid-market) ───
    /**
     * Best-effort mid-market FX rate for {@code from->to} (e.g. USD->SGD) from Yahoo's chart
     * endpoint using the {@code FROMTO=X} convention (e.g. USDSGD=X). Returns empty when disabled,
     * the pair is unquoted (e.g. many LKR pairs), or the request fails. Never throws.
     * The returned value is the mid-market rate WITHOUT any broker spread — the caller stores it
     * as the rate and the spread is applied separately at conversion time.
     */
    public Optional<BigDecimal> fetchFxRate(String from, String to) {
        if (!enabled) return Optional.empty();
        if (from == null || to == null) return Optional.empty();
        String f = from.trim().toUpperCase();
        String t = to.trim().toUpperCase();
        if (f.isEmpty() || t.isEmpty() || f.equals(t)) return Optional.empty();
        try {
            String pair = f + t + "=X";
            String url = "https://query1.finance.yahoo.com/v8/finance/chart/" + urlEncode(pair) + "?range=1d&interval=1d";
            String body = get(url);
            return parseYahooJson(body); // same meta.regularMarketPrice shape as equities
        } catch (Exception e) {
            log.debug("FX fetch failed for {}->{}: {}", from, to, e.toString());
            return Optional.empty();
        }
    }

    private String get(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(8))
                .header("User-Agent", "MyFinance/1.0")
                .GET()
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            log.debug("Price provider returned {} for {}", resp.statusCode(), url);
            return null;
        }
        return resp.body();
    }

    private static String urlEncode(String s) {
        return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8);
    }
}
