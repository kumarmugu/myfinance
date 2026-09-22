package com.myfinance.service;

import com.myfinance.service.IbkrTradeParser.FlexTrades;
import com.myfinance.service.IbkrTradeParser.ParsedCorporateAction;
import com.myfinance.service.IbkrTradeParser.ParsedTrade;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure-parser tests for {@link IbkrTradeParser} — no Spring context needed. Verify that only
 * stock/ETF buys and sells are surfaced (options/forex skipped), buy+sell close a position, and
 * corporate actions are classified (split / symbol change actionable, others need review).
 */
class IbkrTradeParserTest {

    private final IbkrTradeParser parser = new IbkrTradeParser();

    @Test
    void parsesStockBuyAndSellSkippingOptionsAndForex() {
        String xml = """
            <FlexQueryResponse>
              <FlexStatements>
                <FlexStatement>
                  <Trades>
                    <Trade tradeID="1001" symbol="AAPL" assetCategory="STK" buySell="BUY"
                           quantity="10" tradePrice="150.00" currency="USD" tradeDate="20240115"
                           ibCommission="-1.00" fxRateToBase="1.34"/>
                    <Trade tradeID="1002" symbol="AAPL" assetCategory="STK" buySell="SELL"
                           quantity="-10" tradePrice="180.00" currency="USD" tradeDate="20240610"
                           ibCommission="-1.00" fxRateToBase="1.30"/>
                    <Trade tradeID="2001" symbol="AAPL 240119C00150000" assetCategory="OPT" buySell="BUY"
                           quantity="1" tradePrice="2.50" currency="USD" tradeDate="20240101"/>
                    <Trade tradeID="3001" symbol="USD.SGD" assetCategory="CASH" buySell="BUY"
                           quantity="1000" tradePrice="1.34" currency="SGD" tradeDate="20240101"/>
                  </Trades>
                </FlexStatement>
              </FlexStatements>
            </FlexQueryResponse>
            """;

        FlexTrades result = parser.parse(xml);

        assertEquals(2, result.trades().size(), "only the two STK trades are imported");
        assertEquals(2, result.skipped().size(), "the option and forex trades are skipped");

        ParsedTrade buy = result.trades().get(0);
        assertEquals("AAPL", buy.symbol());
        assertTrue(buy.buy());
        assertEquals(0, new java.math.BigDecimal("10").compareTo(buy.quantity()));
        assertEquals(0, new java.math.BigDecimal("150.00").compareTo(buy.price()));
        assertEquals("USD", buy.currency());
        assertEquals(LocalDate.of(2024, 1, 15), buy.tradeDate());
        assertEquals(0, new java.math.BigDecimal("1.00").compareTo(buy.commission()), "commission is absolute");
        assertEquals(0, new java.math.BigDecimal("1.34").compareTo(buy.fxRateToBase()));

        ParsedTrade sell = result.trades().get(1);
        assertFalse(sell.buy(), "the -10 quantity is a SELL");
        assertEquals(0, new java.math.BigDecimal("10").compareTo(sell.quantity()), "quantity is absolute");
    }

    @Test
    void classifiesSplitAndSymbolChangeAndFlagsOthers() {
        String xml = """
            <FlexQueryResponse><FlexStatements><FlexStatement><CorporateActions>
              <CorporateAction symbol="NVDA" type="FS" reportDate="20240610"
                               actionDescription="NVDA(US67066G1040) SPLIT 10 FOR 1"/>
              <CorporateAction symbol="FB" type="TC" reportDate="20220609"
                               actionDescription="FB changed to META"/>
              <CorporateAction symbol="XYZ" type="TO" reportDate="20240101"
                               actionDescription="XYZ SPINOFF of ABC"/>
            </CorporateActions></FlexStatement></FlexStatements></FlexQueryResponse>
            """;

        FlexTrades result = parser.parse(xml);

        assertEquals(2, result.corporateActions().size(), "split + symbol change are actionable");
        assertEquals(1, result.needsReview().size(), "the spinoff needs manual review");

        ParsedCorporateAction split = result.corporateActions().stream()
                .filter(c -> c.kind().equals("SPLIT")).findFirst().orElseThrow();
        assertEquals("NVDA", split.symbol());
        assertEquals(0, new java.math.BigDecimal("10").compareTo(split.ratio()), "10-for-1 split ratio");

        ParsedCorporateAction tc = result.corporateActions().stream()
                .filter(c -> c.kind().equals("SYMBOL_CHANGE")).findFirst().orElseThrow();
        assertEquals("FB", tc.symbol());
        assertEquals("META", tc.newSymbol());
    }

    @Test
    void tradeWithoutSymbolOrDateIsIgnored() {
        String xml = """
            <FlexQueryResponse><FlexStatements><FlexStatement><Trades>
              <Trade tradeID="9" assetCategory="STK" buySell="BUY" quantity="5" tradePrice="10" currency="USD"/>
            </Trades></FlexStatement></FlexStatements></FlexQueryResponse>
            """;
        FlexTrades result = parser.parse(xml);
        assertTrue(result.trades().isEmpty(), "a trade missing symbol/date is dropped, not imported");
    }

    // ─────────────────────────── IBKR "Transaction History" CSV ───────────────────────────

    @Test
    void parsesBuyRowsFromTransactionHistoryCsvSkippingNonTrades() {
        // Real column layout: Date=2, Type=5, Symbol=6, Qty=7, Price=8, PriceCcy=9, Commission=11.
        String csv = String.join("\n",
            "Transaction History,Header,Date,Account,Description,Transaction Type,Symbol,Quantity,Price,Price Currency,Gross Amount ,Commission,Net Amount",
            "Transaction History,Data,2026-09-17,U***30208,SINGAPORE AIRLINES LTD,Buy,C6L,1000.0,6.47,SGD,-6470.0,-7.99692,-6478.7166428",
            "Transaction History,Data,2026-09-16,U***30208,SS SPDR S&P 500 UCIT ETF ACC,Buy,SPYL,531.0454,18.830779,USD,-12783.0,-6.3935365057488,-12789.9689547884",
            "Transaction History,Data,2026-09-16,U***30208,\"Net Amount in Base from Forex Trade: 48,689.69 USD.SGD\",Forex Trade Component,USD.SGD,48689.69,1.27337,SGD,237.26,-2.5458,237.26",
            "Transaction History,Data,2026-09-15,U***30208,Electronic Fund Transfer,Deposit,-,-,-,-,20000.0,-,20000.0",
            "Transaction History,Data,2026-09-14,U***30208,GOOGL(US02079K3059) Cash Dividend USD 0.22 per Share (Ordinary Dividend),Dividend,GOOGL,-,-,-,13.41648,-,13.41648",
            "Transaction History,Data,2026-08-05,U***30208,SGD Credit Interest for Jul-2026,Credit Interest,-,-,-,-,0.31,-,0.31",
            "Transaction History,Data,2025-07-07,U***30208,Disbursement,Withdrawal,-,-,-,-,-9000.0,-,-9000.0");

        FlexTrades result = parser.parseTransactionHistoryCsv(csv);

        assertEquals(2, result.trades().size(), "only the two Buy rows are imported; forex/deposit/dividend/interest/withdrawal skipped");

        ParsedTrade c6l = result.trades().get(0);
        assertEquals("C6L", c6l.symbol());
        assertTrue(c6l.buy());
        assertEquals("SGD", c6l.currency(), "trade currency comes from the Price Currency column");
        assertEquals(0, new java.math.BigDecimal("1000.0").compareTo(c6l.quantity()));
        assertEquals(0, new java.math.BigDecimal("6.47").compareTo(c6l.price()));
        assertEquals(0, new java.math.BigDecimal("7.99692").compareTo(c6l.commission()), "commission is absolute");
        assertNull(c6l.tradeId(), "the CSV has no trade id → fuzzy dedupe");
        assertEquals(LocalDate.of(2026, 9, 17), c6l.tradeDate());

        ParsedTrade spyl = result.trades().get(1);
        assertEquals("SPYL", spyl.symbol());
        assertEquals("USD", spyl.currency());
        assertEquals(0, new java.math.BigDecimal("531.0454").compareTo(spyl.quantity()), "fractional share quantity preserved");
    }

    @Test
    void parseFileAutoDetectsCsvVsXml() {
        String csv = String.join("\n",
            "Transaction History,Header,Date,Account,Description,Transaction Type,Symbol,Quantity,Price,Price Currency,Gross Amount ,Commission,Net Amount",
            "Transaction History,Data,2025-01-10,U1,SINGTEL,Buy,Z74,900.0,3.05,SGD,-2745.0,-2.5,-2747.725");
        FlexTrades fromCsv = parser.parseFile(csv.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertEquals(1, fromCsv.trades().size());
        assertEquals("Z74", fromCsv.trades().get(0).symbol());

        String xml = "<FlexQueryResponse><FlexStatements><FlexStatement><Trades>"
            + "<Trade tradeID=\"1\" symbol=\"AAPL\" assetCategory=\"STK\" buySell=\"BUY\" quantity=\"10\""
            + " tradePrice=\"150\" currency=\"USD\" tradeDate=\"20240115\"/>"
            + "</Trades></FlexStatement></FlexStatements></FlexQueryResponse>";
        FlexTrades fromXml = parser.parseFile(xml.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertEquals(1, fromXml.trades().size());
        assertEquals("AAPL", fromXml.trades().get(0).symbol());
        assertEquals("1", fromXml.trades().get(0).tradeId(), "XML carries a trade id");
    }
}
