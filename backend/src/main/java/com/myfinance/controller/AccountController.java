package com.myfinance.controller;

import com.myfinance.model.Account;
import com.myfinance.model.enums.AccountType;
import com.myfinance.service.AccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
public class AccountController {
    private final AccountService accountService;

    @GetMapping
    public List<Account> getAll() { return accountService.getAllAccounts(); }

    @GetMapping("/{id}")
    public Account getById(@PathVariable Long id) { return accountService.getById(id); }

    @GetMapping("/type/{type}")
    public List<Account> getByType(@PathVariable AccountType type) { return accountService.getByType(type); }

    @GetMapping("/owner/{ownerId}")
    public List<Account> getByOwner(@PathVariable Long ownerId) { return accountService.getByOwner(ownerId); }

    @PostMapping
    public ResponseEntity<Account> create(@Valid @RequestBody Account account) {
        return ResponseEntity.status(HttpStatus.CREATED).body(accountService.create(account));
    }

    @PutMapping("/{id}")
    public Account update(@PathVariable Long id, @Valid @RequestBody Account account) { return accountService.update(id, account); }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) { accountService.delete(id); return ResponseEntity.noContent().build(); }
}
