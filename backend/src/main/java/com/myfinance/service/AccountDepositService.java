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
    public List<AccountDeposit> getByAccount(Long accountId) { return repository.findByAccountIdOrderByDepositDateDesc(accountId); }
    public AccountDeposit create(AccountDeposit deposit) { return repository.save(deposit); }
    public void delete(Long id) { repository.deleteById(id); }
}
