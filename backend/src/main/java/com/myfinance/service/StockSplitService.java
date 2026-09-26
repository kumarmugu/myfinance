package com.myfinance.service;

import com.myfinance.model.Asset;
import com.myfinance.model.Holding;
import com.myfinance.model.Transaction;
import com.myfinance.repository.AssetRepository;
import com.myfinance.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

/**
 * Applies a stock split (or reverse split) to a user's holdings and trade history.
 *
 * <p>A split changes only the <em>share count</em> and the <em>per-share price</em>; it never changes
 * the total cost basis or market value of a position. So for a ratio of {@code numerator : denominator}
 * (e.g. {@code 3:1} forward, {@code 1:8} reverse) we scale every affected record by
 * {@code factor = numerator / denominator}:
 * <ul>
 *   <li>transaction quantity {@code ×factor}, per-unit price {@code ÷factor} → {@code totalAmount} preserved;</li>
 *   <li>holding quantity {@code ×factor}, average buy price {@code ÷factor} → {@code investedAmount} preserved.</li>
 * </ul>
 *
 * <p>Only records dated on or before the split's effective date are adjusted (post-split trades are
 * already expressed in the new shares). Everything is tenant-scoped to the current user: a split can
 * only ever touch the caller's own assets, transactions and holdings.
 *
 * <p>Saxo/broker files record that a split <em>happened</em> (symbol + date) but not the ratio, so the
 * ratio is supplied by the user. After adjusting rows we recompute realized P/L and sold positions so
 * closed lots stay consistent.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockSplitService {

    /** Division scale for the derived per-share price / average price (money-grade precision). */
    private static final int PRICE_SCALE = 8;

    private final AssetRepository assetRepository;
    private final TransactionRepository transactionRepository;
    private final HoldingService holdingService;
    private final TransactionService transactionService;
    private final AuditService auditService;

    /** Outcome of a split adjustment, so the UI can report what changed. */
    public record SplitResult(String symbol, LocalDate effectiveDate, int numerator, int denominator,
                              int transactionsAdjusted, int holdingsAdjusted) {}

    /**
     * Apply a split to the current user's position(s) in {@code symbol}.
     *
     * @param userId        the tenant whose data is adjusted
     * @param symbol        instrument ticker (matched case-insensitively against the user's assets)
     * @param effectiveDate records dated on or before this are adjusted
     * @param numerator     new shares in the ratio (3 for a 3:1 forward split)
     * @param denominator   old shares in the ratio (1 for a 3:1 forward split; 8 for a 1:8 reverse split)
     */
    @Transactional
    public SplitResult applySplit(Long userId, String symbol, LocalDate effectiveDate,
                                  int numerator, int denominator) {
        if (symbol == null || symbol.isBlank()) throw new RuntimeException("Symbol is required");
        if (effectiveDate == null) throw new RuntimeException("Effective date is required");
        if (numerator <= 0 || denominator <= 0) throw new RuntimeException("Split ratio must be positive (e.g. 3:1)");

        String want = symbol.trim().toUpperCase();

        // Tenant-scoped: only this user's assets with a matching ticker can ever be touched.
        List<Asset> assets = assetRepository.findByUserId(userId).stream()
                .filter(a -> a.getSymbol() != null && a.getSymbol().trim().equalsIgnoreCase(want))
                .toList();
        if (assets.isEmpty()) throw new RuntimeException("No holdings found for symbol " + want);

        BigDecimal factor = BigDecimal.valueOf(numerator)
                .divide(BigDecimal.valueOf(denominator), PRICE_SCALE, RoundingMode.HALF_UP);

        int txnAdjusted = 0;
        int holdingsAdjusted = 0;

        for (Asset asset : assets) {
            // 1. Adjust every BUY/SELL for this asset dated on or before the split. Quantity scales up
            //    by the factor and price scales down by it, so totalAmount (the cash) is unchanged.
            List<Transaction> txns = transactionRepository
                    .findByUserIdAndAssetIdOrderByTransactionDateDesc(userId, asset.getId());
            for (Transaction t : txns) {
                if (t.getTransactionDate() == null || t.getTransactionDate().isAfter(effectiveDate)) continue;
                BigDecimal newQty = t.getQuantity().multiply(factor);
                BigDecimal newPrice = t.getPricePerUnit().divide(factor, PRICE_SCALE, RoundingMode.HALF_UP);
                t.setQuantity(newQty);
                t.setPricePerUnit(newPrice);
                // Preserve the recorded cash amount (quantity × price is invariant under a split, but we
                // keep the stored value exact rather than re-derive a rounded product).
                // totalAmount already = old qty × old price (+fees); leave as-is.
                transactionRepository.save(t);
                txnAdjusted++;
            }

            // 2. Adjust the live holding for this asset (across every account/owner it appears in). A
            //    split scales all quantities uniformly, so quantity ×factor and average price ÷factor;
            //    the invested amount (cost basis) is preserved.
            for (Holding h : holdingService.getAllByUserId(userId)) {
                if (h.getAsset() == null || !asset.getId().equals(h.getAsset().getId())) continue;
                h.setQuantity(h.getQuantity().multiply(factor));
                h.setAverageBuyPrice(h.getAverageBuyPrice().divide(factor, PRICE_SCALE, RoundingMode.HALF_UP));
                // investedAmount (cost basis) is unchanged by a split — leave it.
                holdingService.save(h);
                holdingsAdjusted++;
            }
        }

        // 3. Realized P/L and sold positions are derived from the (now adjusted) transactions; refresh
        //    them so closed lots before the split stay consistent.
        transactionService.recomputeRealizedPnlForUser(userId);

        String ratio = numerator + ":" + denominator;
        auditService.log("STOCK_SPLIT", "Transaction", null,
                "Applied " + ratio + " split for " + want + " effective " + effectiveDate
                        + " (" + txnAdjusted + " transactions, " + holdingsAdjusted + " holdings adjusted)");
        log.info("Applied {} split for userId={} symbol={} effective={}: {} txns, {} holdings",
                ratio, userId, want, effectiveDate, txnAdjusted, holdingsAdjusted);

        return new SplitResult(want, effectiveDate, numerator, denominator, txnAdjusted, holdingsAdjusted);
    }
}
