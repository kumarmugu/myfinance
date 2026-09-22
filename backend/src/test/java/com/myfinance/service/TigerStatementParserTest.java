package com.myfinance.service;

import com.myfinance.service.IbkrTradeParser.FlexTrades;
import com.myfinance.service.IbkrTradeParser.ParsedTrade;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Parser tests for the Tiger Brokers "Activity Statement" CSV importer, using the real column layout
 * from a Tiger statement (the Trades &gt; Stock section, the Trades &gt; Forex FX rows, the Account
 * Information base currency, and the end-of-statement Base Currency Exchange Rate table).
 *
 * <p>Verifies: only genuine Stock DATA rows with a symbol import (Forex sub-table, TOTAL rows, and the
 * duplicate blank-symbol row are skipped); all fee columns are summed; the trade date comes from the
 * multi-line "Trade Time" cell; and the FX-to-base rate is associated from the Forex/base-rate tables.
 */
class TigerStatementParserTest {

    private final TigerStatementParser parser = new TigerStatementParser();

    /**
     * A faithful subset of a Tiger statement. The Stock trades header lists (after the 4 section
     * columns): Symbol, Market, Exchange, Activity Type, Quantity, Trade Price, Amount, Commission,
     * Platform Fee, Settlement Fee, GST, Trade Time, Settle Date, Currency. Each trade is followed by
     * Tiger's duplicate blank-symbol DATA row and a TOTAL row, both of which must be skipped.
     */
    private static final String STATEMENT = String.join("\n",
        "Activity Statement,,,,2022-02-24 - 2022-12-31",
        "Account Information,,,,Account,Address,Account Category,Base Currency",
        "Account Information,,,DATA,50414420,SOME ADDRESS,Cash,USD",
        "Trades,,,,Symbol(Base.Quote),Activity Type,Quantity(Base),Trade Price,Amount(Quote),Notes,Trade Time,Settle Date,Currency",
        "Trades,Forex,,DATA,USD.SGD,,7341,1.36205,\"-9,998.81\",,\"2022-02-24\n10:17:17, US/Eastern\",,SGD",
        "Trades,Forex,,TOTAL,Total USD.SGD,,7341,,\"-9,998.81\",,,,SGD",
        "Trades,,,,Symbol,Market,Exchange,Activity Type,Quantity,Trade Price,Amount,Commission,Platform Fee,Settlement Fee,GST,Trade Time,Settle Date,Currency",
        "Trades,Stock,,DATA,QQQ,US,,,2,330.00000,660.00,-0.99,-1.00,-0.01,-0.14,\"2022-03-07\n12:02:32, US/Eastern\",,USD",
        "Trades,Stock,,DATA,,US,,,2,330.00000,660.00,-0.99,-1.00,-0.01,-0.14,\"2022-03-07\n12:02:32, US/Eastern\",,USD",
        "Trades,Stock,,TOTAL,Total QQQ,,,,2,,660.00,-0.99,-1.00,-0.01,-0.14,,,USD",
        "Trades,Stock,,DATA,TSLA,US,,Sell,1,770.00000,-770.00,-0.99,-1.00,0.00,-0.14,\"2022-05-11\n09:37:29, US/Eastern\",,USD",
        "Trades,Stock,,DATA,,US,,Sell,1,770.00000,-770.00,-0.99,-1.00,0.00,-0.14,\"2022-05-11\n09:37:29, US/Eastern\",,USD",
        "Trades,Stock,,TOTAL,Total,,,,,,-110.00,-1.98,-2.00,-0.01,-0.28,,,USD",
        "Base Currency Exchange Rate,,,,Date,2022-12-31",
        "Base Currency Exchange Rate,,,HEADER_DATA,USD,1.0",
        "Base Currency Exchange Rate,,,HEADER_DATA,SGD,0.74594");

    @Test
    void parsesStockTradesSkippingForexTotalsAndDuplicateRows() {
        FlexTrades result = parser.parse(STATEMENT);

        assertEquals(2, result.trades().size(),
                "only the two real Stock DATA rows import; Forex, TOTAL and blank-symbol duplicates are skipped");

        ParsedTrade qqq = result.trades().get(0);
        assertEquals("QQQ", qqq.symbol());
        assertTrue(qqq.buy(), "blank Activity Type + positive Amount → BUY");
        assertEquals(0, new BigDecimal("2").compareTo(qqq.quantity()));
        assertEquals(0, new BigDecimal("330.00").compareTo(qqq.price()));
        assertEquals("USD", qqq.currency());
        assertEquals(LocalDate.of(2022, 3, 7), qqq.tradeDate(), "date taken from the multi-line Trade Time cell");

        // All-in fee = |commission| + |platform| + |settlement| + |gst| = 0.99 + 1.00 + 0.01 + 0.14 = 2.14
        assertEquals(0, new BigDecimal("2.14").compareTo(qqq.commission()),
                "every fee column is summed into one all-in fee");

        ParsedTrade tsla = result.trades().get(1);
        assertEquals("TSLA", tsla.symbol());
        assertFalse(tsla.buy(), "Activity Type 'Sell' → SELL");
    }

    @Test
    void usdTradeInUsdBaseAccountConvertsAtOne() {
        FlexTrades result = parser.parse(STATEMENT);
        assertEquals(0, BigDecimal.ONE.compareTo(result.trades().get(0).fxRateToBase()),
                "a USD trade in a USD-base account converts at 1.0");
    }

    @Test
    void associatesRateForATradeCurrencyThatDiffersFromBase() {
        // Add an SGD-priced trade to the USD-base statement; it should get SGD→base from the base table.
        String stmt = STATEMENT.replace(
            "Trades,Stock,,DATA,TSLA,US,,Sell,1,770.00000,-770.00,-0.99,-1.00,0.00,-0.14,\"2022-05-11\n09:37:29, US/Eastern\",,USD",
            "Trades,Stock,,DATA,D05,SG,,,100,35.00000,3500.00,-1.00,0.00,0.00,-0.07,\"2022-05-11\n09:37:29, US/Eastern\",,SGD");
        FlexTrades result = parser.parse(stmt);
        ParsedTrade sgdTrade = result.trades().stream().filter(t -> t.symbol().equals("D05")).findFirst().orElseThrow();
        assertNotNull(sgdTrade.fxRateToBase(), "an SGD trade in a USD-base account gets a rate from the base table");
        assertEquals(0, new BigDecimal("0.74594").compareTo(sgdTrade.fxRateToBase()));
    }

    @Test
    void looksLikeTigerStatementDetectsTheFormat() {
        assertTrue(TigerStatementParser.looksLikeTigerStatement(STATEMENT));
        assertFalse(TigerStatementParser.looksLikeTigerStatement("<FlexQueryResponse></FlexQueryResponse>"));
        assertFalse(TigerStatementParser.looksLikeTigerStatement("Transaction History,Header,Date"));
    }
}
