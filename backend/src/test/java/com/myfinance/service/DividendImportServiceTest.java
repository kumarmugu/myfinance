package com.myfinance.service;

import com.myfinance.service.DividendImportService.ParsedDividend;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure-parser tests for {@link DividendImportService} — no Spring context needed. Verifies the
 * IBKR CSV and Saxo XLSX parsing rules agreed with the product owner.
 */
class DividendImportServiceTest {

    private final DividendImportService svc = new DividendImportService(null, null, null);

    // ─────────────────────────── IBKR ───────────────────────────

    @Test
    void ibkrNetsUsTaxIntoUsDividendAndKeepsCurrencyUsd() {
        String csv = String.join("\n",
            "Transaction History,Header,Date,Account,Description,Transaction Type,Symbol,Quantity,Price,Price Currency,Gross Amount ,Commission,Net Amount",
            "Transaction History,Data,2026-08-13,U1,AAPL(US0378331005) Cash Dividend USD 0.27 per Share (Ordinary Dividend),Dividend,AAPL,-,-,-,-,-,6.91524",
            "Transaction History,Data,2026-08-13,U1,AAPL(US0378331005) Cash Dividend USD 0.27 per Share - US Tax,Foreign Tax Withholding,AAPL,-,-,-,-,-,-2.074572");
        List<ParsedDividend> out = svc.parseIbkr(csv);
        assertEquals(1, out.size());
        ParsedDividend d = out.get(0);
        assertEquals("AAPL", d.symbol());
        assertEquals("USD", d.currency());
        assertEquals(0, new BigDecimal("4.840668").compareTo(d.net().setScale(6, RoundingMode.HALF_UP)));
        assertEquals(0, new BigDecimal("6.91524").compareTo(d.gross()));
        assertEquals(0, new BigDecimal("2.074572").compareTo(d.tax()));
        assertEquals("ORDINARY", d.type());
    }

    @Test
    void ibkrSgDividendHasNoTaxAndSgdCurrency() {
        String csv = String.join("\n",
            "Transaction History,Header,Date,Account,Description,Transaction Type,Symbol,Quantity,Price,Price Currency,Gross Amount ,Commission,Net Amount",
            "Transaction History,Data,2026-08-28,U1,O39(SG1S04926220) Cash Dividend SGD 0.47 per Share (Ordinary Dividend),Dividend,O39,-,-,-,-,-,47.0");
        List<ParsedDividend> out = svc.parseIbkr(csv);
        assertEquals(1, out.size());
        assertEquals("SGD", out.get(0).currency());
        assertEquals(0, new BigDecimal("47.0").compareTo(out.get(0).net()));
        assertNull(out.get(0).tax());
    }

    @Test
    void ibkrExcludesNonDividendRows() {
        String csv = String.join("\n",
            "Transaction History,Header,Date,Account,Description,Transaction Type,Symbol,Quantity,Price,Price Currency,Gross Amount ,Commission,Net Amount",
            "Transaction History,Data,2026-01-06,U1,Electronic Fund Transfer,Deposit,-,-,-,-,-,-,10000.0",
            "Transaction History,Data,2025-01-10,U1,SINGAPORE TELECOM,Buy,Z74,900,3.05,SGD,-2745,-2.5,-2747.725",
            "Transaction History,Data,2026-08-05,U1,SGD Credit Interest,Credit Interest,-,-,-,-,-,-,0.31",
            "Transaction History,Data,2026-08-19,U1,Z74(SG1T75931496) Cash Dividend SGD 0.103 per Share (Ordinary Dividend),Dividend,Z74,-,-,-,-,-,92.7");
        List<ParsedDividend> out = svc.parseIbkr(csv);
        assertEquals(1, out.size(), "only the Dividend row should be imported");
        assertEquals("Z74", out.get(0).symbol());
    }

    @Test
    void ibkrClassifiesReturnOfCapital() {
        String csv = String.join("\n",
            "Transaction History,Header,Date,Account,Description,Transaction Type,Symbol,Quantity,Price,Price Currency,Gross Amount ,Commission,Net Amount",
            "Transaction History,Data,2026-09-07,U1,ME8U(SG2C32962814) Cash Dividend SGD 0.0055 per Share (Return of Capital),Dividend,ME8U,-,-,-,-,-,14.85");
        assertEquals("RETURN_OF_CAPITAL", svc.parseIbkr(csv).get(0).type());
    }

    // ─────────────────────────── Tiger ───────────────────────────

    @Test
    void tigerImportsPaidDividendsAndSkipsAccrualsAndOtherSections() {
        String csv = String.join("\n",
            "Activity Statement,,,,2022-01-01 - 2022-12-31",
            "Cash Report,,Currency: USD,HEADER_DATA,Dividends,23.38,23.38",
            "Deposits & Withdrawals,,,DATA,2022-02-23,Deposit,2000.00,SGD",
            "Dividends,,,,Date,Product,Symbol,Dividend Reinvestment Plan,Quantity/Gross Rate,Phase,Cash Dividends,Shares,Fees & Tax,Net Cash Value,Currency",
            "Dividends,,,DATA,2022-03-29,,VOO,,,Paid,1.37,0,,1.37,USD",
            "Dividends,,,DATA,2022-05-12,,AAPL,,,Paid,1.15,0,,1.15,USD",
            "Dividends,,,DATA,2022-12-22,,TQQQ,,Quantity: 24,Dividend Accruals Increase,2.35,0,Fee（Include ADR）: 0.71,1.64,USD",
            "Dividends,,,TOTAL,,,Total(In Base),,,Accruals/Paid,,,,1.64/23.38,USD");
        List<ParsedDividend> out = svc.parseTiger(csv);

        assertEquals(2, out.size(), "only Paid dividend rows import; accrual + other sections skipped");
        assertTrue(out.stream().anyMatch(d -> d.symbol().equals("VOO") && d.net().compareTo(new BigDecimal("1.37")) == 0));
        assertTrue(out.stream().anyMatch(d -> d.symbol().equals("AAPL") && d.net().compareTo(new BigDecimal("1.15")) == 0));
        assertTrue(out.stream().noneMatch(d -> d.symbol().equals("TQQQ")), "accrual row must be skipped");
        assertEquals("USD", out.get(0).currency());
    }

    @Test
    void tigerExtractsTickerFromDescriptiveSymbol() {
        // Tiger sometimes puts "NAME (TICKER)" in the Symbol column — must resolve to the bare ticker
        // so it matches the existing asset instead of creating a duplicate.
        String csv = String.join("\n",
            "Dividends,,,,Date,Product,Symbol,Dividend Reinvestment Plan,Quantity/Gross Rate,Phase,Cash Dividends,Shares,Fees & Tax,Net Cash Value,Currency",
            "Dividends,,,DATA,2025-12-23,,\"META PLATFORMS, INC. (META)\",,,Paid,3.30,0,,3.30,USD",
            "Dividends,,,DATA,2025-12-26,,VANGUARD S&P 500 ETF (VOO),,,Paid,47.11,0,,47.11,USD");
        List<ParsedDividend> out = svc.parseTiger(csv);
        assertEquals(2, out.size());
        assertEquals("META", out.get(0).symbol());
        assertEquals("VOO", out.get(1).symbol());
    }

    // ─────────────────────────── Saxo ───────────────────────────

    @Test
    void saxoImportsBookedSgdAndSkipsReversalPair() throws Exception {
        String[] header = new String[20];
        for (int i = 0; i < 20; i++) header[i] = "H" + i;
        String[] a = blank(20);
        a[4] = "VOO:arcx"; a[5] = "Cash dividend"; a[7] = "45822"; a[12] = "USD 34.75"; a[14] = "-USD 8.30"; a[19] = "45.94";
        String[] b = blank(20);
        b[4] = "ARKK:arcx"; b[5] = "Capital gains distribution"; b[7] = "44196"; b[19] = "34.97";
        String[] c = blank(20);
        c[4] = "ARKK:arcx"; c[5] = "Capital gains distribution - Reversals"; c[7] = "44196"; c[19] = "-34.97";

        byte[] xlsx = buildXlsx(List.of(header, a, b, c));
        List<ParsedDividend> out = svc.parseSaxo(xlsx);

        assertEquals(1, out.size(), "reversal pair must be dropped, only the VOO row remains");
        ParsedDividend d = out.get(0);
        assertEquals("VOO", d.symbol());
        assertEquals("SGD", d.currency());
        assertEquals(0, new BigDecimal("45.94").compareTo(d.net()));
        assertEquals(0, new BigDecimal("34.75").compareTo(d.gross()));
        assertEquals(0, new BigDecimal("8.30").compareTo(d.tax()));
    }

    private String[] blank(int n) {
        String[] r = new String[n];
        for (int i = 0; i < n; i++) r[i] = "";
        return r;
    }

    /**
     * Build a minimal .xlsx that matches what the parser reads: a sharedStrings.xml plus a
     * worksheets/sheet1.xml whose cells reference shared strings via {@code t="s"} and {@code <v>}.
     */
    private byte[] buildXlsx(List<String[]> rows) throws Exception {
        // Intern strings.
        Map<String, Integer> dict = new LinkedHashMap<>();
        List<String> order = new ArrayList<>();
        for (String[] row : rows) for (String cell : row) {
            if (cell != null && !cell.isEmpty() && !dict.containsKey(cell)) {
                dict.put(cell, order.size());
                order.add(cell);
            }
        }
        StringBuilder ss = new StringBuilder("<?xml version=\"1.0\"?><sst xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">");
        for (String s : order) ss.append("<si><t>").append(esc(s)).append("</t></si>");
        ss.append("</sst>");

        StringBuilder sheet = new StringBuilder("<?xml version=\"1.0\"?><worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>");
        for (String[] row : rows) {
            sheet.append("<row>");
            for (String cell : row) {
                if (cell == null || cell.isEmpty()) sheet.append("<c/>");
                else sheet.append("<c t=\"s\"><v>").append(dict.get(cell)).append("</v></c>");
            }
            sheet.append("</row>");
        }
        sheet.append("</sheetData></worksheet>");

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

    private String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    // ─────────────────────────── IBKR Flex XML dividends ───────────────────────────

    @Test
    void flexNetsWithholdingIntoDividendBySymbolDateCurrency() {
        String xml = """
            <FlexQueryResponse><FlexStatements><FlexStatement><CashTransactions>
              <CashTransaction type="Dividends" symbol="AAPL" currency="USD" reportDate="20240815"
                               amount="6.91" description="AAPL Cash Dividend USD 0.27 (Ordinary Dividend)"/>
              <CashTransaction type="Withholding Tax" symbol="AAPL" currency="USD" reportDate="20240815"
                               amount="-2.07" description="AAPL US Tax"/>
              <CashTransaction type="Broker Interest Paid" symbol="" currency="USD" reportDate="20240815"
                               amount="-0.10" description="Interest"/>
            </CashTransactions></FlexStatement></FlexStatements></FlexQueryResponse>
            """;
        var out = svc.parseFlexXml(xml);
        assertEquals(1, out.size(), "one dividend; interest ignored");
        var d = out.get(0);
        assertEquals("AAPL", d.symbol());
        assertEquals("USD", d.currency());
        assertEquals(0, new BigDecimal("4.84").compareTo(d.net().setScale(2, RoundingMode.HALF_UP)), "gross 6.91 - tax 2.07");
        assertEquals(0, new BigDecimal("6.91").compareTo(d.gross()));
        assertEquals(0, new BigDecimal("2.07").compareTo(d.tax()));
    }

    @Test
    void flexDividendWithNoTaxHasNullTax() {
        String xml = """
            <FlexQueryResponse><FlexStatements><FlexStatement><CashTransactions>
              <CashTransaction type="Dividends" symbol="O39" currency="SGD" reportDate="20240828"
                               amount="47.00" description="O39 Cash Dividend SGD 0.47 (Ordinary Dividend)"/>
            </CashTransactions></FlexStatement></FlexStatements></FlexQueryResponse>
            """;
        var out = svc.parseFlexXml(xml);
        assertEquals(1, out.size());
        assertEquals("SGD", out.get(0).currency());
        assertEquals(0, new BigDecimal("47.00").compareTo(out.get(0).net()));
        assertNull(out.get(0).tax());
    }
}
