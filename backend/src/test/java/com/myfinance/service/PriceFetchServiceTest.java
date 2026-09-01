package com.myfinance.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure unit tests for the price parsing/mapping helpers — no network calls. Network behavior
 * (the actual HTTP fetch) is intentionally not exercised here; it's best-effort and covered by
 * the graceful-empty contract.
 */
class PriceFetchServiceTest {

    private final PriceFetchService svc = new PriceFetchService();

    @Test
    void parsesStooqCloseFromCsv() {
        String csv = "Symbol,Date,Time,Open,High,Low,Close,Volume\n" +
                "TSLA.US,2026-08-31,22:00:00,410,415,405,412.34,1000000";
        Optional<BigDecimal> price = svc.parseStooqCsv(csv);
        assertTrue(price.isPresent());
        assertEquals(0, new BigDecimal("412.34").compareTo(price.get()));
    }

    @Test
    void stooqReturnsEmptyForNotQuotedSymbol() {
        // Stooq emits "N/D" for symbols it doesn't quote (e.g. Sri Lanka / CSE tickers).
        String csv = "Symbol,Date,Time,Open,High,Low,Close,Volume\n" +
                "JKH.LK,N/D,N/D,N/D,N/D,N/D,N/D,N/D";
        assertTrue(svc.parseStooqCsv(csv).isEmpty());
    }

    @Test
    void stooqReturnsEmptyForMalformedOrEmptyBody() {
        assertTrue(svc.parseStooqCsv(null).isEmpty());
        assertTrue(svc.parseStooqCsv("").isEmpty());
        assertTrue(svc.parseStooqCsv("just-a-header-only").isEmpty());
    }

    @Test
    void mapsStooqSymbolByExchange() {
        assertEquals("tsla.us", svc.mapStooqSymbol("tsla", "NASDAQ"));
        assertEquals("d05.sg", svc.mapStooqSymbol("d05", "SGX"));
        assertEquals("vod.uk", svc.mapStooqSymbol("vod", "LSE"));
        assertEquals("aapl.us", svc.mapStooqSymbol("aapl", null)); // best-effort default
        assertEquals("brk.b", svc.mapStooqSymbol("brk.b", "NYSE")); // already qualified, untouched
    }

    @Test
    void parsesYahooRegularMarketPrice() {
        String json = "{\"quoteResponse\":{\"result\":[{\"symbol\":\"TSLA\",\"regularMarketPrice\":250.5}]}}";
        Optional<BigDecimal> price = svc.parseYahooJson(json);
        assertTrue(price.isPresent());
        assertEquals(0, new BigDecimal("250.5").compareTo(price.get()));
    }

    @Test
    void yahooReturnsEmptyForNoResult() {
        assertTrue(svc.parseYahooJson("{\"quoteResponse\":{\"result\":[]}}").isEmpty());
        assertTrue(svc.parseYahooJson(null).isEmpty());
        assertTrue(svc.parseYahooJson("not json").isEmpty());
    }

    @Test
    void mapsYahooSymbolByExchange() {
        assertEquals("D05.SI", svc.mapYahooSymbol("D05", "SGX"));
        assertEquals("VOD.L", svc.mapYahooSymbol("VOD", "LSE"));
        assertEquals("TSLA", svc.mapYahooSymbol("TSLA", "NASDAQ")); // US bare on Yahoo
    }
}
