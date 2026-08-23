package com.myfinance.service;

import com.myfinance.config.ReferenceConstraintException;
import com.myfinance.model.*;
import com.myfinance.model.enums.*;
import com.myfinance.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AccountServiceTest {

    @Autowired private AccountService accountService;
    @Autowired private AccountRepository accountRepository;
    @Autowired private OwnerRepository ownerRepository;
    @Autowired private AssetRepository assetRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private HoldingRepository holdingRepository;
    @Autowired private AppUserRepository appUserRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private AppUser testUser;
    private Owner owner;

    @BeforeEach
    void setup() {
        holdingRepository.deleteAll();
        transactionRepository.deleteAll();
        assetRepository.deleteAll();
        accountRepository.deleteAll();
        ownerRepository.deleteAll();

        if (appUserRepository.findByUsername("user").isEmpty()) {
            testUser = appUserRepository.save(AppUser.builder()
                    .username("user").email("test@test.com")
                    .password(passwordEncoder.encode("test123"))
                    .displayName("Test User").role("USER").build());
        } else {
            testUser = appUserRepository.findByUsername("user").get();
        }

        owner = ownerRepository.save(Owner.builder()
                .name("Test Owner").relationship(OwnerRelationship.SELF)
                .userId(testUser.getId()).build());
    }

    @Test
    void shouldCreateAccount() {
        Account account = Account.builder()
                .name("DBS Savings").accountType(AccountType.BANK)
                .currency(Currency.SGD).owner(owner)
                .userId(testUser.getId()).build();

        Account saved = accountService.create(account);

        assertNotNull(saved.getId());
        assertEquals("DBS Savings", saved.getName());
        assertEquals(AccountType.BANK, saved.getAccountType());
        assertEquals(Currency.SGD, saved.getCurrency());
    }

    @Test
    void shouldGetAccountById() {
        Account account = accountRepository.save(Account.builder()
                .name("Tiger Broker").accountType(AccountType.BROKER)
                .currency(Currency.USD).owner(owner)
                .userId(testUser.getId()).build());

        Account found = accountService.getById(account.getId());
        assertEquals("Tiger Broker", found.getName());
    }

    @Test
    void shouldThrowWhenAccountNotFound() {
        assertThrows(RuntimeException.class, () -> accountService.getById(9999L));
    }

    @Test
    void shouldGetAccountsByType() {
        accountRepository.save(Account.builder()
                .name("Broker 1").accountType(AccountType.BROKER)
                .currency(Currency.USD).owner(owner).userId(testUser.getId()).build());
        accountRepository.save(Account.builder()
                .name("Bank 1").accountType(AccountType.BANK)
                .currency(Currency.SGD).owner(owner).userId(testUser.getId()).build());
        accountRepository.save(Account.builder()
                .name("Broker 2").accountType(AccountType.BROKER)
                .currency(Currency.USD).owner(owner).userId(testUser.getId()).build());

        List<Account> brokers = accountService.getByType(AccountType.BROKER);
        assertEquals(2, brokers.size());
        assertTrue(brokers.stream().allMatch(a -> a.getAccountType() == AccountType.BROKER));
    }

    @Test
    void shouldUpdateAccount() {
        Account account = accountRepository.save(Account.builder()
                .name("Old Name").accountType(AccountType.BROKER)
                .currency(Currency.USD).owner(owner).userId(testUser.getId()).build());

        Account update = Account.builder()
                .name("New Name").accountType(AccountType.BANK)
                .currency(Currency.SGD).owner(owner).build();

        Account updated = accountService.update(account.getId(), update);
        assertEquals("New Name", updated.getName());
        assertEquals(AccountType.BANK, updated.getAccountType());
        assertEquals(Currency.SGD, updated.getCurrency());
    }

    @Test
    void shouldDeleteAccountWithNoReferences() {
        Account account = accountRepository.save(Account.builder()
                .name("Empty Account").accountType(AccountType.BROKER)
                .currency(Currency.USD).owner(owner).userId(testUser.getId()).build());

        accountService.delete(account.getId());

        assertFalse(accountRepository.findById(account.getId()).isPresent());
    }

    @Test
    @WithMockUser(username = "user")
    void shouldThrowReferenceConstraintExceptionWhenAccountHasTransactions() {
        Account account = accountRepository.save(Account.builder()
                .name("Referenced Account").accountType(AccountType.BROKER)
                .currency(Currency.USD).owner(owner).userId(testUser.getId()).build());

        Asset asset = assetRepository.save(Asset.builder()
                .name("Test Asset").symbol("TST-AS")
                .assetType(AssetType.INDEX_FUND).currency(Currency.USD)
                .userId(testUser.getId()).build());

        // Create a transaction referencing this account
        transactionRepository.save(Transaction.builder()
                .asset(asset).account(account).owner(owner)
                .transactionType(TransactionType.BUY)
                .quantity(BigDecimal.TEN).pricePerUnit(new BigDecimal("100.00"))
                .totalAmount(new BigDecimal("1000.00"))
                .fees(BigDecimal.ZERO).currency(Currency.USD)
                .transactionDate(LocalDate.of(2024, 1, 1))
                .userId(testUser.getId()).build());

        ReferenceConstraintException exception = assertThrows(
                ReferenceConstraintException.class,
                () -> accountService.delete(account.getId()));

        assertTrue(exception.getMessage().contains("Referenced Account"));
        assertTrue(exception.getReferences().stream().anyMatch(r -> r.contains("Transaction")));
    }

    @Test
    @WithMockUser(username = "user")
    void shouldThrowReferenceConstraintExceptionWhenAccountHasHoldings() {
        Account account = accountRepository.save(Account.builder()
                .name("Holding Account").accountType(AccountType.BROKER)
                .currency(Currency.USD).owner(owner).userId(testUser.getId()).build());

        Asset asset = assetRepository.save(Asset.builder()
                .name("Holding Asset").symbol("HLD-AS")
                .assetType(AssetType.INDEX_FUND).currency(Currency.USD)
                .userId(testUser.getId()).build());

        // Create a holding referencing this account
        holdingRepository.save(Holding.builder()
                .asset(asset).account(account).owner(owner)
                .quantity(BigDecimal.TEN)
                .averageBuyPrice(new BigDecimal("100.00"))
                .investedAmount(new BigDecimal("1000.00"))
                .currency(Currency.USD)
                .userId(testUser.getId()).build());

        ReferenceConstraintException exception = assertThrows(
                ReferenceConstraintException.class,
                () -> accountService.delete(account.getId()));

        assertTrue(exception.getMessage().contains("Holding Account"));
        assertTrue(exception.getReferences().stream().anyMatch(r -> r.contains("Holding")));
    }

    @Test
    void shouldGetAllAccounts() {
        accountRepository.save(Account.builder()
                .name("Acc 1").accountType(AccountType.BROKER)
                .currency(Currency.USD).owner(owner).userId(testUser.getId()).build());
        accountRepository.save(Account.builder()
                .name("Acc 2").accountType(AccountType.BANK)
                .currency(Currency.SGD).owner(owner).userId(testUser.getId()).build());

        List<Account> all = accountService.getAllAccounts();
        assertEquals(2, all.size());
    }
}
