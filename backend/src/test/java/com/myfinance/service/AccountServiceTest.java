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
import java.math.RoundingMode;
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

    @Test
    void reassignMovesOwnersPositionsAndLeavesOtherOwnersUntouched() {
        Owner other = ownerRepository.save(Owner.builder().name("Other").relationship(OwnerRelationship.SPOUSE)
                .userId(testUser.getId()).build());
        Account from = accountRepository.save(Account.builder().name("Shared").accountType(AccountType.BROKER)
                .currency(Currency.USD).owner(owner).userId(testUser.getId()).build());
        Account to = accountRepository.save(Account.builder().name("Target").accountType(AccountType.BROKER)
                .currency(Currency.SGD).owner(other).userId(testUser.getId()).build());
        Asset asset = assetRepository.save(Asset.builder().name("A").symbol("AAA")
                .assetType(AssetType.GROWTH_EQUITY).currency(Currency.USD).userId(testUser.getId()).build());

        // 'other' owner's holding + transaction sit on the shared account and should move.
        holdingRepository.save(Holding.builder().asset(asset).account(from).owner(other)
                .quantity(BigDecimal.TEN).averageBuyPrice(new BigDecimal("100")).investedAmount(new BigDecimal("1000"))
                .currency(Currency.USD).userId(testUser.getId()).build());
        transactionRepository.save(Transaction.builder().asset(asset).account(from).owner(other)
                .transactionType(TransactionType.BUY).quantity(BigDecimal.TEN).pricePerUnit(new BigDecimal("100"))
                .totalAmount(new BigDecimal("1000")).fees(BigDecimal.ZERO).currency(Currency.USD)
                .transactionDate(LocalDate.of(2024, 1, 1)).userId(testUser.getId()).build());
        // The account owner's own holding must NOT move.
        holdingRepository.save(Holding.builder().asset(asset).account(from).owner(owner)
                .quantity(BigDecimal.ONE).averageBuyPrice(new BigDecimal("50")).investedAmount(new BigDecimal("50"))
                .currency(Currency.USD).userId(testUser.getId()).build());

        AccountService.ReassignResult r = accountService.reassignOwnerPositions(
                testUser.getId(), from.getId(), to.getId(), other.getId());

        assertEquals(1, r.transactionsMoved());
        assertEquals(1, r.holdingsMoved());
        assertEquals(0, r.holdingsMerged());
        assertEquals(1, holdingRepository.findByAccountId(to.getId()).size(), "other's holding moved to target");
        assertEquals(1, holdingRepository.findByAccountId(from.getId()).size(), "account owner's holding stayed");
    }

    @Test
    void reassignMergesIntoExistingTargetHolding() {
        Account from = accountRepository.save(Account.builder().name("From").accountType(AccountType.BROKER)
                .currency(Currency.USD).owner(owner).userId(testUser.getId()).build());
        Account to = accountRepository.save(Account.builder().name("To").accountType(AccountType.BROKER)
                .currency(Currency.USD).owner(owner).userId(testUser.getId()).build());
        Asset asset = assetRepository.save(Asset.builder().name("A").symbol("AAA")
                .assetType(AssetType.GROWTH_EQUITY).currency(Currency.USD).userId(testUser.getId()).build());

        // Same asset+owner already held in the target: 10 @ 100 there, 10 @ 200 on the source.
        holdingRepository.save(Holding.builder().asset(asset).account(to).owner(owner)
                .quantity(BigDecimal.TEN).averageBuyPrice(new BigDecimal("100")).investedAmount(new BigDecimal("1000"))
                .currency(Currency.USD).userId(testUser.getId()).build());
        holdingRepository.save(Holding.builder().asset(asset).account(from).owner(owner)
                .quantity(BigDecimal.TEN).averageBuyPrice(new BigDecimal("200")).investedAmount(new BigDecimal("2000"))
                .currency(Currency.USD).userId(testUser.getId()).build());

        AccountService.ReassignResult r = accountService.reassignOwnerPositions(
                testUser.getId(), from.getId(), to.getId(), owner.getId());

        assertEquals(1, r.holdingsMerged());
        assertEquals(0, holdingRepository.findByAccountId(from.getId()).size(), "source holding removed");
        Holding merged = holdingRepository.findByAssetIdAndAccountIdAndOwnerId(asset.getId(), to.getId(), owner.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("20").compareTo(merged.getQuantity()), "quantities add");
        assertEquals(0, new BigDecimal("3000").compareTo(merged.getInvestedAmount()), "invested adds");
        assertEquals(0, new BigDecimal("150").compareTo(merged.getAverageBuyPrice().setScale(0, RoundingMode.HALF_UP)), "weighted avg price");
    }
}
