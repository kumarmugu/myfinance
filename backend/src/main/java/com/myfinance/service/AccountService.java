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

    public List<Account> getAllAccounts() {
        return accountRepository.findAll();
    }

    public Account getAccountById(Long id) {
        return accountRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Account not found with id: " + id));
    }

    public List<Account> getAccountsByType(AccountType type) {
        return accountRepository.findByAccountType(type);
    }

    public Account createAccount(Account account) {
        return accountRepository.save(account);
    }

    public Account updateAccount(Long id, Account updated) {
        Account existing = getAccountById(id);
        existing.setName(updated.getName());
        existing.setAccountType(updated.getAccountType());
        existing.setDescription(updated.getDescription());
        return accountRepository.save(existing);
    }

    public void deleteAccount(Long id) {
        accountRepository.deleteById(id);
    }
}
