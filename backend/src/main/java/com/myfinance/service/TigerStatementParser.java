package com.myfinance.service;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses a Tiger Brokers "Activity Statement" CSV export into the shared {@link IbkrTradeParser.FlexTrades}
 * shape, so the existing {@link IbkrSyncService} preview/apply/dedupe machinery imports Tiger trades
 * identically to IBKR ones. This is the file-import counterpart to the live {@link TigerSyncService}.
 *
 * <p>The statement is a multi-section CSV where the first column is the section name. We read only the
 * {@code Trades > Stock} section:
 * <ul>
 *   <li>rows begin with {@code Trades,Stock,,DATA,} and carry a non-blank Symbol;</li>
 *   <li>the {@code Trades,Forex,...} sub-table, {@code TOTAL} rows, and Tiger's duplicate blank-symbol
 *       row that follows each trade are all skipped;</li>
 *   <li>columns are resolved by the section's own header row, not by fixed indices.</li>
 * </ul>
 *
 * <p><b>Fees:</b> every fee/charge column present (Commission, Platform Fee, GST, Settlement Fee, and
 * all the regulatory columns) is summed into one all-in fee per trade, in the trade currency.
 *
 * <p><b>FX rate:</b> when a stock trade's currency differs from the statement's base currency, we
 * associate the FX rate from the {@code Trades > Forex} section (nearest {@code CCY.BASE} row by date),
 * falling back to the {@code Base Currency Exchange Rate} table. If none is found, {@code fxRateToBase}
 * is left null and the app derives base value from the user's own rate table at the trade date.
 *
 * <p>Trades carry no stable id in the file, so de-duplication relies on the fuzzy
 * (account+owner+symbol+date+type+quantity~/price) match in {@link IbkrSyncService}.
 */
@Service
public class TigerStatementParser {

    /** Fee/charge column headers to sum into the trade's all-in fee (lower-cased exact matches). */
    private static final java.util.Set<String> FEE_HEADERS = java.util.Set.of(
            "commission", "platform fee", "gst", "settlement fee", "transaction fee",
            "other tripartite fees", "sec fee", "option regulatory fee", "stamp duty",
            "transaction levy", "clearing fee", "trading activity fee", "exchange fee",
            "future regulatory fee", "brokerage fee", "handing fee", "securities management fee",
            "transfer fees (csdc)", "transfer fees (hkscc)", "stamp duty on stock borrowing",
            "consolidated audit trail fee", "processing fee", "afrc transaction levy", "trading tariff");

    /** True if the text looks like a Tiger Activity Statement (as opposed to an IBKR CSV / Flex XML). */
    public static boolean looksLikeTigerStatement(String text) {
        if (text == null) return false;
        String head = text.stripLeading();
        return head.startsWith("Activity Statement")
                || head.contains("\nTrades,Stock,") || head.contains("\nTrades,Forex,");
    }

    /** One FX observation from the Forex section: base units per 1 unit of {@code ccy}, on {@code date}. */
    private record FxRate(String ccy, BigDecimal rate, LocalDate date) {}

    public IbkrTradeParser.FlexTrades parse(String csv) {
        List<List<String>> rows = splitRowsKeepingQuotedNewlines(csv);

        String baseCurrency = baseCurrency(rows);
        List<FxRate> forexRates = readForexRates(rows);
        Map<String, BigDecimal> baseTable = readBaseRateTable(rows);

        List<IbkrTradeParser.ParsedTrade> trades = new ArrayList<>();
        List<IbkrTradeParser.ParsedTrade> skipped = new ArrayList<>();

        Map<String, Integer> cols = null; // resolved from the Stock trades header row
        List<Integer> feeCols = new ArrayList<>();
        for (List<String> f : rows) {
            if (f.size() < 5) continue;
            if (!"Trades".equals(trim(f.get(0)))) continue;

            String sub = trim(f.get(1));          // "Stock", "Forex", or "" (header rows)
            String marker = trim(f.get(3));       // "DATA" / "TOTAL" / "" ; header rows have "" here

            // Header row for the Stock trades table: col1 blank and col4 == "Symbol".
            if (sub.isEmpty() && "Symbol".equalsIgnoreCase(cell(f, 4))) {
                cols = mapColumns(f);
                feeCols = feeColumns(f);
                continue;
            }
            if (!"Stock".equalsIgnoreCase(sub)) continue;   // skip Forex + other sub-tables
            if (!"DATA".equalsIgnoreCase(marker)) continue; // skip TOTAL rows
            if (cols == null) continue;                     // no header seen yet

            String symbol = cell(f, cols.getOrDefault("symbol", 4));
            if (symbol == null || symbol.isBlank()) continue; // Tiger's duplicate blank-symbol row

            BigDecimal qty = num(cell(f, cols.get("qty")));
            if (qty.signum() == 0) continue;
            BigDecimal price = num(cell(f, cols.get("price")));
            BigDecimal amount = cols.containsKey("amount") ? num(cell(f, cols.get("amount"))) : null;
            String activity = cols.containsKey("activity") ? cell(f, cols.get("activity")) : null;
            String currency = cols.containsKey("ccy") ? cell(f, cols.get("ccy")) : null;
            LocalDate date = tradeDate(cell(f, cols.get("date")));
            if (date == null) continue;

            // Sum every fee column present into one all-in fee (absolute, trade currency).
            BigDecimal fees = BigDecimal.ZERO;
            for (int fc : feeCols) fees = fees.add(num(cell(f, fc)).abs());
            if (fees.signum() == 0) fees = null;

            boolean buy = direction(activity, amount);
            String ccy = (currency == null || currency.isBlank()) ? "USD" : currency.trim().toUpperCase();
            BigDecimal fxToBase = resolveFx(ccy, baseCurrency, date, forexRates, baseTable);

            trades.add(new IbkrTradeParser.ParsedTrade(
                    null, symbol.trim().toUpperCase(), "STK",
                    buy, qty.abs(), price.abs(), ccy, date, fees, fxToBase));
        }
        return new IbkrTradeParser.FlexTrades(trades, skipped, new ArrayList<>(), new ArrayList<>());
    }

    // ─────────────────────────── column mapping ───────────────────────────

    /** Map the Stock trades header names → column index for the fields we need. */
    private Map<String, Integer> mapColumns(List<String> header) {
        Map<String, Integer> cols = new HashMap<>();
        for (int c = 0; c < header.size(); c++) {
            String h = trim(header.get(c)).toLowerCase();
            switch (h) {
                case "symbol" -> cols.putIfAbsent("symbol", c);
                case "activity type" -> cols.putIfAbsent("activity", c);
                case "quantity" -> cols.putIfAbsent("qty", c);
                case "trade price" -> cols.putIfAbsent("price", c);
                case "amount" -> cols.putIfAbsent("amount", c);
                case "trade time" -> cols.putIfAbsent("date", c);
                case "currency" -> cols.put("ccy", c); // last "Currency" (trailing column) wins
                default -> { }
            }
        }
        return cols;
    }

    /** All fee/charge column indices (there are many in a Tiger statement; we sum them all). */
    private List<Integer> feeColumns(List<String> header) {
        List<Integer> out = new ArrayList<>();
        for (int c = 0; c < header.size(); c++) {
            if (FEE_HEADERS.contains(trim(header.get(c)).toLowerCase())) out.add(c);
        }
        return out;
    }

    /**
     * BUY vs SELL. Prefer an explicit Activity Type ("Buy"/"Sell"); Tiger stock statements often leave
     * it blank, in which case the sign of the trade Amount decides. Defaults to BUY.
     */
    private boolean direction(String activity, BigDecimal amount) {
        if (activity != null) {
            String a = activity.trim().toLowerCase();
            if (a.startsWith("sell") || a.contains("sold")) return false;
            if (a.startsWith("buy") || a.contains("bought")) return true;
        }
        if (amount != null && amount.signum() < 0) return false;
        return true;
    }

    // ─────────────────────────── FX association ───────────────────────────

    /** Account/statement base currency from the Account Information section (default USD). */
    private String baseCurrency(List<List<String>> rows) {
        for (List<String> f : rows) {
            if (f.size() >= 8 && "Account Information".equals(trim(f.get(0))) && "DATA".equals(trim(f.get(3)))) {
                String base = trim(f.get(7));
                if (!base.isBlank()) return base.toUpperCase();
            }
        }
        return "USD";
    }

    /**
     * Read the {@code Trades > Forex} section: each {@code CCY.BASE} row gives base units per 1 CCY as
     * the Trade Price. e.g. {@code USD.SGD ... 1.36205} → 1 USD = 1.36205 SGD.
     */
    private List<FxRate> readForexRates(List<List<String>> rows) {
        List<FxRate> out = new ArrayList<>();
        Map<String, Integer> cols = null;
        for (List<String> f : rows) {
            if (f.size() < 5 || !"Trades".equals(trim(f.get(0)))) continue;
            String sub = trim(f.get(1));
            if (sub.isEmpty() && "Symbol(Base.Quote)".equalsIgnoreCase(trim(cell(f, 4)))) {
                cols = forexColumns(f);
                continue;
            }
            if (!"Forex".equalsIgnoreCase(sub) || !"DATA".equalsIgnoreCase(trim(f.get(3)))) continue;
            if (cols == null) continue;
            String pair = cell(f, cols.getOrDefault("pair", 4)); // "USD.SGD"
            if (pair == null || !pair.contains(".")) continue;
            String[] parts = pair.trim().toUpperCase().split("\\.");
            if (parts.length != 2) continue;
            BigDecimal rate = num(cell(f, cols.get("price")));
            LocalDate date = tradeDate(cell(f, cols.get("date")));
            if (rate.signum() == 0) continue;
            out.add(new FxRate(parts[0], rate.abs(), date)); // base units per 1 unit of parts[0]
        }
        return out;
    }

    private Map<String, Integer> forexColumns(List<String> header) {
        Map<String, Integer> cols = new HashMap<>();
        for (int c = 0; c < header.size(); c++) {
            String h = trim(header.get(c)).toLowerCase();
            switch (h) {
                case "symbol(base.quote)" -> cols.putIfAbsent("pair", c);
                case "trade price" -> cols.putIfAbsent("price", c);
                case "trade time" -> cols.putIfAbsent("date", c);
                default -> { }
            }
        }
        return cols;
    }

    /** The end-of-statement {@code Base Currency Exchange Rate} table: CCY → base units per 1 CCY. */
    private Map<String, BigDecimal> readBaseRateTable(List<List<String>> rows) {
        Map<String, BigDecimal> out = new HashMap<>();
        for (List<String> f : rows) {
            if (f.size() >= 5 && "Base Currency Exchange Rate".equals(trim(f.get(0)))
                    && "HEADER_DATA".equals(trim(f.get(3)))) {
                String ccy = trim(f.get(4)).toUpperCase();
                BigDecimal rate = num(cell(f, 5));
                if (!ccy.isEmpty() && rate.signum() != 0) out.put(ccy, rate);
            }
        }
        return out;
    }

    /**
     * Resolve a trade-currency→base FX rate. Same currency → 1. Otherwise prefer the closest Forex-row
     * rate for that currency (by date), then the base-rate table. Null if nothing is available.
     */
    private BigDecimal resolveFx(String tradeCcy, String base, LocalDate tradeDate,
                                 List<FxRate> forexRates, Map<String, BigDecimal> baseTable) {
        if (tradeCcy == null || base == null || tradeCcy.equalsIgnoreCase(base)) return BigDecimal.ONE;

        FxRate best = null;
        long bestGap = Long.MAX_VALUE;
        for (FxRate fx : forexRates) {
            if (!fx.ccy().equalsIgnoreCase(tradeCcy)) continue;
            long gap = (fx.date() == null || tradeDate == null) ? Long.MAX_VALUE - 1
                    : Math.abs(fx.date().toEpochDay() - tradeDate.toEpochDay());
            if (best == null || gap < bestGap) { best = fx; bestGap = gap; }
        }
        if (best != null) return best.rate();

        BigDecimal fromTable = baseTable.get(tradeCcy.toUpperCase());
        return fromTable != null ? fromTable : null;
    }

    // ─────────────────────────── CSV reading ───────────────────────────

    /**
     * Split the CSV into rows, honouring double-quoted fields that contain embedded newlines — Tiger
     * wraps the "Trade Time" cell across two physical lines (e.g. {@code "2022-03-07\n12:02:32, US/Eastern"}).
     */
    private List<List<String>> splitRowsKeepingQuotedNewlines(String csv) {
        List<List<String>> rows = new ArrayList<>();
        List<String> cur = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        int i = 0;
        int n = csv.length();
        while (i < n) {
            char ch = csv.charAt(i);
            if (ch == '"') {
                if (inQuotes && i + 1 < n && csv.charAt(i + 1) == '"') { field.append('"'); i += 2; continue; }
                inQuotes = !inQuotes;
                i++;
            } else if (ch == ',' && !inQuotes) {
                cur.add(field.toString());
                field.setLength(0);
                i++;
            } else if ((ch == '\n' || ch == '\r') && !inQuotes) {
                if (ch == '\r' && i + 1 < n && csv.charAt(i + 1) == '\n') i++;
                cur.add(field.toString());
                field.setLength(0);
                rows.add(cur);
                cur = new ArrayList<>();
                i++;
            } else {
                field.append(ch);
                i++;
            }
        }
        if (field.length() > 0 || !cur.isEmpty()) { cur.add(field.toString()); rows.add(cur); }
        return rows;
    }

    // ─────────────────────────── helpers ───────────────────────────

    private String cell(List<String> row, Integer i) {
        if (i == null || i < 0 || i >= row.size()) return null;
        return row.get(i);
    }

    private String trim(String s) { return s == null ? "" : s.strip().replace("\uFEFF", ""); }

    /** Parse a possibly comma-grouped number ("1,575.00" / "-9,998.81"); blank/"-" → 0. */
    private BigDecimal num(String s) {
        if (s == null) return BigDecimal.ZERO;
        String t = s.trim();
        if (t.isEmpty() || t.equals("-")) return BigDecimal.ZERO;
        try { return new BigDecimal(t.replace(",", "")); }
        catch (NumberFormatException e) { return BigDecimal.ZERO; }
    }

    /** Tiger "Trade Time" like "2022-03-07\n12:02:32, US/Eastern" → the trade date. */
    private LocalDate tradeDate(String s) {
        if (s == null || s.isBlank()) return null;
        var m = java.util.regex.Pattern.compile("(\\d{4}-\\d{2}-\\d{2})").matcher(s.trim());
        if (m.find()) {
            try { return LocalDate.parse(m.group(1), DateTimeFormatter.ISO_LOCAL_DATE); }
            catch (Exception e) { return null; }
        }
        return null;
    }
}
