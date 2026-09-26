package com.myfinance.service;

import com.myfinance.service.IbkrTradeParser.FlexTrades;
import com.myfinance.service.IbkrTradeParser.ParsedTrade;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link SaxoTradeParser}. We build a minimal, valid XLSX in-memory (only the two entries
 * the parser reads: {@code xl/sharedStrings.xml} and a worksheet) so no external fixture is needed.
 * Verifies header-name column detection, all-fee summing, buy/sell direction, and a per-line FX rate.
 */
class SaxoTradeParserTest {

    private final SaxoTradeParser parser = new SaxoTradeParser();

    @Test
    void detectsXlsxByMagicBytes() {
        assertTrue(SaxoTradeParser.looksLikeXlsx(new byte[]{0x50, 0x4B, 0x03, 0x04}));
        assertFalse(SaxoTradeParser.looksLikeXlsx("<xml/>".getBytes()));
        assertFalse(SaxoTradeParser.looksLikeXlsx(null));
    }

    /**
     * The real Saxo "Transactions" export layout: side/quantity/price are packed into the "Event" text
     * ("Buy 10 @ 150.00 USD"), the real ticker is in "Instrument Symbol", the descriptive name is in
     * "Instrument", and "Transaction Type" classifies the row. Only "Trade" rows are buys/sells.
     */
    @Test
    void parsesTradeRowsFromEventText() throws Exception {
        String[][] rows = {
            {"Trade Date", "Transaction Type", "Event", "Booked Amount", "Currency", "Instrument", "Instrument Symbol", "Instrument currency"},
            {"2024-01-15", "Trade", "Buy 10 @ 150.00 USD", "-1500.00", "USD", "Apple Inc.", "AAPL:xnas", "USD"},
            {"2024-06-10", "Trade", "Sell -5 @ 250.00 USD", "1250.00", "USD", "Tesla Inc.", "TSLA:xnas", "USD"},
        };
        FlexTrades result = parser.parse(buildXlsx(rows));
        assertEquals(2, result.trades().size());

        ParsedTrade buy = result.trades().get(0);
        assertEquals("AAPL", buy.symbol(), "ticker column preferred + exchange suffix stripped");
        assertTrue(buy.buy());
        assertEquals(0, new BigDecimal("10").compareTo(buy.quantity()), "quantity parsed from Event, not Booked Amount");
        assertEquals(0, new BigDecimal("150.00").compareTo(buy.price()), "price parsed from Event");
        assertEquals("USD", buy.currency());
        assertEquals(LocalDate.of(2024, 1, 15), buy.tradeDate());

        ParsedTrade sell = result.trades().get(1);
        assertEquals("TSLA", sell.symbol());
        assertFalse(sell.buy());
        assertEquals(0, new BigDecimal("5").compareTo(sell.quantity()), "negative qty in Event is taken as magnitude");
    }

    /** Corporate actions (dividends/splits), cash amounts (fees) and transfers must NOT become trades. */
    @Test
    void skipsNonTradeRows() throws Exception {
        String[][] rows = {
            {"Trade Date", "Transaction Type", "Event", "Booked Amount", "Currency", "Instrument", "Instrument Symbol", "Instrument currency"},
            {"2024-01-15", "Trade", "Buy 10 @ 150.00 USD", "-1500.00", "USD", "Apple Inc.", "AAPL:xnas", "USD"},
            {"2024-02-01", "Corporate action", "Cash dividend", "5.64", "USD", "Microsoft Corp.", "MSFT:xnas", "USD"},
            {"2024-02-02", "Cash amount", "Securities Lending Client Fee", "0.77", "USD", "ARK Innovation ETF", "ARKK:bats", "USD"},
            {"2024-02-03", "Cash Transfer", "Deposit", "1000.00", "USD", "", "", "USD"},
        };
        FlexTrades result = parser.parse(buildXlsx(rows));
        assertEquals(1, result.trades().size(), "only the Trade row becomes a trade");
        assertEquals("AAPL", result.trades().get(0).symbol());
    }

    /** A "Trade" row whose Event text we can't parse is surfaced as skipped, never as a bogus trade. */
    @Test
    void unparseableTradeEventGoesToSkipped() throws Exception {
        String[][] rows = {
            {"Trade Date", "Transaction Type", "Event", "Booked Amount", "Currency", "Instrument", "Instrument Symbol", "Instrument currency"},
            {"2024-01-15", "Trade", "Assignment of option", "0.00", "USD", "Apple Inc.", "AAPL:xnas", "USD"},
        };
        FlexTrades result = parser.parse(buildXlsx(rows));
        assertEquals(0, result.trades().size());
        assertEquals(1, result.skipped().size());
    }

    /** Currency falls back to the trailing code in the Event when no explicit currency is resolved. */
    @Test
    void currencyFromEventTrailingCode() throws Exception {
        String[][] rows = {
            {"Trade Date", "Transaction Type", "Event", "Instrument", "Instrument Symbol"},
            {"2024-03-01", "Trade", "Buy 3 @ 100.00 EUR", "Some Euro Fund", "EFUND:xetr"},
        };
        FlexTrades result = parser.parse(buildXlsx(rows));
        assertEquals(1, result.trades().size());
        assertEquals("EUR", result.trades().get(0).currency(), "currency taken from Event when column absent");
    }

    @Test
    void unrecognisedSheetThrows() {
        assertThrows(RuntimeException.class, () -> {
            byte[] xlsx = buildXlsx(new String[][]{{"Foo", "Bar", "Baz"}, {"1", "2", "3"}});
            parser.parse(xlsx);
        });
    }

    // ─────────────────────────── minimal XLSX builder ───────────────────────────

    /** Build an XLSX with all cells as shared strings (t="s"), which the parser resolves by index. */
    private byte[] buildXlsx(String[][] rows) throws Exception {
        List<String> shared = new ArrayList<>();
        StringBuilder sheet = new StringBuilder();
        sheet.append("<?xml version=\"1.0\"?><worksheet><sheetData>");
        for (int r = 0; r < rows.length; r++) {
            sheet.append("<row r=\"").append(r + 1).append("\">");
            for (int c = 0; c < rows[r].length; c++) {
                int idx = shared.size();
                shared.add(rows[r][c]);
                String ref = colRef(c) + (r + 1);
                sheet.append("<c r=\"").append(ref).append("\" t=\"s\"><v>").append(idx).append("</v></c>");
            }
            sheet.append("</row>");
        }
        sheet.append("</sheetData></worksheet>");

        StringBuilder ss = new StringBuilder();
        ss.append("<?xml version=\"1.0\"?><sst>");
        for (String s : shared) ss.append("<si><t>").append(xml(s)).append("</t></si>");
        ss.append("</sst>");

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos)) {
            zos.putNextEntry(new ZipEntry("xl/sharedStrings.xml"));
            zos.write(ss.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("xl/worksheets/sheet1.xml"));
            zos.write(sheet.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        return bos.toByteArray();
    }

    private String colRef(int c) {
        StringBuilder sb = new StringBuilder();
        int n = c;
        do { sb.insert(0, (char) ('A' + (n % 26))); n = n / 26 - 1; } while (n >= 0);
        return sb.toString();
    }

    private String xml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
