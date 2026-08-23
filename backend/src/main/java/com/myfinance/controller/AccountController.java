package com.myfinance.controller;

import com.myfinance.model.Account;
import com.myfinance.model.enums.AccountType;
import com.myfinance.security.TenantContext;
import com.myfinance.service.AccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
@Slf4j
public class AccountController {
    private final AccountService accountService;
    private final com.myfinance.repository.AccountRepository accountRepository;
    private final TenantContext tenantContext;

    @GetMapping
    public List<Account> getAll() { return accountRepository.findByUserId(tenantContext.getCurrentUserId()); }

    @GetMapping("/{id}")
    public Account getById(@PathVariable Long id) { return accountService.getById(id); }

    @GetMapping("/type/{type}")
    public List<Account> getByType(@PathVariable AccountType type) { return accountService.getByType(type); }

    @GetMapping("/owner/{ownerId}")
    public List<Account> getByOwner(@PathVariable Long ownerId) { return accountService.getByOwner(ownerId); }

    @PostMapping
    public ResponseEntity<Account> create(@Valid @RequestBody Account account) {
        log.info("Creating account: name={}, type={}", account.getName(), account.getAccountType());
        account.setUserId(tenantContext.getCurrentUserId());
        Account saved = accountService.create(account);
        log.info("Created account id={}", saved.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PutMapping("/{id}")
    public Account update(@PathVariable Long id, @Valid @RequestBody Account account) {
        log.info("Updating account id={}", id);
        return accountService.update(id, account);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        log.info("Deleting account id={}", id);
        accountService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
