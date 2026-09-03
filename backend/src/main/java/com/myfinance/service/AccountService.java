package com.myfinance.service;

import com.myfinance.config.ReferenceConstraintException;
import com.myfinance.model.Account;
import com.myfinance.model.enums.AccountType;
import com.myfinance.repository.AccountRepository;
import com.myfinance.repository.HoldingRepository;
import com.myfinance.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountService {
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final HoldingRepository holdingRepository;

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
}
