package com.myfinance.service;

import com.myfinance.config.ReferenceConstraintException;
import com.myfinance.model.Account;
import com.myfinance.model.Holding;
import com.myfinance.model.enums.AccountType;
import com.myfinance.repository.AccountRepository;
import com.myfinance.repository.DividendRepository;
import com.myfinance.repository.HoldingRepository;
import com.myfinance.repository.SoldPositionRepository;
import com.myfinance.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountService {
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final HoldingRepository holdingRepository;
    private final SoldPositionRepository soldPositionRepository;
    private final DividendRepository dividendRepository;

    public List<Account> getAllAccounts() { return accountRepository.findAll(); }
    public Account getById(Long id) { return accountRepository.findById(id).orElseThrow(() -> new RuntimeException("Account not found: " + id)); }
    public List<Account> getByType(AccountType type) { return accountRepository.findByAccountType(type); }
    public List<Account> getByOwner(Long ownerId) { return accountRepository.findByOwnerId(ownerId); }
    public Account create(Account account) {
        Account saved = accountRepository.save(account);
        log.info("Created Account id={} name={}", saved.getId(), saved.getName());
        return saved;
    }
    public Account update(Long id, Account updated) {
        Account existing = getById(id);
        existing.setName(updated.getName());
        existing.setAccountType(updated.getAccountType());
        existing.setCurrency(updated.getCurrency());
        existing.setAccountNumber(updated.getAccountNumber());
        existing.setDescription(updated.getDescription());
        existing.setOwner(updated.getOwner());
        existing.setCashBalance(updated.getCashBalance());
        existing.setIncludeCashInNetWorth(updated.getIncludeCashInNetWorth());
        Account saved = accountRepository.save(existing);
        log.info("Updated Account id={}", id);
        return saved;
    }

    public void delete(Long id) {
        Account account = getById(id);
        List<String> references = new ArrayList<>();

        long txCount = transactionRepository.findByAccountIdOrderByTransactionDateDesc(id).size();
        if (txCount > 0) references.add(txCount + " Transaction(s)");

        long holdingCount = holdingRepository.findByAccountId(id).size();
        if (holdingCount > 0) references.add(holdingCount + " Holding(s)");

        if (!references.isEmpty()) {
            log.warn("Cannot delete Account id={}, referenced by: {}", id, references);
            throw new ReferenceConstraintException("Account '" + account.getName() + "'", references);
        }

        accountRepository.deleteById(id);
        log.info("Deleted Account id={}", id);
    }

    /** Summary of an account reassignment, so the caller can report what moved. */
    public record ReassignResult(int transactionsMoved, int holdingsMoved, int holdingsMerged,
                                 int soldPositionsMoved, int dividendsMoved) {}

    /**
     * Move one owner's positions from one account to another (e.g. holdings mistakenly recorded on
     * a shared account). Repoints that owner's transactions, holdings, sold positions and dividends
     * from {@code fromAccountId} to {@code toAccountId}. Other owners' rows on the source account are
     * left untouched (this is why it is owner-scoped, not a blanket account move).
     *
     * <p>Holdings are unique per (asset, account, owner). If the target account already has a holding
     * for the same asset+owner, the two are merged: quantities and invested amounts add, and the
     * average buy price / buy FX rate become quantity-weighted. Both accounts must belong to the
     * caller (tenant check).
     */
    @Transactional
    public ReassignResult reassignOwnerPositions(Long userId, Long fromAccountId, Long toAccountId, Long ownerId) {
        if (fromAccountId.equals(toAccountId)) {
            throw new RuntimeException("Source and target accounts must differ");
        }
        Account from = getById(fromAccountId);
        Account to = getById(toAccountId);
        if (!userId.equals(from.getUserId()) || !userId.equals(to.getUserId())) {
            throw new RuntimeException("Both accounts must belong to the current user");
        }

        int txMoved = 0, spMoved = 0, divMoved = 0, holdMoved = 0, holdMerged = 0;

        for (var t : transactionRepository.findByUserIdAndAccountIdOrderByTransactionDateDesc(userId, fromAccountId)) {
            if (t.getOwner() != null && ownerId.equals(t.getOwner().getId())) {
                t.setAccount(to);
                transactionRepository.save(t);
                txMoved++;
            }
        }
        for (var sp : soldPositionRepository.findByAccountIdOrderBySoldDateDesc(fromAccountId)) {
            if (userId.equals(sp.getUserId()) && sp.getOwner() != null && ownerId.equals(sp.getOwner().getId())) {
                sp.setAccount(to);
                soldPositionRepository.save(sp);
                spMoved++;
            }
        }
        for (var d : dividendRepository.findByAccountIdOrderByReceivedDateDesc(fromAccountId)) {
            if (userId.equals(d.getUserId()) && d.getOwner() != null && ownerId.equals(d.getOwner().getId())) {
                d.setAccount(to);
                dividendRepository.save(d);
                divMoved++;
            }
        }
        // Holdings last, handling the unique (asset, account, owner) constraint by merging.
        for (var h : holdingRepository.findByAccountId(fromAccountId)) {
            if (h.getOwner() == null || !ownerId.equals(h.getOwner().getId())) continue;
            var existing = holdingRepository.findByAssetIdAndAccountIdAndOwnerId(
                    h.getAsset().getId(), toAccountId, ownerId);
            if (existing.isPresent() && !existing.get().getId().equals(h.getId())) {
                mergeHolding(existing.get(), h);
                holdingRepository.save(existing.get());
                holdingRepository.delete(h);
                holdMerged++;
            } else {
                h.setAccount(to);
                holdingRepository.save(h);
                holdMoved++;
            }
        }

        log.info("Reassigned owner {} positions from account {} to {} for userId={}: {} txns, {} holdings ({} merged), {} sold, {} dividends",
                ownerId, fromAccountId, toAccountId, userId, txMoved, holdMoved, holdMerged, spMoved, divMoved);
        return new ReassignResult(txMoved, holdMoved, holdMerged, spMoved, divMoved);
    }

    /** Merge {@code src} into {@code target}: additive quantity/invested, quantity-weighted prices. */
    private void mergeHolding(Holding target, Holding src) {
        BigDecimal tq = target.getQuantity() == null ? BigDecimal.ZERO : target.getQuantity();
        BigDecimal sq = src.getQuantity() == null ? BigDecimal.ZERO : src.getQuantity();
        BigDecimal totalQty = tq.add(sq);

        BigDecimal tInv = target.getInvestedAmount() == null ? BigDecimal.ZERO : target.getInvestedAmount();
        BigDecimal sInv = src.getInvestedAmount() == null ? BigDecimal.ZERO : src.getInvestedAmount();
        target.setInvestedAmount(tInv.add(sInv));

        if (totalQty.signum() > 0) {
            // Quantity-weighted average buy price.
            BigDecimal tPrice = target.getAverageBuyPrice() == null ? BigDecimal.ZERO : target.getAverageBuyPrice();
            BigDecimal sPrice = src.getAverageBuyPrice() == null ? BigDecimal.ZERO : src.getAverageBuyPrice();
            BigDecimal avgPrice = tPrice.multiply(tq).add(sPrice.multiply(sq))
                    .divide(totalQty, 6, RoundingMode.HALF_UP);
            target.setAverageBuyPrice(avgPrice);

            // Quantity-weighted average buy FX, counting only the sides that have a rate.
            BigDecimal fxWeighted = BigDecimal.ZERO, fxQty = BigDecimal.ZERO;
            if (target.getAverageBuyFxRate() != null) { fxWeighted = fxWeighted.add(target.getAverageBuyFxRate().multiply(tq)); fxQty = fxQty.add(tq); }
            if (src.getAverageBuyFxRate() != null) { fxWeighted = fxWeighted.add(src.getAverageBuyFxRate().multiply(sq)); fxQty = fxQty.add(sq); }
            target.setAverageBuyFxRate(fxQty.signum() > 0 ? fxWeighted.divide(fxQty, 6, RoundingMode.HALF_UP) : null);
        }
        target.setQuantity(totalQty);
    }
}
