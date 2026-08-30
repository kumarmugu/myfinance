package com.myfinance.service;

import com.myfinance.model.AccountDeposit;
import com.myfinance.repository.AccountDepositRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AccountDepositService {
    private final AccountDepositRepository repository;

    public List<AccountDeposit> getAll() { return repository.findAllByOrderByDepositDateDesc(); }
    public List<AccountDeposit> getAllForUser(Long userId) { return repository.findByUserIdOrderByDepositDateDesc(userId); }
    public List<AccountDeposit> getByAccount(Long accountId) { return repository.findByAccountIdOrderByDepositDateDesc(accountId); }
    public AccountDeposit create(AccountDeposit deposit) { return repository.save(deposit); }

    public AccountDeposit update(Long id, Long userId, AccountDeposit changes) {
        AccountDeposit existing = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Deposit not found"));
        if (existing.getUserId() != null && !existing.getUserId().equals(userId)) {
            throw new RuntimeException("Deposit not found");
        }
        existing.setAccount(changes.getAccount());
        existing.setAmount(changes.getAmount());
        existing.setDepositType(changes.getDepositType());
        if (changes.getCurrency() != null) existing.setCurrency(changes.getCurrency());
        existing.setDepositDate(changes.getDepositDate());
        existing.setNotes(changes.getNotes());
        return repository.save(existing);
    }

    public void delete(Long id) { repository.deleteById(id); }
}
