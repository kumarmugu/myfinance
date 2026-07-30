package com.myfinance.service;

import com.myfinance.model.Asset;
import com.myfinance.model.Holding;
import com.myfinance.model.Transaction;
import com.myfinance.model.enums.AssetType;
import com.myfinance.model.enums.TransactionType;
import com.myfinance.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final HoldingService holdingService;
    private final AssetService assetService;
    private final AccountService accountService;

    public List<Transaction> getAllTransactions() {
        return transactionRepository.findAllByOrderByTransactionDateDesc();
    }

    public Transaction getTransactionById(Long id) {
        return transactionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Transaction not found with id: " + id));
    }

    public List<Transaction> getTransactionsByAsset(Long assetId) {
        return transactionRepository.findByAssetIdOrderByTransactionDateDesc(assetId);
    }

    public List<Transaction> getTransactionsByAccount(Long accountId) {
        return transactionRepository.findByAccountIdOrderByTransactionDateDesc(accountId);
    }

    public List<Transaction> getTransactionsByAssetType(AssetType assetType) {
        return transactionRepository.findByAssetType(assetType);
    }

    public List<Transaction> getTransactionsByDateRange(LocalDate start, LocalDate end) {
        return transactionRepository.findByTransactionDateBetweenOrderByTransactionDateDesc(start, end);
    }

    public List<Transaction> getRecentTransactions(int days) {
        return transactionRepository.findRecentTransactions(LocalDate.now().minusDays(days));
    }

    @Transactional
    public Transaction createTransaction(Long assetId, Long accountId, TransactionType type,
                                          BigDecimal quantity, BigDecimal pricePerUnit,
                                          BigDecimal fees, LocalDate date, String notes) {
        Asset asset = assetService.getAssetById(assetId);
        var account = accountService.getAccountById(accountId);

        BigDecimal totalAmount = quantity.multiply(pricePerUnit);
        if (fees != null) {
            totalAmount = totalAmount.add(fees);
        }

        Transaction transaction = Transaction.builder()
                .asset(asset)
                .account(account)
                .transactionType(type)
                .quantity(quantity)
                .pricePerUnit(pricePerUnit)
                .totalAmount(totalAmount)
                .fees(fees != null ? fees : BigDecimal.ZERO)
                .transactionDate(date != null ? date : LocalDate.now())
                .notes(notes)
                .build();

        Transaction saved = transactionRepository.save(transaction);

        // Update holding
        updateHolding(asset, account, type, quantity, pricePerUnit);

        return saved;
    }

    private void updateHolding(Asset asset, com.myfinance.model.Account account,
                                TransactionType type, BigDecimal quantity, BigDecimal pricePerUnit) {
        var holdingOpt = holdingService.getHolding(asset.getId(), account.getId());

        if (type == TransactionType.BUY) {
            if (holdingOpt.isPresent()) {
                Holding holding = holdingOpt.get();
                BigDecimal newQuantity = holding.getQuantity().add(quantity);
                BigDecimal newInvested = holding.getInvestedAmount().add(quantity.multiply(pricePerUnit));
                BigDecimal newAvgPrice = newInvested.divide(newQuantity, 4, RoundingMode.HALF_UP);
                holding.setQuantity(newQuantity);
                holding.setAverageBuyPrice(newAvgPrice);
                holding.setInvestedAmount(newInvested);
                holdingService.saveHolding(holding);
            } else {
                Holding holding = Holding.builder()
                        .asset(asset)
                        .account(account)
                        .quantity(quantity)
                        .averageBuyPrice(pricePerUnit)
                        .investedAmount(quantity.multiply(pricePerUnit))
                        .build();
                holdingService.saveHolding(holding);
            }
        } else {
            // SELL
            if (holdingOpt.isPresent()) {
                Holding holding = holdingOpt.get();
                BigDecimal newQuantity = holding.getQuantity().subtract(quantity);
                if (newQuantity.compareTo(BigDecimal.ZERO) < 0) {
                    throw new RuntimeException("Cannot sell more than held quantity");
                }
                BigDecimal soldInvestment = quantity.multiply(holding.getAverageBuyPrice());
                holding.setQuantity(newQuantity);
                holding.setInvestedAmount(holding.getInvestedAmount().subtract(soldInvestment));
                holdingService.saveHolding(holding);
            } else {
                throw new RuntimeException("No holding found to sell");
            }
        }
    }

    public void deleteTransaction(Long id) {
        transactionRepository.deleteById(id);
    }
}
