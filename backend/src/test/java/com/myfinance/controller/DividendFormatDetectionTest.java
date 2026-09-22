package com.myfinance.controller;

import com.myfinance.service.DividendImportService.Format;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link DividendController#detectFormat}. This is the sniffer that decides which
 * parser a dividend upload goes to. The regression it guards: an IBKR file must NOT be misrouted to
 * the Tiger parser (which silently imports 0 rows) just because both brokers title their export
 * "Activity Statement". Detection is by content shape, not the title.
 */
class DividendFormatDetectionTest {

    // detectFormat uses none of the injected collaborators, so nulls are fine here.
    private final DividendController controller =
            new DividendController(null, null, null, null, null, null, null, null);

    private byte[] bytes(String s) { return s.getBytes(StandardCharsets.UTF_8); }

    @Test
    void detectsIbkrFlexXml() {
        String xml = "<FlexQueryResponse><FlexStatements><FlexStatement><CashTransactions>"
                + "<CashTransaction type=\"Dividends\" symbol=\"AAPL\" currency=\"USD\" reportDate=\"20240815\" amount=\"6.91\"/>"
                + "</CashTransactions></FlexStatement></FlexStatements></FlexQueryResponse>";
        assertEquals(Format.IBKR_FLEX_XML, controller.detectFormat(null, "statement.xml", bytes(xml)));
    }

    @Test
    void detectsIbkrTransactionHistoryCsv() {
        String csv = "Transaction History,Header,Date,Account,Description,Transaction Type,Symbol,Quantity,Price,Price Currency,Gross Amount ,Commission,Net Amount\n"
                + "Transaction History,Data,2026-08-19,U1,Z74 Cash Dividend,Dividend,Z74,-,-,-,-,-,92.7";
        assertEquals(Format.IBKR_CSV, controller.detectFormat(null, "dividends.csv", bytes(csv)));
    }

    @Test
    void detectsTigerActivityStatementCsv() {
        String csv = "Activity Statement,,,,2022-01-01 - 2022-12-31\n"
                + "Account Information,,,DATA,50414420,ADDR,Cash,USD\n"
                + "Dividends,,,,Date,Product,Symbol,Dividend Reinvestment Plan,Quantity/Gross Rate,Phase,Cash Dividends,Shares,Fees & Tax,Net Cash Value,Currency\n"
                + "Dividends,,,DATA,2022-03-29,,VOO,,,Paid,1.37,0,,1.37,USD";
        assertEquals(Format.TIGER_CSV, controller.detectFormat(null, "Statement-50414420.csv", bytes(csv)));
    }

    @Test
    void ibkrCsvTitledActivityStatementIsNotMisroutedToTiger() {
        // The real regression: an IBKR export that ALSO contains the words "Activity Statement".
        // Content shape (Transaction History rows) must win over the shared title.
        String csv = "Activity Statement,,,,IBKR\n"
                + "Transaction History,Header,Date,Account,Description,Transaction Type,Symbol,Quantity,Price,Price Currency,Gross Amount ,Commission,Net Amount\n"
                + "Transaction History,Data,2026-08-19,U1,Z74 Cash Dividend,Dividend,Z74,-,-,-,-,-,92.7";
        assertEquals(Format.IBKR_CSV, controller.detectFormat(null, "activity.csv", bytes(csv)),
                "IBKR content must not be misrouted to the Tiger parser");
    }

    @Test
    void detectsSaxoXlsxByMagicBytes() {
        byte[] zip = new byte[]{0x50, 0x4B, 0x03, 0x04, 0, 0};
        assertEquals(Format.SAXO_XLSX, controller.detectFormat(null, "dividends.xlsx", zip));
    }

    @Test
    void explicitFormatOverridesSniffing() {
        assertEquals(Format.TIGER_CSV, controller.detectFormat("TIGER_CSV", "whatever.csv", bytes("Transaction History,")));
    }
}
