package com.myfinance.service;

import com.myfinance.model.Account;
import com.myfinance.model.enums.AccountType;
import com.myfinance.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AccountService {
    private final AccountRepository accountRepository;

    public List<Account> getAllAccounts() { return accountRepository.findAll(); }
    public Account getById(Long id) { return accountRepository.findById(id).orElseThrow(() -> new RuntimeException("Account not found: " + id)); }
    public List<Account> getByType(AccountType type) { return accountRepository.findByAccountType(type); }
    public List<Account> getByOwner(Long ownerId) { return accountRepository.findByOwnerId(ownerId); }
    public Account create(Account account) { return accountRepository.save(account); }
    public Account update(Long id, Account updated) {
        Account existing = getById(id);
        existing.setName(updated.getName());
        existing.setAccountType(updated.getAccountType());
        existing.setCurrency(updated.getCurrency());
        existing.setAccountNumber(updated.getAccountNumber());
        existing.setDescription(updated.getDescription());
        existing.setOwner(updated.getOwner());
        return accountRepository.save(existing);
    }
    public void delete(Long id) { accountRepository.deleteById(id); }
}
