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
}
