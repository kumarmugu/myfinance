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

    @Test
    void parsesTradesWithSummedFeesAndFxRate() throws Exception {
        // Columns: Instrument Symbol | Buy/Sell | Amount | Price | Trade Date | Instrument Currency | Commission | Exchange Fee | Conversion Rate
        String[][] rows = {
            {"Instrument Symbol", "Buy/Sell", "Amount", "Price", "Trade Date", "Instrument Currency", "Commission", "Exchange Fee", "Conversion Rate"},
            {"AAPL:xnas", "Buy", "10", "150.00", "2024-01-15", "USD", "-1.00", "-0.50", "1.34"},
            {"TSLA:xnas", "Sell", "5", "250.00", "2024-06-10", "USD", "-1.00", "-0.25", "1.30"},
        };
        byte[] xlsx = buildXlsx(rows);

        FlexTrades result = parser.parse(xlsx);
        assertEquals(2, result.trades().size());

        ParsedTrade buy = result.trades().get(0);
        assertEquals("AAPL", buy.symbol(), "exchange suffix stripped");
        assertTrue(buy.buy());
        assertEquals(0, new BigDecimal("10").compareTo(buy.quantity()));
        assertEquals(0, new BigDecimal("150.00").compareTo(buy.price()));
        assertEquals("USD", buy.currency());
        assertEquals(LocalDate.of(2024, 1, 15), buy.tradeDate());
        // all-in fee = |commission 1.00| + |exchange fee 0.50| = 1.50
        assertEquals(0, new BigDecimal("1.50").compareTo(buy.commission()), "commission + exchange fee summed");
        assertEquals(0, new BigDecimal("1.34").compareTo(buy.fxRateToBase()), "per-line conversion rate captured");

        ParsedTrade sell = result.trades().get(1);
        assertEquals("TSLA", sell.symbol());
        assertFalse(sell.buy());
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
