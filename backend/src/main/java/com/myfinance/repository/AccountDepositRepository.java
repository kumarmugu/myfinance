package com.myfinance.repository;

import com.myfinance.model.AccountDeposit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AccountDepositRepository extends JpaRepository<AccountDeposit, Long> {
    List<AccountDeposit> findByAccountIdOrderByDepositDateDesc(Long accountId);
    List<AccountDeposit> findAllByOrderByDepositDateDesc();
}
