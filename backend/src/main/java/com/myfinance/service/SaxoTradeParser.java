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

    /** Header keywords we recognise (lower-cased, matched by "contains"). */
    private static final List<String> SYMBOL_HEADERS   = List.of("instrument symbol", "instrument", "symbol", "ticker", "uic");
    private static final List<String> SIDE_HEADERS     = List.of("buy/sell", "b/s", "direction", "side", "event");
    private static final List<String> QTY_HEADERS      = List.of("amount", "quantity", "traded amount", "filled quantity", "nominal");
    private static final List<String> PRICE_HEADERS    = List.of("price", "traded price", "avg price", "average price", "trade price");
    private static final List<String> DATE_HEADERS     = List.of("trade date", "trade time", "date", "execution time", "value date");
    private static final List<String> CCY_HEADERS      = List.of("instrument currency", "currency", "trade currency", "ccy");
    /** Fee/charge header keywords — every matching column is summed into one all-in fee. */
    private static final List<String> FEE_HEADERS      = List.of("commission", "fee", "cost", "charge", "tax", "levy", "duty", "gst", "conversion");
    /** Per-line FX rate to the account/base currency, if the export carries one. */
    private static final List<String> FX_HEADERS       = List.of("conversion rate", "fx rate", "exchange rate", "rate to base", "booking rate");

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

        // Find the header row (the first row that yields both a symbol and a side/quantity column).
        int headerIdx = -1;
        Map<String, Integer> cols = null;
        List<Integer> feeCols = List.of();
        for (int i = 0; i < Math.min(sheet.size(), 10); i++) {
            Map<String, Integer> m = mapColumns(sheet.get(i));
            if (m.containsKey("symbol") && (m.containsKey("side") || m.containsKey("qty"))) {
                headerIdx = i; cols = m; feeCols = feeColumns(sheet.get(i)); break;
            }
        }
        if (cols == null) {
            throw new RuntimeException("Could not recognise the Saxo trades sheet — expected columns like Instrument, Buy/Sell, Amount, Price, Trade Date");
        }

        // TEMP DIAGNOSTIC: log the detected header row, column mapping, and first few data rows so we
        // can see the real Saxo trade column names/values and fix the mapping. Remove after investigation.
        org.slf4j.Logger diag = org.slf4j.LoggerFactory.getLogger(SaxoTradeParser.class);
        diag.info("SAXO-DIAG header(row {})={} | resolved cols={} | feeCols={}",
                headerIdx, sheet.get(headerIdx), cols, feeCols);
        java.util.Map<String, Integer> txnTypeCounts = new java.util.TreeMap<>();
        java.util.Map<String, Integer> eventCounts = new java.util.TreeMap<>();
        for (int di = headerIdx + 1; di < sheet.size(); di++) {
            List<String> r = sheet.get(di);
            String tt = cell(r, 9); String ev = cell(r, 10);
            if (tt != null && !tt.isBlank()) txnTypeCounts.merge(tt.trim(), 1, Integer::sum);
            if (ev != null && !ev.isBlank()) eventCounts.merge(ev.trim(), 1, Integer::sum);
        }
        diag.info("SAXO-DIAG distinct TransactionType counts = {}", txnTypeCounts);
        diag.info("SAXO-DIAG distinct Event counts = {}", eventCounts);

        for (int i = headerIdx + 1; i < sheet.size(); i++) {
            List<String> row = sheet.get(i);
            String symbol = stripExchange(cell(row, cols.get("symbol")));
            if (symbol == null || symbol.isBlank()) continue;

            String sideRaw = cell(row, cols.get("side"));
            BigDecimal qty = num(cell(row, cols.get("qty")));
            Boolean buy = direction(sideRaw, qty);
            if (buy == null) continue;                    // not a trade row (dividend, fee, transfer, ...)
            if (qty.signum() == 0) continue;

            BigDecimal price = num(cell(row, cols.get("price")));
            String currency = cell(row, cols.get("ccy"));
            LocalDate date = date(cell(row, cols.get("date")));
            if (date == null) continue;

            // Sum every fee/charge column present into one all-in fee (absolute).
            BigDecimal fees = BigDecimal.ZERO;
            for (int fc : feeCols) fees = fees.add(num(cell(row, fc)).abs());

            // Per-line FX rate if the export carries one; else leave null (app derives from its rate table).
            BigDecimal fx = cols.containsKey("fx") ? num(cell(row, cols.get("fx"))) : BigDecimal.ZERO;

            trades.add(new IbkrTradeParser.ParsedTrade(
                    null, symbol.toUpperCase(), "STK",
                    buy, qty.abs(), price.abs(),
                    (currency == null || currency.isBlank()) ? "USD" : currency.trim().toUpperCase(),
                    date,
                    fees.signum() == 0 ? null : fees,
                    fx.signum() == 0 ? null : fx));
        }
        return new IbkrTradeParser.FlexTrades(trades, skipped, new ArrayList<>(), new ArrayList<>());
    }

    /** Map recognised header keywords → column index. */
    private Map<String, Integer> mapColumns(List<String> header) {
        Map<String, Integer> cols = new HashMap<>();
        for (int c = 0; c < header.size(); c++) {
            String h = header.get(c) == null ? "" : header.get(c).trim().toLowerCase();
            if (h.isEmpty()) continue;
            putIfHeader(cols, "symbol", h, c, SYMBOL_HEADERS);
            putIfHeader(cols, "side", h, c, SIDE_HEADERS);
            putIfHeader(cols, "qty", h, c, QTY_HEADERS);
            putIfHeader(cols, "price", h, c, PRICE_HEADERS);
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

    /** BUY → true, SELL → false. If side text is ambiguous, fall back to the sign of the quantity. */
    private Boolean direction(String side, BigDecimal qty) {
        if (side != null) {
            String s = side.trim().toLowerCase();
            if (s.startsWith("buy") || s.equals("b") || s.contains("bought")) return Boolean.TRUE;
            if (s.startsWith("sell") || s.equals("s") || s.contains("sold")) return Boolean.FALSE;
        }
        if (qty != null && qty.signum() != 0) return qty.signum() > 0;
        return null;
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
