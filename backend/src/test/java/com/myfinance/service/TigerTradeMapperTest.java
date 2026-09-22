package com.myfinance.service;

import com.myfinance.service.IbkrTradeParser.FlexTrades;
import com.myfinance.service.IbkrTradeParser.ParsedTrade;
import com.tigerbrokers.stock.openapi.client.https.domain.trade.item.TradeOrder;
import com.tigerbrokers.stock.openapi.client.struct.enums.OrderStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure-mapper tests for {@link TigerTradeMapper}: filled equity orders become trades (with all-in
 * fee = commission + GST and odd-lot quantity scaling), while non-filled and non-stock orders are
 * dropped/skipped. No network or SDK client is involved.
 */
class TigerTradeMapperTest {

    private final TigerTradeMapper mapper = new TigerTradeMapper();

    private TradeOrder order(String symbol, String secType, String action, String status,
                             long filledQty, Integer scale, double avgFill, String ccy,
                             Double commission, Double gst, long openTimeMillis, Long id) {
        TradeOrder o = new TradeOrder();
        o.setSymbol(symbol);
        o.setSecType(secType);
        o.setAction(action);
        o.setStatus(status == null ? null : OrderStatus.valueOf(status));
        o.setFilledQuantity(filledQty);
        o.setFilledQuantityScale(scale);
        o.setAvgFillPrice(avgFill);
        o.setCurrency(ccy);
        o.setCommission(commission);
        o.setGst(gst);
        o.setOpenTime(openTimeMillis);
        o.setId(id);
        return o;
    }

    private long millis(LocalDate d) {
        return d.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    @Test
    void mapsFilledEquityOrdersSummingCommissionAndGst() {
        long t = millis(LocalDate.of(2023, 6, 20));
        List<TradeOrder> orders = List.of(
            order("AAPL", "STK", "BUY", "Filled", 10, 0, 150.0, "USD", 1.0, 0.34, t, 111L),
            order("NIO.SI", "STK", "SELL", "Filled", 5, 0, 9.36, "SGD", 2.4, null, t, 222L));

        FlexTrades result = mapper.toFlexTrades(orders);
        assertEquals(2, result.trades().size());

        ParsedTrade aapl = result.trades().get(0);
        assertEquals("AAPL", aapl.symbol());
        assertTrue(aapl.buy());
        assertEquals(0, new BigDecimal("10").compareTo(aapl.quantity()));
        assertEquals(0, new BigDecimal("150.0").compareTo(aapl.price()));
        assertEquals("USD", aapl.currency());
        assertEquals(LocalDate.of(2023, 6, 20), aapl.tradeDate());
        assertEquals("TIGER-111", aapl.tradeId(), "the Tiger order id becomes the dedupe key");
        // all-in fee = commission 1.0 + gst 0.34 = 1.34
        assertEquals(0, new BigDecimal("1.34").compareTo(aapl.commission()));

        ParsedTrade nio = result.trades().get(1);
        assertFalse(nio.buy(), "SELL action");
        assertEquals(0, new BigDecimal("2.4").compareTo(nio.commission()), "no GST → just the commission");
    }

    @Test
    void appliesFilledQuantityScaleForOddLots() {
        long t = millis(LocalDate.of(2023, 1, 2));
        // filledQuantity=2135, scale=2 → 21.35 shares.
        FlexTrades result = mapper.toFlexTrades(List.of(
            order("VOO", "STK", "BUY", "Filled", 2135, 2, 380.0, "USD", 0.99, null, t, 5L)));
        assertEquals(1, result.trades().size());
        assertEquals(0, new BigDecimal("21.35").compareTo(result.trades().get(0).quantity()));
    }

    @Test
    void dropsUnfilledOrdersAndZeroQuantity() {
        long t = millis(LocalDate.of(2023, 1, 2));
        FlexTrades result = mapper.toFlexTrades(List.of(
            order("JD", "STK", "BUY", "Invalid", 0, 0, 35.0, "USD", 0.0, null, t, 1L),
            order("BABA", "STK", "BUY", "Cancelled", 10, 0, 100.0, "USD", 0.0, null, t, 2L)));
        assertTrue(result.trades().isEmpty(), "only Filled orders with a filled quantity import");
    }

    @Test
    void skipsNonStockSecurityTypes() {
        long t = millis(LocalDate.of(2023, 1, 2));
        FlexTrades result = mapper.toFlexTrades(List.of(
            order("AAPL 240119C00150000", "OPT", "BUY", "Filled", 1, 0, 2.5, "USD", 1.0, null, t, 9L)));
        assertTrue(result.trades().isEmpty(), "options are not imported as trades");
        assertEquals(1, result.skipped().size());
    }

    @Test
    void handlesNullAndEmptyInput() {
        assertTrue(mapper.toFlexTrades(null).trades().isEmpty());
        assertTrue(mapper.toFlexTrades(List.of()).trades().isEmpty());
    }
}
