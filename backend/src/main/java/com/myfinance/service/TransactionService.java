package com.myfinance.service;

import com.myfinance.model.*;
import com.myfinance.model.enums.InvestmentPurpose;
import com.myfinance.model.enums.TransactionType;
import com.myfinance.repository.TransactionRepository;
import com.myfinance.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionService {
    private final TransactionRepository transactionRepository;
    private final HoldingService holdingService;
    private final AssetService assetService;
    private final AccountService accountService;
    private final OwnerService ownerService;
    private final TenantContext tenantContext;

    public List<Transaction> getAll() { return transactionRepository.findAllByOrderByTransactionDateDesc(); }
    public List<Transaction> getByUser(Long userId) { return transactionRepository.findByUserIdOrderByTransactionDateDesc(userId); }
    public List<Transaction> getByOwner(Long ownerId) { return transactionRepository.findByOwnerIdOrderByTransactionDateDesc(ownerId); }
    public List<Transaction> getByAccount(Long accountId) { return transactionRepository.findByAccountIdOrderByTransactionDateDesc(accountId); }
    public List<Transaction> getByAccountForUser(Long userId, Long accountId) { return transactionRepository.findByUserIdAndAccountIdOrderByTransactionDateDesc(userId, accountId); }
    public List<Transaction> getByAsset(Long assetId) { return transactionRepository.findByAssetIdOrderByTransactionDateDesc(assetId); }
    public List<Transaction> getByAssetForUser(Long userId, Long assetId) { return transactionRepository.findByUserIdAndAssetIdOrderByTransactionDateDesc(userId, assetId); }
    public List<Transaction> getByDateRange(LocalDate start, LocalDate end) { return transactionRepository.findByTransactionDateBetweenOrderByTransactionDateDesc(start, end); }
    public List<Transaction> getByDateRangeForUser(Long userId, LocalDate start, LocalDate end) { return transactionRepository.findByUserIdAndDateRange(userId, start, end); }
    public List<Transaction> getRecent(int days) { return transactionRepository.findRecentTransactions(LocalDate.now().minusDays(days)); }
    public List<Transaction> getRecentForUser(Long userId, int days) { return transactionRepository.findRecentByUser(userId, LocalDate.now().minusDays(days)); }

    @Transactional
    public Transaction create(Long assetId, Long accountId, Long ownerId, TransactionType type,
                              BigDecimal quantity, BigDecimal pricePerUnit, BigDecimal fees,
                              String currency, LocalDate date, String notes) {
        return create(assetId, accountId, ownerId, type, quantity, pricePerUnit, fees, currency, date, notes, null);
    }

    @Transactional
    public Transaction create(Long assetId, Long accountId, Long ownerId, TransactionType type,
                              BigDecimal quantity, BigDecimal pricePerUnit, BigDecimal fees,
                              String currency, LocalDate date, String notes, InvestmentPurpose purpose) {
        return create(assetId, accountId, ownerId, type, quantity, pricePerUnit, fees, currency, date, notes, purpose, null, null);
    }

    @Transactional
    public Transaction create(Long assetId, Long accountId, Long ownerId, TransactionType type,
                              BigDecimal quantity, BigDecimal pricePerUnit, BigDecimal fees,
                              String currency, LocalDate date, String notes, InvestmentPurpose purpose,
                              String feeCurrency, BigDecimal fxRateToBase) {
        Asset asset = assetService.getById(assetId);
        Account account = accountService.getById(accountId);
        Owner owner = ownerService.getById(ownerId);

        BigDecimal totalAmount = quantity.multiply(pricePerUnit);
        if (fees != null) totalAmount = totalAmount.add(fees);

        Transaction tx = Transaction.builder()
                .asset(asset).account(account).owner(owner)
                .transactionType(type).quantity(quantity).pricePerUnit(pricePerUnit)
                .totalAmount(totalAmount).fees(fees != null ? fees : BigDecimal.ZERO)
                .feeCurrency(feeCurrency).fxRateToBase(fxRateToBase)
                .currency(currency != null ? com.myfinance.model.enums.Currency.valueOf(currency) : account.getCurrency())
                .transactionDate(date != null ? date : LocalDate.now())
                .notes(notes).purpose(purpose)
                .userId(tenantContext.getCurrentUserId()).build();

        Transaction saved = transactionRepository.save(tx);
        log.info("Created Transaction id={} type={} assetId={} quantity={}", saved.getId(), type, assetId, quantity);
        RealizedPnl realized = updateHolding(asset, account, owner, type, quantity, pricePerUnit, purpose, fxRateToBase, fees, feeCurrency);
        if (realized != null) {
            saved.setRealizedPnl(realized.total());
            saved.setRealizedStockPnl(realized.stock());
            saved.setRealizedFxPnl(realized.fx());
            saved = transactionRepository.save(saved);
        }
        return saved;
    }

    /**
     * Update an existing transaction. The holding is kept correct by REVERSING the old
     * transaction's effect and then APPLYING the new values — so editing quantity/price
     * (e.g. after a stock split) never leaves the holding out of sync. Ownership is enforced
     * by the caller (controller checks the record belongs to the current user).
     */
    @Transactional
    public Transaction update(Long id, Long assetId, Long accountId, Long ownerId, TransactionType type,
                              BigDecimal quantity, BigDecimal pricePerUnit, BigDecimal fees,
                              String currency, LocalDate date, String notes, InvestmentPurpose purpose) {
        return update(id, assetId, accountId, ownerId, type, quantity, pricePerUnit, fees, currency, date, notes, purpose, null, null);
    }

    @Transactional
    public Transaction update(Long id, Long assetId, Long accountId, Long ownerId, TransactionType type,
                              BigDecimal quantity, BigDecimal pricePerUnit, BigDecimal fees,
                              String currency, LocalDate date, String notes, InvestmentPurpose purpose,
                              String feeCurrency, BigDecimal fxRateToBase) {
        Transaction existing = transactionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Transaction not found: " + id));

        // 1. Reverse the OLD transaction's effect on the holding.
        reverseHolding(existing.getAsset(), existing.getAccount(), existing.getOwner(),
                existing.getTransactionType(), existing.getQuantity(), existing.getPricePerUnit());

        // 2. Re-point the transaction to the (possibly changed) asset/account/owner and values.
        Asset asset = assetService.getById(assetId);
        Account account = accountService.getById(accountId);
        Owner owner = ownerService.getById(ownerId);

        BigDecimal totalAmount = quantity.multiply(pricePerUnit);
        if (fees != null) totalAmount = totalAmount.add(fees);

        existing.setAsset(asset);
        existing.setAccount(account);
        existing.setOwner(owner);
        existing.setTransactionType(type);
        existing.setQuantity(quantity);
        existing.setPricePerUnit(pricePerUnit);
        existing.setTotalAmount(totalAmount);
        existing.setFees(fees != null ? fees : BigDecimal.ZERO);
        existing.setFeeCurrency(feeCurrency);
        existing.setFxRateToBase(fxRateToBase);
        existing.setCurrency(currency != null ? com.myfinance.model.enums.Currency.valueOf(currency) : account.getCurrency());
        existing.setTransactionDate(date != null ? date : existing.getTransactionDate());
        existing.setNotes(notes);
        existing.setPurpose(purpose);

        // 3. Apply the NEW transaction's effect on the holding, capturing realized P/L for sells.
        RealizedPnl realized = updateHolding(asset, account, owner, type, quantity, pricePerUnit, purpose, fxRateToBase, fees, feeCurrency);
        existing.setRealizedPnl(realized != null ? realized.total() : null);
        existing.setRealizedStockPnl(realized != null ? realized.stock() : null);
        existing.setRealizedFxPnl(realized != null ? realized.fx() : null);

        Transaction saved = transactionRepository.save(existing);
        log.info("Updated Transaction id={} type={} assetId={} quantity={}", id, type, assetId, quantity);
        return saved;
    }

    /** Undo a previously-applied transaction's effect on its holding (inverse of updateHolding). */
    private void reverseHolding(Asset asset, Account account, Owner owner, TransactionType type,
                                BigDecimal quantity, BigDecimal pricePerUnit) {
        var holdingOpt = holdingService.getHolding(asset.getId(), account.getId(), owner.getId());
        if (holdingOpt.isEmpty()) return; // Nothing to reverse (e.g. holding was already removed).
        Holding h = holdingOpt.get();

        if (type == TransactionType.BUY) {
            // Removing a past BUY: subtract its quantity and invested amount at the ORIGINAL price.
            BigDecimal newQty = h.getQuantity().subtract(quantity);
            BigDecimal newInvested = h.getInvestedAmount().subtract(quantity.multiply(pricePerUnit));
            if (newQty.compareTo(BigDecimal.ZERO) <= 0) {
                holdingService.delete(h.getId());
                return;
            }
            h.setQuantity(newQty);
            h.setInvestedAmount(newInvested.max(BigDecimal.ZERO));
            h.setAverageBuyPrice(h.getInvestedAmount().divide(newQty, 6, RoundingMode.HALF_UP));
            holdingService.save(h);
        } else if (type == TransactionType.SELL) {
            // Removing a past SELL: add the quantity back at the current average buy price.
            BigDecimal newQty = h.getQuantity().add(quantity);
            h.setQuantity(newQty);
            h.setInvestedAmount(h.getInvestedAmount().add(quantity.multiply(h.getAverageBuyPrice())));
            holdingService.save(h);
        }
    }

    /** Realized P/L breakdown returned by a SELL, all in the broker account's currency. */
    public record RealizedPnl(BigDecimal total, BigDecimal stock, BigDecimal fx) {}

    private void updateHolding(Asset asset, Account account, Owner owner, TransactionType type, BigDecimal quantity, BigDecimal pricePerUnit, InvestmentPurpose purpose) {
        updateHolding(asset, account, owner, type, quantity, pricePerUnit, purpose, null, null, null);
    }

    /**
     * Apply a transaction's effect to its holding. For BUY, maintains quantity, average buy price,
     * invested amount and the quantity-weighted average buy FX rate. For SELL, reduces the holding
     * and returns the realized P/L (stock + FX split) in the account currency; returns null for BUY.
     */
    private RealizedPnl updateHolding(Asset asset, Account account, Owner owner, TransactionType type,
                                      BigDecimal quantity, BigDecimal pricePerUnit, InvestmentPurpose purpose,
                                      BigDecimal fxRateToBase, BigDecimal fees, String feeCurrency) {
        var holdingOpt = holdingService.getHolding(asset.getId(), account.getId(), owner.getId());
        // buyFx: the trade→account FX for this transaction. 1 when same currency / not provided.
        BigDecimal txFx = fxRateToBase != null ? fxRateToBase : BigDecimal.ONE;

        if (type == TransactionType.BUY) {
            if (holdingOpt.isPresent()) {
                Holding h = holdingOpt.get();
                BigDecimal newQty = h.getQuantity().add(quantity);
                BigDecimal newInvested = h.getInvestedAmount().add(quantity.multiply(pricePerUnit));
                BigDecimal newAvg = newInvested.divide(newQty, 6, RoundingMode.HALF_UP);
                // Quantity-weighted average of the buy FX rate.
                BigDecimal prevFx = h.getAverageBuyFxRate() != null ? h.getAverageBuyFxRate() : BigDecimal.ONE;
                BigDecimal newAvgFx = prevFx.multiply(h.getQuantity()).add(txFx.multiply(quantity))
                        .divide(newQty, 6, RoundingMode.HALF_UP);
                h.setQuantity(newQty);
                h.setAverageBuyPrice(newAvg);
                h.setInvestedAmount(newInvested);
                h.setAverageBuyFxRate(newAvgFx);
                if (purpose != null) h.setPurpose(purpose);
                holdingService.save(h);
            } else {
                holdingService.save(Holding.builder()
                        .asset(asset).account(account).owner(owner)
                        .quantity(quantity).averageBuyPrice(pricePerUnit)
                        .investedAmount(quantity.multiply(pricePerUnit))
                        .averageBuyFxRate(txFx)
                        .currency(asset.getCurrency() != null ? asset.getCurrency() : account.getCurrency())
                        .purpose(purpose)
                        .userId(tenantContext.getCurrentUserId()).build());
            }
            return null;
        } else if (type == TransactionType.SELL) {
            if (holdingOpt.isPresent()) {
                Holding h = holdingOpt.get();
                BigDecimal newQty = h.getQuantity().subtract(quantity);
                if (newQty.compareTo(BigDecimal.ZERO) < 0) {
                    log.error("Failed to sell: quantity {} exceeds holding for assetId={}", quantity, asset.getId());
                    throw new RuntimeException("Cannot sell more than held");
                }
                BigDecimal soldInvestment = quantity.multiply(h.getAverageBuyPrice());
                h.setQuantity(newQty);
                h.setInvestedAmount(h.getInvestedAmount().subtract(soldInvestment));
                holdingService.save(h);

                // ── Realized P/L in the account currency, split into stock and FX components ──
                // avgBuyFx: FX rate at which the sold shares were originally bought (quantity-weighted).
                BigDecimal avgBuyFx = h.getAverageBuyFxRate() != null ? h.getAverageBuyFxRate() : BigDecimal.ONE;
                BigDecimal sellFx = txFx; // FX rate captured on this SELL (or 1 if same currency)
                BigDecimal avgBuyPrice = h.getAverageBuyPrice();

                // Proceeds and cost in the trade (instrument) currency.
                BigDecimal proceedsTrade = quantity.multiply(pricePerUnit);
                BigDecimal costTrade = quantity.multiply(avgBuyPrice);

                // Stock component: valued at the BUY FX rate (so only the price move shows here).
                BigDecimal stock = proceedsTrade.subtract(costTrade).multiply(avgBuyFx);
                // FX component: proceeds re-valued at (sellFx − buyFx).
                BigDecimal fx = proceedsTrade.multiply(sellFx.subtract(avgBuyFx));

                // Fees reduce the realized total (converted into the account currency).
                BigDecimal feeAcct = BigDecimal.ZERO;
                if (fees != null && fees.signum() != 0) {
                    // A fee in the account currency needs no conversion; a fee in the trade currency
                    // uses the sell FX rate. (feeCurrency null → assume account currency.)
                    boolean feeInTradeCcy = feeCurrency != null
                            && account.getCurrency() != null
                            && !feeCurrency.equalsIgnoreCase(account.getCurrency().name());
                    feeAcct = feeInTradeCcy ? fees.multiply(sellFx) : fees;
                }

                BigDecimal total = stock.add(fx).subtract(feeAcct);
                // Fees are booked against the stock component for the split (they're not an FX effect).
                return new RealizedPnl(total, stock.subtract(feeAcct), fx);
            } else {
                log.error("Failed to sell: no holding found for assetId={} accountId={} ownerId={}", asset.getId(), account.getId(), owner.getId());
                throw new RuntimeException("No holding found to sell");
            }
        }
        return null;
    }

    public void delete(Long id) {
        transactionRepository.deleteById(id);
        log.info("Deleted Transaction id={}", id);
    }
}
