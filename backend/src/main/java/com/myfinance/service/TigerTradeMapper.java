package com.myfinance.service;

import com.tigerbrokers.stock.openapi.client.https.domain.trade.item.TradeOrder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Maps Tiger Open API filled orders into the shared {@link IbkrTradeParser.FlexTrades} shape so the
 * existing {@link IbkrSyncService} preview/apply/dedupe/holding/P&amp;L machinery is reused verbatim.
 *
 * <p>This is deliberately a pure mapper (no network, no SDK client) so it can be unit-tested with
 * plain {@link TradeOrder} objects. {@link TigerSyncService} owns the signed API call and hands the
 * fetched orders here.
 */
@Service
public class TigerTradeMapper {

    /** Only equities/ETFs are imported as trades — options/futures/warrants are skipped, matching IBKR. */
    private static final java.util.Set<String> STOCK_LIKE = java.util.Set.of("STK", "ETF", "FUND");

    /**
     * Convert Tiger filled orders to trades. Non-filled orders, non-stock security types, and rows
     * with a zero filled quantity are dropped. The Tiger order {@code id} is a stable, unique key,
     * so it becomes the {@code tradeId} for exact de-duplication on re-sync.
     */
    public IbkrTradeParser.FlexTrades toFlexTrades(List<TradeOrder> orders) {
        List<IbkrTradeParser.ParsedTrade> trades = new ArrayList<>();
        List<IbkrTradeParser.ParsedTrade> skipped = new ArrayList<>();
        if (orders == null) {
            return new IbkrTradeParser.FlexTrades(trades, skipped, new ArrayList<>(), new ArrayList<>());
        }

        for (TradeOrder o : orders) {
            if (o == null) continue;
            BigDecimal qty = filledQuantity(o);
            if (qty.signum() == 0) continue;                 // nothing actually filled
            if (!isFilled(o)) continue;                      // ignore open/cancelled/invalid orders

            String symbol = o.getSymbol() == null ? null : o.getSymbol().trim().toUpperCase();
            if (symbol == null || symbol.isEmpty()) continue;

            String secType = o.getSecType() == null ? "STK" : o.getSecType().trim().toUpperCase();
            boolean buy = o.getAction() != null && o.getAction().trim().equalsIgnoreCase("BUY");
            BigDecimal price = o.getAvgFillPrice() == null ? BigDecimal.ZERO : BigDecimal.valueOf(o.getAvgFillPrice());
            String currency = o.getCurrency() == null || o.getCurrency().isBlank()
                    ? "USD" : o.getCurrency().trim().toUpperCase();
            LocalDate tradeDate = epochMillisToDate(firstNonNull(o.getOpenTime(), o.getLatestTime(), o.getUpdateTime()));
            BigDecimal commission = o.getCommission() == null ? null : BigDecimal.valueOf(o.getCommission()).abs();
            String tradeId = o.getId() == null ? null : "TIGER-" + o.getId();

            if (tradeDate == null) continue;

            IbkrTradeParser.ParsedTrade t = new IbkrTradeParser.ParsedTrade(
                    tradeId, symbol, secType, buy, qty.abs(), price.abs(),
                    currency, tradeDate, commission, null);

            if (STOCK_LIKE.contains(secType)) trades.add(t);
            else skipped.add(t);
        }
        return new IbkrTradeParser.FlexTrades(trades, skipped, new ArrayList<>(), new ArrayList<>());
    }

    /**
     * Tiger returns quantities as an integer plus a scale (odd-lot support):
     * actual = filledQuantity * 10^(-scale). e.g. filledQuantity=2135, scale=2 → 21.35.
     */
    private BigDecimal filledQuantity(TradeOrder o) {
        Long q = o.getFilledQuantity();
        if (q == null || q == 0L) return BigDecimal.ZERO;
        int scale = o.getFilledQuantityScale() == null ? 0 : o.getFilledQuantityScale();
        BigDecimal v = BigDecimal.valueOf(q);
        return scale > 0 ? v.movePointLeft(scale) : v;
    }

    /** True when the order status indicates it was (fully or partially) filled. */
    private boolean isFilled(TradeOrder o) {
        if (o.getStatus() == null) return false;
        String s = o.getStatus().name();
        // The SDK's OrderStatus enum uses names like Filled / PartiallyFilled.
        return s.equalsIgnoreCase("Filled") || s.toUpperCase().contains("FILLED");
    }

    private LocalDate epochMillisToDate(Long millis) {
        if (millis == null || millis <= 0) return null;
        return Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate();
    }

    @SafeVarargs
    private final <T> T firstNonNull(T... vals) {
        for (T v : vals) if (v != null) return v;
        return null;
    }
}
