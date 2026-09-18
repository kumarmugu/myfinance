package com.myfinance.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses the {@code <Trade>} and {@code <CorporateAction>} sections of an IBKR "Activity Flex"
 * statement XML. Kept separate from {@link DividendImportService} so trade-sync logic is isolated.
 *
 * <p>Only plain stock/ETF buys and sells are surfaced as trades — options, futures, forex (CASH)
 * and warrants are skipped so we never create malformed transactions. Corporate actions are parsed
 * separately: stock splits and symbol changes are actionable; anything else is flagged for manual
 * review rather than applied blindly.
 */
@Slf4j
@Service
public class IbkrTradeParser {

    /** Asset categories we import as normal transactions. Everything else is skipped. */
    private static final java.util.Set<String> STOCK_LIKE = java.util.Set.of("STK", "ETF", "FUND");

    /** A single parsed trade from the Flex {@code <Trade>} section. */
    public record ParsedTrade(String tradeId, String symbol, String assetCategory,
                              boolean buy, BigDecimal quantity, BigDecimal price, String currency,
                              LocalDate tradeDate, BigDecimal commission, BigDecimal fxRateToBase) {}

    /** A parsed corporate action. {@code kind} is SPLIT, SYMBOL_CHANGE, or OTHER (needs review). */
    public record ParsedCorporateAction(String kind, String symbol, String newSymbol,
                                        BigDecimal ratio, LocalDate date, String description) {}

    public record FlexTrades(List<ParsedTrade> trades, List<ParsedTrade> skipped,
                             List<ParsedCorporateAction> corporateActions,
                             List<ParsedCorporateAction> needsReview) {}

    /**
     * Parse trades + corporate actions from a Flex statement. Returns imported-eligible trades,
     * skipped (non-stock) trades, actionable corporate actions, and ones needing manual review.
     */
    public FlexTrades parse(String xml) {
        List<ParsedTrade> trades = new ArrayList<>();
        List<ParsedTrade> skipped = new ArrayList<>();
        List<ParsedCorporateAction> corp = new ArrayList<>();
        List<ParsedCorporateAction> review = new ArrayList<>();

        try {
            XMLStreamReader r = XMLInputFactory.newInstance().createXMLStreamReader(
                    new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
            while (r.hasNext()) {
                if (r.next() != XMLStreamReader.START_ELEMENT) continue;
                String el = r.getLocalName();
                if ("Trade".equals(el)) {
                    ParsedTrade t = readTrade(r);
                    if (t == null) continue;
                    if (t.assetCategory() != null && STOCK_LIKE.contains(t.assetCategory().toUpperCase())) {
                        trades.add(t);
                    } else {
                        skipped.add(t); // options / futures / forex / warrants
                    }
                } else if ("CorporateAction".equals(el)) {
                    ParsedCorporateAction ca = readCorporateAction(r);
                    if (ca == null) continue;
                    if ("OTHER".equals(ca.kind())) review.add(ca); else corp.add(ca);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse IBKR Flex trades: " + e.getMessage(), e);
        }
        return new FlexTrades(trades, skipped, corp, review);
    }

    private ParsedTrade readTrade(XMLStreamReader r) {
        String tradeId = firstNonBlank(attr(r, "tradeID"), attr(r, "ibExecID"), attr(r, "transactionID"));
        String symbol = firstNonBlank(attr(r, "symbol"), attr(r, "underlyingSymbol"));
        String category = attr(r, "assetCategory");
        String buySell = attr(r, "buySell");
        BigDecimal qty = num(attr(r, "quantity"));
        BigDecimal price = num(attr(r, "tradePrice"));
        String currency = attr(r, "currency");
        LocalDate date = date(firstNonBlank(attr(r, "tradeDate"), attr(r, "dateTime"), attr(r, "settleDateTarget")));
        BigDecimal commission = num(attr(r, "ibCommission"));
        BigDecimal fxRate = num(attr(r, "fxRateToBase"));

        if (symbol == null || date == null || qty.signum() == 0) return null;

        // Determine direction: prefer the explicit buySell flag, else the sign of quantity.
        boolean buy;
        if (buySell != null) buy = buySell.trim().toUpperCase().startsWith("BUY");
        else buy = qty.signum() > 0;

        return new ParsedTrade(
                tradeId, symbol.trim().toUpperCase(), category,
                buy, qty.abs(), price.abs(),
                currency == null ? "USD" : currency.trim().toUpperCase(),
                date,
                commission == null ? null : commission.abs(),
                fxRate == null || fxRate.signum() == 0 ? null : fxRate);
    }

    private ParsedCorporateAction readCorporateAction(XMLStreamReader r) {
        String symbol = firstNonBlank(attr(r, "symbol"), attr(r, "underlyingSymbol"));
        String type = attr(r, "type");             // e.g. FS (forward split), RS (reverse split), TC (ticker change)
        String description = attr(r, "actionDescription");
        LocalDate date = date(firstNonBlank(attr(r, "reportDate"), attr(r, "dateTime")));
        if (symbol == null) return null;
        symbol = symbol.trim().toUpperCase();

        String t = type == null ? "" : type.trim().toUpperCase();
        String descLower = description == null ? "" : description.toLowerCase();

        // Splits: IBKR uses FS/RS for forward/reverse split; description often "SPLIT".
        if (t.equals("FS") || t.equals("RS") || descLower.contains("split")) {
            return new ParsedCorporateAction("SPLIT", symbol, null, parseSplitRatio(description), date, description);
        }
        // Ticker/symbol change.
        if (t.equals("TC") || descLower.contains("symbol change") || descLower.contains("ticker change")
                || descLower.contains("name change")) {
            return new ParsedCorporateAction("SYMBOL_CHANGE", symbol, parseNewSymbol(description), null, date, description);
        }
        // Everything else (spinoff, merger, delisting, ...) needs a human.
        return new ParsedCorporateAction("OTHER", symbol, null, null, date, description);
    }

    /** Pull a split ratio like "2 FOR 1" / "SPLIT 4:1" out of the description; null if not found. */
    private BigDecimal parseSplitRatio(String desc) {
        if (desc == null) return null;
        var m = java.util.regex.Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(?:for|:|/)\\s*(\\d+(?:\\.\\d+)?)",
                java.util.regex.Pattern.CASE_INSENSITIVE).matcher(desc);
        if (m.find()) {
            BigDecimal a = new BigDecimal(m.group(1));
            BigDecimal b = new BigDecimal(m.group(2));
            if (b.signum() != 0) return a.divide(b, 8, java.math.RoundingMode.HALF_UP);
        }
        return null;
    }

    /** Pull the new ticker from a symbol-change description; best-effort, null if unclear. */
    private String parseNewSymbol(String desc) {
        if (desc == null) return null;
        var m = java.util.regex.Pattern.compile("\\bto\\s+([A-Z0-9.\\-]{1,15})\\b").matcher(desc);
        if (m.find()) return m.group(1).toUpperCase();
        return null;
    }

    // ── low-level helpers ──

    private String attr(XMLStreamReader r, String name) {
        String v = r.getAttributeValue(null, name);
        return (v == null || v.isBlank()) ? null : v;
    }

    private String firstNonBlank(String... vals) {
        for (String v : vals) if (v != null && !v.isBlank()) return v;
        return null;
    }

    private BigDecimal num(String s) {
        if (s == null) return BigDecimal.ZERO;
        s = s.trim();
        if (s.isEmpty() || s.equals("-")) return BigDecimal.ZERO;
        try { return new BigDecimal(s); } catch (NumberFormatException e) { return BigDecimal.ZERO; }
    }

    private LocalDate date(String s) {
        if (s == null) return null;
        String d = s.trim();
        int sp = d.indexOf(' ');
        if (sp > 0) d = d.substring(0, sp);
        if (d.contains(";")) d = d.substring(0, d.indexOf(';'));
        try {
            if (d.matches("\\d{8}")) return LocalDate.parse(d, DateTimeFormatter.BASIC_ISO_DATE);
            return LocalDate.parse(d, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (Exception e) { return null; }
    }
}
