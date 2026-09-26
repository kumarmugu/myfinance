package com.myfinance.service;

import org.springframework.stereotype.Service;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Parses a Saxo "Transactions" trade export (XLSX) into the shared {@link IbkrTradeParser.FlexTrades}
 * shape, so the existing {@link IbkrSyncService} preview/apply/dedupe machinery imports Saxo trades
 * identically to IBKR ones. Saxo has no live trade API for our accounts (registration is funding-gated),
 * so file import is the supported path.
 *
 * <p>Reads the XLSX with JDK-only {@code java.util.zip} + StAX (no Apache POI), and — unlike a
 * fixed-column reader — resolves each cell's column from its {@code r} reference (e.g. {@code C7}) so
 * blank cells don't shift the row, then maps columns by matching the <em>header names</em>. This makes
 * the importer resilient to Saxo re-ordering or inserting columns.
 *
 * <p>Trades carry no stable external id in the file, so de-duplication relies on the fuzzy
 * (account+owner+symbol+date+type+quantity~/price) match in {@link IbkrSyncService}.
 */
@Service
public class SaxoTradeParser {

    /**
     * Header keywords we recognise (lower-cased, matched by "contains"). Order matters: the FIRST
     * keyword that a header contains wins, so more-specific names must come first. A Saxo transactions
     * export has an "Instrument" (descriptive name) AND an "Instrument Symbol" (the ticker) column — we
     * must map SYMBOL to the ticker, so "instrument symbol" is matched before the bare "instrument".
     */
    private static final List<String> SYMBOL_HEADERS   = List.of("instrument symbol", "symbol", "ticker", "uic");
    private static final List<String> NAME_HEADERS     = List.of("instrument");
    /** "Transaction Type" classifies the row: only "Trade" rows are buys/sells (the rest are dividends, fees, transfers...). */
    private static final List<String> TXNTYPE_HEADERS  = List.of("transaction type");
    /** Saxo packs side+quantity+price into the "Event" text, e.g. "Buy 10 @ 53.00 USD" / "Sell -5 @ 373.47 USD". */
    private static final List<String> EVENT_HEADERS    = List.of("event");
    private static final List<String> DATE_HEADERS     = List.of("trade date", "trade time", "execution time", "value date", "date");
    private static final List<String> CCY_HEADERS      = List.of("instrument currency", "trade currency", "currency", "ccy");
    /** Fee/charge header keywords — every matching column is summed into one all-in fee. */
    private static final List<String> FEE_HEADERS      = List.of("conversion cost", "total cost", "commission");
    /** Per-line FX rate to the account/base currency, if the export carries one. */
    private static final List<String> FX_HEADERS       = List.of("conversion rate", "fx rate", "exchange rate", "rate to base", "booking rate");

    /** side + signed quantity + price (+ optional currency) packed in the Saxo "Event" text. */
    private static final java.util.regex.Pattern EVENT_TRADE =
            java.util.regex.Pattern.compile("(?i)\\b(buy|sell|bought|sold)\\b\\s*(-?[0-9][0-9,]*(?:\\.[0-9]+)?)\\s*@\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)\\s*([A-Za-z]{3})?");

    /** True if the bytes look like an XLSX (a ZIP container starts with "PK"). */
    public static boolean looksLikeXlsx(byte[] content) {
        return content != null && content.length > 3
                && content[0] == 0x50 && content[1] == 0x4B; // 'P','K'
    }

    /** Parse the uploaded Saxo trades XLSX. Only genuine buy/sell rows with a quantity become trades. */
    public IbkrTradeParser.FlexTrades parse(byte[] xlsx) {
        List<List<String>> sheet = readWorkbook(xlsx);
        List<IbkrTradeParser.ParsedTrade> trades = new ArrayList<>();
        List<IbkrTradeParser.ParsedTrade> skipped = new ArrayList<>();
        if (sheet.size() < 2) {
            return new IbkrTradeParser.FlexTrades(trades, skipped, new ArrayList<>(), new ArrayList<>());
        }

        // Find the header row: the first row that yields a symbol/name column plus the "Transaction Type"
        // classifier and the "Event" column (Saxo packs side+qty+price into Event, and there is no
        // dedicated quantity/price column in the transactions export).
        int headerIdx = -1;
        Map<String, Integer> cols = null;
        List<Integer> feeCols = List.of();
        for (int i = 0; i < Math.min(sheet.size(), 10); i++) {
            Map<String, Integer> m = mapColumns(sheet.get(i));
            if ((m.containsKey("symbol") || m.containsKey("name")) && m.containsKey("txnType") && m.containsKey("event")) {
                headerIdx = i; cols = m; feeCols = feeColumns(sheet.get(i)); break;
            }
        }
        if (cols == null) {
            throw new RuntimeException("Could not recognise the Saxo transactions sheet — expected columns like "
                    + "Transaction Type, Event, Instrument Symbol, Trade Date, Currency");
        }

        List<IbkrTradeParser.ParsedCorporateAction> corporateActions = new ArrayList<>();

        for (int i = headerIdx + 1; i < sheet.size(); i++) {
            List<String> row = sheet.get(i);

            // Only genuine trade bookings are buys/sells. Everything else (Corporate action = dividends/
            // splits/mergers, Cash amount = fees/interest, Cash Transfer = deposits/withdrawals) is skipped
            // here — those are not trades and must never be turned into positions.
            String txnType = cell(row, cols.get("txnType"));
            if (txnType == null || !txnType.trim().equalsIgnoreCase("Trade")) {
                // Detect stock-split events and surface them so the user can enter the ratio (the Saxo
                // export records that a split happened, but NOT the ratio). Symbol prefers the ticker.
                String ev = cell(row, cols.get("event"));
                if (ev != null && ev.toLowerCase().contains("split")) {
                    String sym = stripExchange(cell(row, cols.get("symbol")));
                    if (sym == null || sym.isBlank()) sym = cell(row, cols.get("name"));
                    corporateActions.add(new IbkrTradeParser.ParsedCorporateAction(
                            "SPLIT", sym == null ? null : sym.toUpperCase(), null,
                            null, date(cell(row, cols.get("date"))), ev.trim()));
                }
                continue;
            }

            // Side, quantity and price live inside the Event text, e.g. "Buy 10 @ 53.00 USD".
            String event = cell(row, cols.get("event"));
            if (event == null) continue;
            var m = EVENT_TRADE.matcher(event);
            if (!m.find()) { skipped.add(unparsedTrade(row, cols, event)); continue; }

            String side = m.group(1).toLowerCase();
            boolean buy = side.startsWith("buy") || side.equals("bought");
            BigDecimal qty = new BigDecimal(m.group(2).replace(",", "")).abs();
            BigDecimal price = new BigDecimal(m.group(3).replace(",", "")).abs();
            if (qty.signum() == 0) continue;

            // Symbol: prefer the real ticker ("Instrument Symbol", e.g. "MSFT:xnas" → "MSFT"); fall back to
            // the descriptive name only if no ticker column/value is present.
            String symbol = stripExchange(cell(row, cols.get("symbol")));
            if (symbol == null || symbol.isBlank()) symbol = cell(row, cols.get("name"));
            if (symbol == null || symbol.isBlank()) continue;

            // Currency: the code trailing the Event wins ("... 53.00 USD"), else the instrument currency column.
            String currency = (m.group(4) != null && !m.group(4).isBlank()) ? m.group(4) : cell(row, cols.get("ccy"));

            LocalDate date = date(cell(row, cols.get("date")));
            if (date == null) continue;

            // Sum any explicit fee/charge columns into one all-in fee (usually 0 on Saxo trade rows —
            // commissions/custody fees are booked as separate "Cash amount" rows).
            BigDecimal fees = BigDecimal.ZERO;
            for (int fc : feeCols) fees = fees.add(num(cell(row, fc)).abs());

            // Per-line FX rate if the export carries one; else leave null (app derives from its rate table).
            BigDecimal fx = cols.containsKey("fx") ? num(cell(row, cols.get("fx"))) : BigDecimal.ZERO;

            trades.add(new IbkrTradeParser.ParsedTrade(
                    null, symbol.toUpperCase(), "STK",
                    buy, qty, price,
                    (currency == null || currency.isBlank()) ? "USD" : currency.trim().toUpperCase(),
                    date,
                    fees.signum() == 0 ? null : fees,
                    fx.signum() == 0 ? null : fx));
        }
        return new IbkrTradeParser.FlexTrades(trades, skipped, corporateActions, new ArrayList<>());
    }

    /** A Trade row whose Event text we couldn't parse — surface it as skipped so the user can see it. */
    private IbkrTradeParser.ParsedTrade unparsedTrade(List<String> row, Map<String, Integer> cols, String event) {
        String symbol = stripExchange(cell(row, cols.get("symbol")));
        if (symbol == null || symbol.isBlank()) symbol = cell(row, cols.get("name"));
        LocalDate date = date(cell(row, cols.get("date")));
        return new IbkrTradeParser.ParsedTrade(
                null, symbol == null ? event : symbol.toUpperCase(), "STK",
                true, BigDecimal.ZERO, BigDecimal.ZERO,
                "USD", date, null, null);
    }

    /** Map recognised header keywords → column index. */
    private Map<String, Integer> mapColumns(List<String> header) {
        Map<String, Integer> cols = new HashMap<>();
        for (int c = 0; c < header.size(); c++) {
            String h = header.get(c) == null ? "" : header.get(c).trim().toLowerCase();
            if (h.isEmpty()) continue;
            putIfHeader(cols, "symbol", h, c, SYMBOL_HEADERS);
            putIfHeader(cols, "name", h, c, NAME_HEADERS);
            putIfHeader(cols, "txnType", h, c, TXNTYPE_HEADERS);
            putIfHeader(cols, "event", h, c, EVENT_HEADERS);
            putIfHeader(cols, "date", h, c, DATE_HEADERS);
            putIfHeader(cols, "ccy", h, c, CCY_HEADERS);
            putIfHeader(cols, "fx", h, c, FX_HEADERS);
        }
        return cols;
    }

    /**
     * Every fee/charge column index (a Saxo trades sheet may carry commission plus separate exchange
     * fee, tax, levy, etc. columns). We sum them all into one all-in fee. The symbol/side/qty/price/
     * date/ccy/fx columns are excluded so a header like "Trade Price" isn't mistaken for a fee.
     */
    private List<Integer> feeColumns(List<String> header) {
        java.util.Set<Integer> structural = new java.util.HashSet<>(mapColumns(header).values());
        List<Integer> out = new ArrayList<>();
        for (int c = 0; c < header.size(); c++) {
            if (structural.contains(c)) continue;
            String h = header.get(c) == null ? "" : header.get(c).trim().toLowerCase();
            if (h.isEmpty()) continue;
            for (String kw : FEE_HEADERS) {
                if (h.contains(kw)) { out.add(c); break; }
            }
        }
        return out;
    }

    private void putIfHeader(Map<String, Integer> cols, String key, String header, int idx, List<String> keywords) {
        if (cols.containsKey(key)) return;          // keep the first match
        for (String kw : keywords) {
            if (header.contains(kw)) { cols.put(key, idx); return; }
        }
    }

    // ─────────────────────────── XLSX reading (reference-aware) ───────────────────────────

    private List<List<String>> readWorkbook(byte[] xlsx) {
        List<String> shared = new ArrayList<>();
        byte[] sheetXml = null;
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(xlsx))) {
            byte[] sharedXml = null;
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                String name = e.getName();
                if (name.equals("xl/sharedStrings.xml")) sharedXml = readAll(zis);
                // Take the first worksheet (Saxo exports a single sheet).
                else if (sheetXml == null && name.startsWith("xl/worksheets/") && name.endsWith(".xml")) sheetXml = readAll(zis);
            }
            if (sharedXml != null) shared = readSharedStrings(sharedXml);
            if (sheetXml == null) throw new RuntimeException("no worksheet found");
            return readSheet(sheetXml, shared);
        } catch (RuntimeException e) {
            throw new RuntimeException("Failed to read Saxo XLSX: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to read Saxo XLSX: " + e.getMessage(), e);
        }
    }

    private List<String> readSharedStrings(byte[] xml) throws Exception {
        List<String> out = new ArrayList<>();
        XMLStreamReader r = XMLInputFactory.newInstance().createXMLStreamReader(new ByteArrayInputStream(xml));
        StringBuilder cur = null;
        while (r.hasNext()) {
            int ev = r.next();
            if (ev == XMLStreamReader.START_ELEMENT && r.getLocalName().equals("si")) cur = new StringBuilder();
            else if (ev == XMLStreamReader.CHARACTERS && cur != null) cur.append(r.getText());
            else if (ev == XMLStreamReader.END_ELEMENT && r.getLocalName().equals("si") && cur != null) {
                out.add(cur.toString());
                cur = null;
            }
        }
        return out;
    }

    /**
     * Read a worksheet into rows of cell strings, resolving each cell's column from its {@code r}
     * reference (e.g. "C7") so blank cells are preserved as empty strings and columns stay aligned.
     */
    private List<List<String>> readSheet(byte[] xml, List<String> shared) throws Exception {
        List<List<String>> rows = new ArrayList<>();
        XMLStreamReader r = XMLInputFactory.newInstance().createXMLStreamReader(new ByteArrayInputStream(xml));
        Map<Integer, String> cellsByCol = null;
        int maxCol = -1;
        String cellType = null, value = null;
        int colIdx = 0;
        boolean inValue = false;
        while (r.hasNext()) {
            int ev = r.next();
            if (ev == XMLStreamReader.START_ELEMENT) {
                switch (r.getLocalName()) {
                    case "row" -> { cellsByCol = new HashMap<>(); maxCol = -1; }
                    case "c" -> {
                        cellType = r.getAttributeValue(null, "t");
                        value = null;
                        colIdx = columnFromRef(r.getAttributeValue(null, "r"));
                    }
                    case "v" -> inValue = true;
                    default -> { }
                }
            } else if (ev == XMLStreamReader.CHARACTERS && inValue) {
                value = (value == null ? "" : value) + r.getText();
            } else if (ev == XMLStreamReader.END_ELEMENT) {
                switch (r.getLocalName()) {
                    case "v" -> inValue = false;
                    case "c" -> {
                        String resolved = value;
                        if ("s".equals(cellType) && value != null) {
                            int idx = safeInt(value);
                            resolved = (idx >= 0 && idx < shared.size()) ? shared.get(idx) : "";
                        }
                        if (cellsByCol != null && colIdx >= 0) {
                            cellsByCol.put(colIdx, resolved == null ? "" : resolved);
                            if (colIdx > maxCol) maxCol = colIdx;
                        }
                    }
                    case "row" -> {
                        List<String> row = new ArrayList<>();
                        for (int c = 0; c <= maxCol; c++) row.add(cellsByCol == null ? "" : cellsByCol.getOrDefault(c, ""));
                        rows.add(row);
                        cellsByCol = null;
                    }
                    default -> { }
                }
            }
        }
        return rows;
    }

    /** "C7" → 2 (0-based column). Returns -1 if the ref is missing/unparseable. */
    private int columnFromRef(String ref) {
        if (ref == null) return -1;
        int col = 0; boolean any = false;
        for (int i = 0; i < ref.length(); i++) {
            char ch = ref.charAt(i);
            if (ch >= 'A' && ch <= 'Z') { col = col * 26 + (ch - 'A' + 1); any = true; }
            else if (ch >= 'a' && ch <= 'z') { col = col * 26 + (ch - 'a' + 1); any = true; }
            else break;
        }
        return any ? col - 1 : -1;
    }

    // ─────────────────────────── low-level helpers ───────────────────────────

    private String cell(List<String> row, Integer i) {
        if (i == null || i < 0 || i >= row.size()) return null;
        return row.get(i);
    }

    private String stripExchange(String sym) {
        if (sym == null) return null;
        int i = sym.indexOf(':');
        return (i >= 0 ? sym.substring(0, i) : sym).trim();
    }

    private int safeInt(String s) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return -1; }
    }

    /** Parse a number that may carry a currency prefix ("USD 34.75"), thousands separators or sign. */
    private BigDecimal num(String s) {
        if (s == null) return BigDecimal.ZERO;
        String t = s.trim();
        if (t.isEmpty() || t.equals("-")) return BigDecimal.ZERO;
        var m = java.util.regex.Pattern.compile("(-?)\\s*(?:[A-Za-z]{3}\\s*)?(-?[0-9][0-9,]*(?:\\.[0-9]+)?)").matcher(t);
        if (m.find()) {
            BigDecimal v = new BigDecimal(m.group(2).replace(",", ""));
            return m.group(1).equals("-") ? v.negate() : v;
        }
        try { return new BigDecimal(t.replace(",", "")); } catch (NumberFormatException e) { return BigDecimal.ZERO; }
    }

    /** Excel serial date (days since 1899-12-30) or ISO string → LocalDate. */
    private LocalDate date(String s) {
        if (s == null || s.isBlank()) return null;
        String d = s.trim();
        int sp = d.indexOf(' ');
        if (sp > 0 && !d.matches("\\d+")) d = d.substring(0, sp); // drop a trailing time on textual dates
        try {
            if (d.matches("\\d+")) return LocalDate.of(1899, 12, 30).plusDays(Long.parseLong(d));
            return LocalDate.parse(d, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (Exception e) {
            // Try a couple of common Saxo textual formats.
            for (String pat : new String[]{"dd-MMM-yyyy", "dd/MM/yyyy", "yyyy/MM/dd", "d MMM yyyy"}) {
                try { return LocalDate.parse(d, DateTimeFormatter.ofPattern(pat, java.util.Locale.ENGLISH)); }
                catch (Exception ignored) { }
            }
            return null;
        }
    }

    private byte[] readAll(InputStream in) throws java.io.IOException {
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) bos.write(buf, 0, n);
        return bos.toByteArray();
    }
}
