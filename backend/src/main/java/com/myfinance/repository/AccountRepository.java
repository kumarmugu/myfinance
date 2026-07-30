package com.myfinance.repository;

import com.myfinance.model.Account;
import com.myfinance.model.enums.AccountType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {
    List<Account> findByAccountType(AccountType accountType);
    List<Account> findByNameContainingIgnoreCase(String name);
}
